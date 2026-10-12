import { requestApi } from './requests'
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

function request(path: string, method = 'GET', data?: unknown) {
  return requestApi(`/status-pages${path}`, method, data, { notFound: 'This status page was not found.' })
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
