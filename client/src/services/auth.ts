import axios from 'axios'
import { API_BASE, http, requestError } from './api'

export interface AuthUser {
  id: number
  email: string
  provider: 'local' | 'github' | 'discord'
}

export async function csrfHeaders(): Promise<Record<string, string>> {
  try {
    const response = await http.get<{ token: string; headerName: string }>('/auth/csrf', { timeout: 10000 })
    const csrf = response.data
    return { [csrf.headerName]: csrf.token }
  } catch (error) {
    throw requestError(error, 'The request could not be completed. Please try again.')
  }
}

async function post(path: string, data?: unknown, contentType?: string) {
  const headers = await csrfHeaders()
  if (contentType) headers['Content-Type'] = contentType
  try {
    await http.post(`/auth${path}`, data, { headers, timeout: 10000 })
  } catch (error) {
    throw requestError(error, 'The request could not be completed. Please try again.')
  }
}

export const authApi = {
  async me(): Promise<AuthUser | null> {
    try {
      const response = await http.get<AuthUser>('/auth/me', { timeout: 10000 })
      return response.data
    } catch (error) {
      // A missing session is normal before signing in.
      if (axios.isAxiosError(error) && error.response?.status === 401) return null
      throw requestError(error, 'The request could not be completed. Please try again.')
    }
  },

  async providers(): Promise<string[]> {
    try {
      const response = await http.get<string[]>('/auth/providers', { timeout: 10000 })
      return response.data
    } catch (error) {
      throw requestError(error, 'The request could not be completed. Please try again.')
    }
  },

  async register(email: string, password: string) {
    await post('/register', { email, password })
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
