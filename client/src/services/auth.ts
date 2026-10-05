import { API_BASE } from './api'

export interface AuthUser {
  id: number
  email: string
  provider: 'local' | 'github' | 'discord'
}

async function checkResponse(response: Response) {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || 'The request could not be completed. Please try again.')
  }
  return response
}

async function request(path: string, options: RequestInit = {}) {
  try {
    return await fetch(`${API_BASE}/auth${path}`, {
      ...options,
      credentials: 'include',
      signal: AbortSignal.timeout(10000),
    })
  } catch {
    throw new Error('We could not reach the server. Please try again in a moment.')
  }
}

async function post(path: string, body?: BodyInit, contentType?: string) {
  const csrfResponse = await checkResponse(await request('/csrf'))
  const csrf: { token: string; headerName: string } = await csrfResponse.json()
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token }
  if (contentType) headers['Content-Type'] = contentType
  return checkResponse(await request(path, { method: 'POST', headers, body }))
}

export const authApi = {
  async me(): Promise<AuthUser | null> {
    const response = await request('/me')
    if (response.status === 401) return null
    return (await checkResponse(response)).json()
  },

  async providers(): Promise<string[]> {
    return (await checkResponse(await request('/providers'))).json()
  },

  async register(email: string, password: string) {
    await post('/register', JSON.stringify({ email, password }), 'application/json')
  },

  async login(email: string, password: string): Promise<AuthUser> {
    await post('/login', new URLSearchParams({ email, password }), 'application/x-www-form-urlencoded')
    const user = await this.me()
    if (!user) throw new Error('Your session could not be started. Please try again.')
    return user
  },

  async logout() {
    await post('/logout')
  },

  oauthUrl(provider: string) {
    return `${new URL(API_BASE, window.location.origin).origin}/oauth2/authorization/${provider}`
  },
}
