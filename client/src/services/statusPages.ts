import axios from 'axios'
import { http, requestError } from './api'
import { csrfHeaders } from './auth'
import type { Monitor } from '../types/monitor'
import type { PublicIncident } from './incidents'
import type { PublicMaintenance } from './maintenance'

export interface StatusPageData {
  id: number
  name: string
  slug: string
  description: string
  pinnedDefault: boolean
  monitors: Monitor[]
  incidents: PublicIncident[]
  maintenance: PublicMaintenance[]
}
export interface StatusPageInput {
  name: string
  slug: string
  description: string
  monitorIds: number[]
  pinnedDefault: boolean
}
export interface StatusPageListing {
  canPinDefault: boolean
  pages: StatusPageData[]
}

async function request(path: string, method = 'GET', data?: unknown) {
  const headers = method === 'GET' ? {} : await csrfHeaders()
  try {
    return await http.request({ url: `/status-pages${path}`, method, headers, data })
  } catch (error) {
    const fallback = axios.isAxiosError(error) && error.response?.status === 404
      ? 'This status page was not found.'
      : 'Could not complete this request. Please try again.'
    throw requestError(error, fallback, true)
  }
}

export const statusPageApi = {
  async mine(): Promise<StatusPageListing> {
    return (await request('/mine')).data
  },
  async publicPage(slug: string): Promise<StatusPageData> {
    return (await request(`/${encodeURIComponent(slug)}`)).data
  },
  async defaultPage(): Promise<StatusPageData | null> {
    const response = await request('/default')
    return response.status === 204 ? null : response.data
  },
  async save(
    id: number | null,
    input: StatusPageInput
  ): Promise<StatusPageData> {
    return (
      await request(
        id === null ? '' : `/${id}`,
        id === null ? 'POST' : 'PUT',
        input
      )
    ).data
  },
  async pin(id: number, pinnedDefault: boolean) {
    await request(`/${id}/pin-default`, 'PATCH', { pinnedDefault })
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  }
}

export function statusPageUrl(slug: string) {
  return `#status/${encodeURIComponent(slug)}`
}
