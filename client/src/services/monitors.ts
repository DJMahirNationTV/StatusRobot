import { API_BASE } from './api'
import { csrfHeaders } from './auth'
import type { Monitor, MonitorInput } from '../types/monitor'

async function request(path: string, method = 'GET', monitor?: MonitorInput) {
  const headers = method === 'GET' ? {} : await csrfHeaders()
  let response: Response
  try {
    response = await fetch(`${API_BASE}/monitors${path}`, {
      method,
      credentials: 'include',
      headers: { ...headers, ...(monitor ? { 'Content-Type': 'application/json' } : {}) },
      body: monitor ? JSON.stringify(monitor) : undefined,
      signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new Error('Could not reach the server. Please try again.')
  }
  if (response.status === 401) {
    window.location.hash = '#login'
    throw new Error('Your session expired. Please sign in again.')
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || (response.status === 404
      ? 'This monitor is no longer available. Refresh the list.'
      : 'Could not save this change. Please try again.'))
  }
  return response
}

export const monitorApi = {
  async list(): Promise<Monitor[]> {
    return (await request('/mine')).json()
  },
  async create(monitor: MonitorInput): Promise<Monitor> {
    return (await request('', 'POST', monitor)).json()
  },
  async update(id: number, monitor: MonitorInput): Promise<Monitor> {
    return (await request(`/${id}`, 'PUT', monitor)).json()
  },
  async togglePause(id: number): Promise<Monitor> {
    return (await request(`/${id}/toggle-pause`, 'PATCH')).json()
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  },
}
