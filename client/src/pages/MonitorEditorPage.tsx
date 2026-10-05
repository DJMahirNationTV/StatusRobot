import { useEffect, useState } from 'react'
import { ChevronLeft } from 'lucide-react'
import { MonitorForm } from '../components/MonitorForm'
import { monitorApi } from '../services/monitors'
import { integrationApi } from '../services/integrations'
import type { IntegrationListing } from '../services/integrations'
import type { Monitor, MonitorInput } from '../types/monitor'

export function MonitorEditorPage({ monitorId }: { monitorId: number | null }) {
  const [data, setData] = useState<{
    monitor: Monitor | null
    listing: IntegrationListing
    selected: number[]
  } | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([
      integrationApi.list(),
      monitorId === null ? Promise.resolve([]) : monitorApi.list(),
      monitorId === null
        ? Promise.resolve([])
        : integrationApi.selected(monitorId)
    ])
      .then(([listing, monitors, selected]) => {
        const monitor = monitors.find((item) => item.id === monitorId) || null
        if (monitorId !== null && !monitor)
          throw new Error('Monitor not found in your account.')
        if (active) setData({ monitor, listing, selected })
      })
      .catch((exception) => {
        if (active) setError(exception.message)
      })
    return () => {
      active = false
    }
  }, [monitorId, attempt])

  async function save(input: MonitorInput) {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      if (monitorId === null) await monitorApi.create(input)
      else await monitorApi.update(monitorId, input)
      window.location.hash = '#dashboard/monitoring/'
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
      const listing = await integrationApi.list()
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
      <a href="#dashboard/monitoring/" className="dashboard-action mb-6">
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
          busy={busy}
          onRefreshIntegrations={refreshIntegrations}
          onSave={save}
          onCancel={() => {
            window.location.hash = '#dashboard/monitoring/'
          }}
        />
      )}
    </>
  )
}
