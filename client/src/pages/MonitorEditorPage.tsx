import { useEffect, useState } from 'react'
import { ChevronLeft } from 'lucide-react'
import { MonitorForm } from '../components/MonitorForm'
import { monitorApi } from '../services/monitors'
import { integrationApi } from '../services/integrations'
import type { IntegrationListing } from '../services/integrations'
import type { Monitor, MonitorInput } from '../types/monitor'
import { teamApi } from '../services/teams'

export function MonitorEditorPage({ monitorId, workspaceId }: { monitorId: number | null; workspaceId: number | null }) {
  const workspaceQuery = workspaceId === null ? '' : `?workspace=${workspaceId}`
  const [data, setData] = useState<{
    monitor: Monitor | null
    listing: IntegrationListing
    selected: number[]
    shared: boolean
  } | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([
      monitorApi.integrations(workspaceId),
      monitorId === null ? Promise.resolve([]) : monitorApi.list(workspaceId),
      monitorId === null
        ? Promise.resolve([])
        : integrationApi.selected(monitorId),
      teamApi.list()
    ])
      .then(([listing, monitors, selected, team]) => {
        const workspace = team.workspaces.find(item => workspaceId === null ? item.role === 'OWNER' : item.id === workspaceId)
        if (!workspace || workspace.role === 'VIEWER') throw new Error('You do not have permission to edit monitors in this workspace.')
        const monitor = monitors.find((item) => item.id === monitorId) || null
        if (monitorId !== null && !monitor)
          throw new Error('Monitor not found in this workspace.')
        if (active) setData({ monitor, listing, selected, shared: workspace.role !== 'OWNER' })
      })
      .catch((exception) => {
        if (active) setError(exception.message)
      })
    return () => {
      active = false
    }
  }, [monitorId, workspaceId, attempt])

  async function save(input: MonitorInput) {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      if (monitorId === null) await monitorApi.create(input, workspaceId)
      else await monitorApi.update(monitorId, input)
      window.location.hash = `#dashboard/monitoring/${workspaceQuery}`
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : 'Could not save this monitor.'
      )
    } finally {
      setBusy(false)
    }
  }

  async function refreshIntegrations() {
    setBusy(true)
    setError('')
    try {
      const listing = await monitorApi.integrations(workspaceId)
      setData((current) => current && { ...current, listing })
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : 'Could not load integrations.'
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <a href={`#dashboard/monitoring/${workspaceQuery}`} className="dashboard-action mb-6">
        <ChevronLeft size={14} />
        Monitoring
      </a>
      <h1 className="dashboard-heading mb-7">
        {monitorId === null ? 'Add a monitor' : 'Edit monitor'}
        <span className="text-green">.</span>
      </h1>
      {error && (
        <div role="alert" className="dashboard-error mb-5">
          {error}
          {!data && (
            <button
              onClick={() => {
                setError('')
                setAttempt((value) => value + 1)
              }}
              className="ml-3 underline"
            >
              Retry
            </button>
          )}
        </div>
      )}
      {!data && !error && (
        <p role="status" className="text-sm text-muted">
          Loading monitor settings...
        </p>
      )}
      {data && (
        <MonitorForm
          monitor={data.monitor}
          integrations={data.listing.integrations}
          configured={data.listing.configured}
          selected={data.selected}
          shared={data.shared}
          busy={busy}
          onRefreshIntegrations={refreshIntegrations}
          onSave={save}
          onCancel={() => {
            window.location.hash = `#dashboard/monitoring/${workspaceQuery}`
          }}
        />
      )}
    </>
  )
}
