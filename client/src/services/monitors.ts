import { requestApi } from './requests'
import type { Monitor, MonitorInput } from '../types/monitor'
import type { IntegrationListing } from './integrations'

function request(path: string, method = 'GET', monitor?: MonitorInput) {
  return requestApi(`/monitors${path}`, method, monitor, {
    fallback: 'Could not save this change. Please try again.',
    notFound: 'This monitor is no longer available. Refresh the list.',
  })
}

export const monitorApi = {
  async list(workspaceId: number | null = null): Promise<Monitor[]> {
    return (await request(`/mine${workspaceId === null ? '' : `?workspaceId=${workspaceId}`}`)).data
  },
  async create(monitor: MonitorInput, workspaceId: number | null = null): Promise<Monitor> {
    return (await request(workspaceId === null ? '' : `?workspaceId=${workspaceId}`, 'POST', monitor)).data
  },
  async integrations(workspaceId: number | null = null): Promise<IntegrationListing> {
    return (await request(`/workspace/integrations${workspaceId === null ? '' : `?workspaceId=${workspaceId}`}`)).data
  },
  async update(id: number, monitor: MonitorInput): Promise<Monitor> {
    return (await request(`/${id}`, 'PUT', monitor)).data
  },
  async togglePause(id: number): Promise<Monitor> {
    return (await request(`/${id}/toggle-pause`, 'PATCH')).data
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  },
}
