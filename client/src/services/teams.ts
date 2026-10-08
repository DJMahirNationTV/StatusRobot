import { API_BASE } from './api'
import { csrfHeaders } from './auth'

export type TeamRole = 'VIEWER' | 'EDITOR'
export interface TeamMember {
  id: number
  email: string
  role: TeamRole
  accepted: boolean
}
export interface Workspace {
  id: number
  email: string
  role: TeamRole | 'OWNER'
  membershipId: number | null
}
export interface TeamListing {
  members: TeamMember[]
  workspaces: Workspace[]
  invitations: { id: number; email: string; role: TeamRole }[]
}

async function request(path = '', method = 'GET', data?: { email?: string; role: TeamRole }) {
  const headers = method === 'GET' ? {} : await csrfHeaders()
  let response: Response
  try {
    response = await fetch(`${API_BASE}/team-members${path}`, {
      method,
      credentials: 'include',
      headers: { ...headers, ...(data ? { 'Content-Type': 'application/json' } : {}) },
      body: data ? JSON.stringify(data) : undefined,
      signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new Error('Could not reach the server. Please try again.')
  }
  if (response.status === 401) window.location.hash = '#login'
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || 'Could not update the team. Refresh and try again.')
  }
  return response
}

export const teamApi = {
  async list(): Promise<TeamListing> {
    return (await request()).json()
  },
  async invite(email: string, role: TeamRole) {
    await request('', 'POST', { email, role })
  },
  async setRole(id: number, role: TeamRole) {
    await request(`/${id}`, 'PATCH', { role })
  },
  async accept(id: number) {
    await request(`/${id}/accept`, 'POST')
  },
  async remove(id: number) {
    await request(`/${id}`, 'DELETE')
  },
}
