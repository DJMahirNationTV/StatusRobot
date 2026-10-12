import { http, requestError } from './api'
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
  try {
    return await http.request({ url: `/maintenance${path}`, method, headers, data })
  } catch (error) {
    throw requestError(error, 'Could not save this change. Refresh and try again.', true)
  }
}

export const maintenanceApi = {
  async list(page = 0): Promise<MaintenanceListing> {
    return (await request(`/mine?page=${page}`)).data
  },
  async get(id: number): Promise<Maintenance> {
    return (await request(`/${id}`)).data
  },
  async save(id: number | null, input: MaintenanceInput): Promise<Maintenance> {
    return (await request(id === null ? '' : `/${id}`, id === null ? 'POST' : 'PUT', input)).data
  },
  async cancel(id: number): Promise<Maintenance> {
    return (await request(`/${id}/cancel`, 'PATCH')).data
  },
  async publish(id: number, published: boolean): Promise<Maintenance> {
    return (await request(`/${id}/publication`, 'PATCH', { published })).data
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  },
}
