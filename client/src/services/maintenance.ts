import { API_BASE } from './api'
import { csrfHeaders } from './auth'

export type MaintenanceStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export const maintenanceLabels: Record<MaintenanceStatus, string> = {
  SCHEDULED: 'Scheduled', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', CANCELLED: 'Cancelled',
}
export interface PublicMaintenance {
  id: number
  title: string
  description: string
  startsAt: string
  endsAt: string
  status: MaintenanceStatus
  monitorIds: number[]
}
export interface Maintenance extends PublicMaintenance {
  published: boolean
  createdAt: string
}
export interface MaintenanceInput {
  title: string
  description: string
  startsAt: string
  endsAt: string
  monitorIds: number[]
  published: boolean
}
export interface MaintenanceListing {
  maintenance: Maintenance[]
  page: number
  totalPages: number
  totalElements: number
}

async function request(path: string, method = 'GET', data?: unknown) {
  const headers = method === 'GET' ? {} : await csrfHeaders()
  let response: Response
  try {
    response = await fetch(`${API_BASE}/maintenance${path}`, {
      method,
      credentials: 'include',
      headers: { ...headers, ...(data ? { 'Content-Type': 'application/json' } : {}) },
      body: data ? JSON.stringify(data) : undefined,
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
    throw new Error(body?.message || 'Could not save this change. Refresh and try again.')
  }
  return response
}

export const maintenanceApi = {
  async list(page = 0): Promise<MaintenanceListing> {
    return (await request(`/mine?page=${page}`)).json()
  },
  async get(id: number): Promise<Maintenance> {
    return (await request(`/${id}`)).json()
  },
  async save(id: number | null, input: MaintenanceInput): Promise<Maintenance> {
    return (await request(id === null ? '' : `/${id}`, id === null ? 'POST' : 'PUT', input)).json()
  },
  async cancel(id: number): Promise<Maintenance> {
    return (await request(`/${id}/cancel`, 'PATCH')).json()
  },
  async publish(id: number, published: boolean): Promise<Maintenance> {
    return (await request(`/${id}/publication`, 'PATCH', { published })).json()
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  },
}
