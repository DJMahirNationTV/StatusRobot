import { API_BASE } from './api'
import { csrfHeaders } from './auth'

export type IncidentStage = 'INVESTIGATING' | 'IDENTIFIED' | 'MONITORING' | 'RESOLVED'
export const stageLabels: Record<IncidentStage, string> = {
  INVESTIGATING: 'Investigating', IDENTIFIED: 'Identified', MONITORING: 'Monitoring', RESOLVED: 'Resolved',
}
export interface IncidentUpdate {
  stage: IncidentStage
  message: string
  createdAt: string
}
export interface PublicIncident {
  id: number
  monitorId: number
  title: string
  stage: IncidentStage
  startedAt: string
  resolvedAt: string | null
  updates: IncidentUpdate[]
}

export interface Incident extends PublicIncident {
  monitorName: string
  cause: string
  manual: boolean
  published: boolean
}

export type IncidentFilter = 'all' | 'open' | 'resolved'

export interface IncidentListing {
  incidents: Incident[]
  page: number
  totalPages: number
  totalElements: number
}

async function request(path: string, method = 'GET', data?: unknown) {
  const headers = method === 'GET' ? {} : await csrfHeaders()
  let response: Response
  try {
    response = await fetch(`${API_BASE}/incidents${path}`, {
      credentials: 'include',
      method,
      headers: { ...headers, ...(data ? { 'Content-Type': 'application/json' } : {}) },
      body: data ? JSON.stringify(data) : undefined,
      signal: AbortSignal.timeout(30000),
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
    throw new Error(body?.message || 'Could not complete this request. Please try again.')
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
  create(input: { monitorId: number, title: string, message: string }): Promise<Incident> {
    return request('', 'POST', input)
  },
  update(id: number, stage: IncidentStage, message: string): Promise<Incident> {
    return request(`/${id}/updates`, 'POST', { stage, message })
  },
  publish(id: number, title: string, published: boolean): Promise<Incident> {
    return request(`/${id}`, 'PATCH', { title, published })
  },
  analysisSettings(): Promise<{ enabled: boolean }> {
    return request('/analysis-settings')
  },
  analyze(id: number, context: string): Promise<{ text: string, model: string }> {
    return request(`/${id}/analysis`, 'POST', { consent: true, context })
  },
}
