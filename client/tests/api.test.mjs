import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { createServer as createHttpServer } from 'node:http'
import { createServer as createViteServer } from 'vite'

let vite, server, http, api, authApi, csrfHeaders, origin
let requests = []
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
  http.defaults.baseURL = `${origin}/api`
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
