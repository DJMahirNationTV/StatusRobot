import { API_BASE } from './api'

export interface Incident {
  id: number
  monitorId: number
  monitorName: string
  cause: string
  startedAt: string
  resolvedAt: string | null
}

export type IncidentFilter = 'all' | 'open' | 'resolved'

export interface IncidentListing {
  incidents: Incident[]
  page: number
  totalPages: number
  totalElements: number
}

async function request(path: string) {
  let response: Response
  try {
    response = await fetch(`${API_BASE}/incidents${path}`, {
      credentials: 'include',
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
    throw new Error(body?.message || 'Could not load incidents. Please try again.')
  }
  return response.json()
}

export const incidentApi = {
  list(status: IncidentFilter = 'all', page = 0): Promise<IncidentListing> {
    return request(`?${new URLSearchParams({ status, page: String(page) })}`)
  },
  get(id: number): Promise<Incident> {
    return request(`/${id}`)
  },
}
