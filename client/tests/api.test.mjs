import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { createServer as createHttpServer } from 'node:http'
import { createServer as createViteServer } from 'vite'

let vite, server, http, api, authApi, csrfHeaders, origin
let monitorApi, incidentApi, integrationApi, maintenanceApi, statusPageApi, teamApi
let findWorkspace, canEditWorkspace, workspaceQuery
let requests = []
let configs = []
let nextReply = null
let tokenNumber = 0
const account = { id: 1, email: 'owner@example.com', provider: 'local' }
const originalWindow = globalThis.window

before(async () => {
  // These endpoints only use test data. No real backend is contacted.
  server = createHttpServer(async (request, response) => {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    const url = new URL(request.url, 'http://localhost')
    requests.push({ path: url.pathname, query: url.searchParams, method: request.method,
      headers: request.headers, body: Buffer.concat(chunks).toString() })
    if (nextReply && nextReply.path === url.pathname) {
      const reply = nextReply
      nextReply = null
      if (reply.disconnect) return request.socket.destroy()
      if (reply.delay) await new Promise(resolve => setTimeout(resolve, reply.delay))
      response.writeHead(reply.status, { 'Content-Type': reply.html ? 'text/html' : 'application/json' })
      return response.end(reply.html || JSON.stringify(reply.data))
    }
    if (url.pathname === '/api/auth/csrf') {
      response.setHeader('Content-Type', 'application/json')
      return response.end(JSON.stringify({ token: `token-${++tokenNumber}`, headerName: 'X-CSRF-TOKEN' }))
    }
    if (request.method !== 'GET' && request.headers['x-csrf-token'] !== `token-${tokenNumber}`) {
      response.writeHead(403, { 'Content-Type': 'application/json' })
      return response.end(JSON.stringify({ message: 'Missing CSRF token.' }))
    }
    if (url.pathname.endsWith('/default') || request.method === 'DELETE' || url.pathname.endsWith('/logout')) {
      response.writeHead(204)
      return response.end()
    }
    response.setHeader('Content-Type', 'application/json')
    response.end(JSON.stringify(url.pathname.endsWith('/me') ? account : { ok: true }))
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  origin = `http://127.0.0.1:${server.address().port}`
  globalThis.window = { location: { origin, hash: '#dashboard' } }
  vite = await createViteServer({ configFile: false, envDir: false, server: { middlewareMode: true, watch: null } })
  ;({ http, api } = await vite.ssrLoadModule('/src/services/api.ts'))
  ;({ authApi, csrfHeaders } = await vite.ssrLoadModule('/src/services/auth.ts'))
  ;({ monitorApi } = await vite.ssrLoadModule('/src/services/monitors.ts'))
  ;({ incidentApi } = await vite.ssrLoadModule('/src/services/incidents.ts'))
  ;({ integrationApi } = await vite.ssrLoadModule('/src/services/integrations.ts'))
  ;({ maintenanceApi } = await vite.ssrLoadModule('/src/services/maintenance.ts'))
  ;({ statusPageApi } = await vite.ssrLoadModule('/src/services/statusPages.ts'))
  ;({ teamApi, findWorkspace, canEditWorkspace, workspaceQuery } = await vite.ssrLoadModule('/src/services/teams.ts'))
  http.defaults.baseURL = `${origin}/api`
  http.interceptors.request.use(config => { configs.push(config); return config })
})

after(async () => {
  await vite?.close()
  server?.closeAllConnections()
  await new Promise(resolve => server ? server.close(resolve) : resolve())
  if (originalWindow === undefined) delete globalThis.window
  else globalThis.window = originalWindow
})

test('Axios keeps cookie, CSRF and timeout settings', () => {
  assert.equal(http.defaults.withCredentials, true)
  assert.equal(http.defaults.withXSRFToken, false)
  assert.equal(http.defaults.timeout, 15000)
})

test('public monitoring requests return parsed data and query parameters', async () => {
  requests = []
  assert.deepEqual(await api.getMonitors(), { ok: true })
  assert.deepEqual(await api.getHistory(7, 6), { ok: true })
  await api.getStats(7)
  await api.getRecentPings(7, 3)
  assert.equal(requests[1].query.get('hours'), '6')
  assert.equal(requests[3].query.get('limit'), '3')
  assert.equal(requests.length, 4)
  assert.ok(requests.every(request => request.method === 'GET'))
})

test('an anonymous session returns null without a redirect', async () => {
  nextReply = { path: '/api/auth/me', status: 401, data: {} }
  assert.equal(await authApi.me(), null)
  assert.equal(window.location.hash, '#dashboard')
})

test('login sends form data with a fresh CSRF header and then reads the account', async () => {
  requests = []
  const email = 'person+test@example.com'
  const password = 'test & + password'
  assert.deepEqual(await authApi.login(email, password), account)
  assert.deepEqual(requests.map(request => request.path), ['/api/auth/csrf', '/api/auth/login', '/api/auth/me'])
  assert.match(requests[1].headers['content-type'], /^application\/x-www-form-urlencoded/)
  const form = new URLSearchParams(requests[1].body)
  assert.equal(form.get('email'), email)
  assert.equal(form.get('password'), password)
  assert.equal(requests[1].headers['x-csrf-token'], `token-${tokenNumber}`)
})

test('registration sends JSON and logout works with an empty response', async () => {
  requests = []
  await authApi.register('new@example.com', 'test password')
  assert.match(requests[1].headers['content-type'], /^application\/json/)
  assert.deepEqual(JSON.parse(requests[1].body), { email: 'new@example.com', password: 'test password' })
  const registrationToken = requests[1].headers['x-csrf-token']
  await authApi.logout()
  assert.notEqual(requests[3].headers['x-csrf-token'], registrationToken)
  assert.equal(requests[3].path, '/api/auth/logout')
})

test('backend errors keep their message without exposing Axios request details', async () => {
  nextReply = { path: '/api/auth/login', status: 400, data: { message: 'Email or password is incorrect.' } }
  await assert.rejects(authApi.login('owner@example.com', 'wrong'), error => {
    assert.equal(error.message, 'Email or password is incorrect.')
    assert.equal(error.config, undefined)
    return true
  })
  nextReply = { path: '/api/auth/providers', status: 500, html: '<html>Server error</html>' }
  await assert.rejects(authApi.providers(), /The request could not be completed/)
})

test('failed CSRF lookup stops the write request', async () => {
  requests = []
  nextReply = { path: '/api/auth/csrf', status: 403, data: { message: 'CSRF unavailable.' } }
  await assert.rejects(authApi.register('new@example.com', 'test password'), /CSRF unavailable/)
  assert.deepEqual(requests.map(request => request.path), ['/api/auth/csrf'])
})

test('network failures are readable and do not retry', async () => {
  requests = []
  nextReply = { path: '/api/monitors', disconnect: true }
  await assert.rejects(api.getMonitors(), /Could not reach the server/)
  assert.equal(requests.length, 1)
})

test('OAuth URLs still point to the backend authorization endpoint', async () => {
  assert.equal(authApi.oauthUrl('github'), `${origin}/oauth2/authorization/github`)
  assert.equal(authApi.oauthUrl('discord'), `${origin}/oauth2/authorization/discord`)
  assert.match((await csrfHeaders())['X-CSRF-TOKEN'], /^token-/)
})

test('all dashboard reads keep their endpoints and return response.data', async () => {
  const reads = [
    [() => monitorApi.list(), '/api/monitors/mine'],
    [() => monitorApi.list(9), '/api/monitors/mine?workspaceId=9'],
    [() => monitorApi.integrations(9), '/api/monitors/workspace/integrations?workspaceId=9'],
    [() => integrationApi.list(), '/api/integrations'],
    [() => integrationApi.selected(7), '/api/integrations/monitors/7'],
    [() => maintenanceApi.list(2), '/api/maintenance/mine?page=2'],
    [() => maintenanceApi.get(7), '/api/maintenance/7'],
    [() => incidentApi.list('open', 2), '/api/incidents?status=open&page=2'],
    [() => incidentApi.get(7), '/api/incidents/7'],
    [() => incidentApi.analysisSettings(), '/api/incidents/analysis-settings'],
    [() => statusPageApi.mine(), '/api/status-pages/mine'],
    [() => statusPageApi.publicPage('our page'), '/api/status-pages/our%20page'],
    [() => teamApi.list(), '/api/team-members'],
  ]
  for (const [call, expected] of reads) {
    requests = []
    assert.deepEqual(await call(), { ok: true })
    assert.equal(requests.length, 1)
    const actual = requests[0].path + (requests[0].query.size ? `?${requests[0].query}` : '')
    assert.equal(actual, expected)
    assert.equal(requests[0].method, 'GET')
  }
})

test('dashboard writes keep their methods, JSON bodies and fresh CSRF headers', async () => {
  const monitor = { name: 'Website', url: 'https://example.com', httpMethod: 'GET', intervalSeconds: 60, timeoutSeconds: 10, integrationIds: [] }
  const maintenance = { title: 'Update', description: '', startsAt: '2030-01-01T10:00:00Z', endsAt: '2030-01-01T11:00:00Z', monitorIds: [7], published: false }
  const page = { name: 'Status', slug: 'status', description: '', monitorIds: [7], pinnedDefault: false }
  const writes = [
    [() => monitorApi.create(monitor, 9), 'POST', '/api/monitors?workspaceId=9', monitor],
    [() => monitorApi.update(7, monitor), 'PUT', '/api/monitors/7', monitor],
    [() => monitorApi.togglePause(7), 'PATCH', '/api/monitors/7/toggle-pause'],
    [() => monitorApi.remove(7), 'DELETE', '/api/monitors/7'],
    [() => integrationApi.save(null, 'Alerts', 'https://example.com/webhook'), 'POST', '/api/integrations', { name: 'Alerts', webhookUrl: 'https://example.com/webhook' }],
    [() => integrationApi.save(7, 'Alerts', 'https://example.com/webhook'), 'PUT', '/api/integrations/7', { name: 'Alerts', webhookUrl: 'https://example.com/webhook' }],
    [() => integrationApi.test(7), 'POST', '/api/integrations/7/test'],
    [() => integrationApi.remove(7), 'DELETE', '/api/integrations/7'],
    [() => maintenanceApi.save(null, maintenance), 'POST', '/api/maintenance', maintenance],
    [() => maintenanceApi.save(7, maintenance), 'PUT', '/api/maintenance/7', maintenance],
    [() => maintenanceApi.cancel(7), 'PATCH', '/api/maintenance/7/cancel'],
    [() => maintenanceApi.publish(7, false), 'PATCH', '/api/maintenance/7/publication', { published: false }],
    [() => maintenanceApi.remove(7), 'DELETE', '/api/maintenance/7'],
    [() => incidentApi.create({ monitorId: 7, title: 'Outage', message: 'Checking' }), 'POST', '/api/incidents', { monitorId: 7, title: 'Outage', message: 'Checking' }],
    [() => incidentApi.update(7, 'IDENTIFIED', 'Checking'), 'POST', '/api/incidents/7/updates', { stage: 'IDENTIFIED', message: 'Checking' }],
    [() => incidentApi.publish(7, 'Outage', false), 'PATCH', '/api/incidents/7', { title: 'Outage', published: false }],
    [() => incidentApi.analyze(7, 'Test context'), 'POST', '/api/incidents/7/analysis', { consent: true, context: 'Test context' }],
    [() => statusPageApi.save(null, page), 'POST', '/api/status-pages', page],
    [() => statusPageApi.save(7, page), 'PUT', '/api/status-pages/7', page],
    [() => statusPageApi.pin(7, false), 'PATCH', '/api/status-pages/7/pin-default', { pinnedDefault: false }],
    [() => statusPageApi.remove(7), 'DELETE', '/api/status-pages/7'],
    [() => teamApi.invite('member@example.com', 'VIEWER'), 'POST', '/api/team-members', { email: 'member@example.com', role: 'VIEWER' }],
    [() => teamApi.setRole(7, 'EDITOR'), 'PATCH', '/api/team-members/7', { role: 'EDITOR' }],
    [() => teamApi.accept(7), 'POST', '/api/team-members/7/accept'],
    [() => teamApi.remove(7), 'DELETE', '/api/team-members/7'],
  ]
  for (const [call, method, expected, body] of writes) {
    requests = []
    await call()
    assert.equal(requests.length, 2)
    assert.equal(requests[0].path, '/api/auth/csrf')
    const actual = requests[1].path + (requests[1].query.size ? `?${requests[1].query}` : '')
    assert.equal(actual, expected)
    assert.equal(requests[1].method, method)
    assert.equal(requests[1].headers['x-csrf-token'], `token-${tokenNumber}`)
    if (body) {
      assert.match(requests[1].headers['content-type'], /^application\/json/)
      assert.deepEqual(JSON.parse(requests[1].body), body)
    } else assert.equal(requests[1].body, '')
  }
})

test('a missing default status page returns null for the landing page', async () => {
  assert.equal(await statusPageApi.defaultPage(), null)
})

test('expired dashboard sessions redirect to login in every service', async () => {
  const reads = [
    [() => monitorApi.list(), '/api/monitors/mine'],
    [() => incidentApi.list(), '/api/incidents'],
    [() => integrationApi.list(), '/api/integrations'],
    [() => maintenanceApi.list(), '/api/maintenance/mine'],
    [() => statusPageApi.mine(), '/api/status-pages/mine'],
    [() => teamApi.list(), '/api/team-members'],
  ]
  for (const [call, path] of reads) {
    window.location.hash = '#dashboard'
    nextReply = { path, status: 401, data: {} }
    await assert.rejects(call(), /Your session expired/)
    assert.equal(window.location.hash, '#login')
  }
})

test('permission, validation, conflict and rate-limit errors stay visible', async () => {
  for (const status of [400, 403, 409, 429, 502]) {
    nextReply = { path: '/api/maintenance/7', status, data: { message: `Test error ${status}` } }
    window.location.hash = '#dashboard'
    await assert.rejects(maintenanceApi.get(7), new RegExp(`Test error ${status}`))
    assert.equal(window.location.hash, '#dashboard')
  }
  nextReply = { path: '/api/status-pages/missing', status: 404, data: {} }
  await assert.rejects(statusPageApi.publicPage('missing'), /This status page was not found/)
  nextReply = { path: '/api/monitors/mine', status: 404, data: {} }
  await assert.rejects(monitorApi.list(), /This monitor is no longer available/)
})

test('authentication and incident requests keep their own timeout values', async () => {
  configs = []
  await authApi.me()
  await monitorApi.list()
  await incidentApi.analysisSettings()
  assert.deepEqual(configs.map(config => config.timeout), [10000, 15000, 30000])
})

test('timed-out requests show an error without retrying', async () => {
  requests = []
  const normalTimeout = http.defaults.timeout
  http.defaults.timeout = 30
  nextReply = { path: '/api/monitors', status: 200, data: {}, delay: 100 }
  try {
    await assert.rejects(api.getMonitors(), /Could not reach the server/)
    assert.equal(requests.length, 1)
  } finally {
    http.defaults.timeout = normalTimeout
  }
})

test('invalid JSON is rejected rather than passed to a React page', async () => {
  nextReply = { path: '/api/monitors', status: 200, html: '{invalid json}' }
  await assert.rejects(api.getMonitors(), /Error on loading Monitors/)
})

test('a non-JSON 401 still returns to login', async () => {
  window.location.hash = '#dashboard'
  nextReply = { path: '/api/maintenance/mine', status: 401, html: '<html>Sign in</html>' }
  await assert.rejects(maintenanceApi.list(), /Your session expired/)
  assert.equal(window.location.hash, '#login')
})

test('incident and maintenance lists send the selected workspace', async () => {
  requests = []
  await incidentApi.list('open', 1, 9)
  await maintenanceApi.list(1, 9)
  assert.equal(requests[0].query.get('workspaceId'), '9')
  assert.equal(requests[0].query.get('status'), 'open')
  assert.equal(requests[1].query.get('workspaceId'), '9')
  assert.equal(requests[1].query.get('page'), '1')
})

test('maintenance creation includes the workspace without trusting it during editing', async () => {
  const input = { title: 'Work', description: '', startsAt: '2030-01-01T10:00:00Z', endsAt: '2030-01-01T11:00:00Z', monitorIds: [7], published: false }
  requests = []
  await maintenanceApi.save(null, input, 9)
  assert.equal(requests[1].query.get('workspaceId'), '9')
  await maintenanceApi.save(7, input, 99)
  assert.equal(requests[3].path, '/api/maintenance/7')
  assert.equal(requests[3].query.has('workspaceId'), false)
})

test('workspace helpers keep missing workspaces and viewers read-only', () => {
  const workspaces = [
    { id: 1, role: 'OWNER' }, { id: 9, role: 'EDITOR' }, { id: 10, role: 'VIEWER' },
  ]
  assert.equal(findWorkspace(workspaces, null).id, 1)
  assert.equal(canEditWorkspace(findWorkspace(workspaces, 9)), true)
  assert.equal(canEditWorkspace(findWorkspace(workspaces, 10)), false)
  assert.equal(canEditWorkspace(findWorkspace(workspaces, 999)), false)
  assert.equal(workspaceQuery(null), '')
  assert.equal(workspaceQuery(9), '?workspace=9')
})
