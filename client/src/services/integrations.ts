import { http, requestError } from './api'
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
  try {
    return await http.request({ url: `/integrations${path}`, method, headers, data })
  } catch (error) {
    throw requestError(error, 'Could not complete this request. Refresh and try again.', true)
  }
}

export const integrationApi = {
  async list(): Promise<IntegrationListing> {
    return (await request('')).data
  },
  async selected(monitorId: number): Promise<number[]> {
    return (await request(`/monitors/${monitorId}`)).data
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
    ).data
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  },
  async test(id: number) {
    await request(`/${id}/test`, 'POST')
  }
}
