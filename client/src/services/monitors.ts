import axios from 'axios'
import { http, requestError } from './api'
import { csrfHeaders } from './auth'
import type { Monitor, MonitorInput } from '../types/monitor'
import type { IntegrationListing } from './integrations'

async function request(path: string, method = 'GET', monitor?: MonitorInput) {
  const headers = method === 'GET' ? {} : await csrfHeaders()
  try {
    return await http.request({ url: `/monitors${path}`, method, headers, data: monitor })
  } catch (error) {
    const fallback = axios.isAxiosError(error) && error.response?.status === 404
      ? 'This monitor is no longer available. Refresh the list.'
      : 'Could not save this change. Please try again.'
    throw requestError(error, fallback, true)
  }
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
