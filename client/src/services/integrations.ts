import { API_BASE } from './api'
import { csrfHeaders } from './auth'

export interface Integration {
  id: number
  name: string
}
export interface IntegrationListing {
  configured: boolean
  integrations: Integration[]
}

async function request(
  path: string,
  method = 'GET',
  data?: { name: string; webhookUrl: string }
) {
  const headers = method === 'GET' ? {} : await csrfHeaders()
  let response: Response
  try {
    response = await fetch(`${API_BASE}/integrations${path}`, {
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
  if (response.status === 401) window.location.hash = '#login'
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(
      body?.message || 'Could not complete this request. Refresh and try again.'
    )
  }
  return response
}

export const integrationApi = {
  async list(): Promise<IntegrationListing> {
    return (await request('')).json()
  },
  async selected(monitorId: number): Promise<number[]> {
    return (await request(`/monitors/${monitorId}`)).json()
  },
  async save(
    id: number | null,
    name: string,
    webhookUrl: string
  ): Promise<Integration> {
    return (
      await request(id === null ? '' : `/${id}`, id === null ? 'POST' : 'PUT', {
        name,
        webhookUrl
      })
    ).json()
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  },
  async test(id: number) {
    await request(`/${id}/test`, 'POST')
  }
}
