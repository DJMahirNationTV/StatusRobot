import { findWorkspace, workspaceQuery } from '../services/teams'
import type { Workspace } from '../services/teams'

interface WorkspacePickerProps {
  workspaces: Workspace[]
  workspaceId: number | null
  page: string
  ownLabel: string
  disabled?: boolean
}

export function WorkspacePicker({ workspaces, workspaceId, page, ownLabel, disabled }: WorkspacePickerProps) {
  const workspace = findWorkspace(workspaces, workspaceId)
  if (workspaces.length === 0) return null
  return <div className="mb-6 flex flex-wrap items-center gap-3">
    <label className="text-xs text-muted">Workspace
      <select value={workspace?.id ?? ''} disabled={disabled} onChange={event => {
        window.location.hash = `${page}${workspaceQuery(Number(event.target.value))}`
      }} className="form-input mt-2 sm:ml-3 sm:mt-0 sm:inline-block sm:w-auto">
        {workspaces.map(item => <option key={item.id} value={item.id}>{item.role === 'OWNER' ? ownLabel : item.email}</option>)}
      </select>
    </label>
    {workspace && workspace.role !== 'OWNER' && <span className="rounded-lg bg-sage px-3 py-2 text-xs text-green">{workspace.role === 'EDITOR' ? 'Editor access' : 'Viewer access'}</span>}
  </div>
}
