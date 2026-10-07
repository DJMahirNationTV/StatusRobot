import { API_BASE } from './api'
import { csrfHeaders } from './auth'
import type { Monitor } from '../types/monitor'
import type { PublicIncident } from './incidents'

export interface StatusPageData {
  id: number
  name: string
  slug: string
  description: string
  pinnedDefault: boolean
  monitors: Monitor[]
  incidents: PublicIncident[]
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
  let response: Response
  try {
    response = await fetch(`${API_BASE}/status-pages${path}`, {
      method,
      credentials: 'include',
      headers: {
        ...headers,
        ...(data ? { 'Content-Type': 'application/json' } : {})
      },
      body: data ? JSON.stringify(data) : undefined,
      signal: AbortSignal.timeout(15000)
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
    throw new Error(
      body?.message ||
        (response.status === 404
          ? 'This status page was not found.'
          : 'Could not complete this request. Please try again.')
    )
  }
  return response
}

export const statusPageApi = {
  async mine(): Promise<StatusPageListing> {
    return (await request('/mine')).json()
  },
  async publicPage(slug: string): Promise<StatusPageData> {
    return (await request(`/${encodeURIComponent(slug)}`)).json()
  },
  async defaultPage(): Promise<StatusPageData | null> {
    const response = await request('/default')
    return response.status === 204 ? null : response.json()
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
    ).json()
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
