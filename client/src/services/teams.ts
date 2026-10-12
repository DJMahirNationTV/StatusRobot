import { requestApi } from './requests'

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

export function findWorkspace(workspaces: Workspace[], id: number | null) {
  return workspaces.find(item => id === null ? item.role === 'OWNER' : item.id === id)
}

export function canEditWorkspace(workspace: Workspace | undefined | null) {
  return workspace?.role === 'OWNER' || workspace?.role === 'EDITOR'
}

export function workspaceQuery(id: number | null) {
  return id === null ? '' : `?workspace=${id}`
}

function request(path = '', method = 'GET', data?: { email?: string; role: TeamRole }) {
  return requestApi(`/team-members${path}`, method, data, { fallback: 'Could not update the team. Refresh and try again.' })
}

export const teamApi = {
  async list(): Promise<TeamListing> {
    return (await request()).data
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
