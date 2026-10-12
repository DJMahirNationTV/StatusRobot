import { http, requestError } from './api'
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
  try {
    const response = await http.request({
      url: `/incidents${path}`, method, headers, data,
      timeout: 30000, // Incident help can take longer than normal requests.
    })
    return response.data
  } catch (error) {
    throw requestError(error, 'Could not complete this request. Please try again.', true)
  }
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
