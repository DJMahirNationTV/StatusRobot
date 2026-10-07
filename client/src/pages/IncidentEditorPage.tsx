import { useEffect, useState } from 'react'
import { ChevronLeft } from 'lucide-react'
import { incidentApi } from '../services/incidents'
import { monitorApi } from '../services/monitors'
import type { Monitor } from '../types/monitor'

export function IncidentEditorPage() {
  const [monitors, setMonitors] = useState<Monitor[] | null>(null)
  const [monitorId, setMonitorId] = useState('')
  const [title, setTitle] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    monitorApi.list().then(list => { if (active) setMonitors(list) })
      .catch(exception => { if (active) setError(exception.message) })
    return () => { active = false }
  }, [attempt])

  async function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const incident = await incidentApi.create({ monitorId: Number(monitorId), title: title.trim(), message: message.trim() })
      window.location.hash = `#dashboard/incidents/${incident.id}/`
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Could not create this incident.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="max-w-3xl">
      <a href="#dashboard/incidents/" className="dashboard-action mb-6"><ChevronLeft size={14} />Incidents</a>
      <h1 className="dashboard-heading mb-7">Add an incident<span className="text-green">.</span></h1>
      {error && <p role="alert" className="dashboard-error mb-5">{error}
        {!monitors && <button className="ml-3 underline" onClick={() => { setError(''); setAttempt(value => value + 1) }}>Retry</button>}
      </p>}
      {!monitors && !error && <p role="status" className="text-sm text-muted">Loading your monitors...</p>}
      {monitors?.length === 0 && <div className="dashboard-panel p-6">
        <p className="text-sm text-muted">Add a monitor before creating an incident.</p>
        <a href="#dashboard/monitoring/new/" className="button button-green mt-5">Add a monitor</a>
      </div>}
      {monitors && monitors.length > 0 && <form onSubmit={create} className="dashboard-panel space-y-6 p-6">
        <fieldset disabled={busy} className="space-y-5">
          <label className="block text-sm font-medium">Affected monitor
            <select required value={monitorId} onChange={event => setMonitorId(event.target.value)} className="form-input mt-2 w-full">
              <option value="">Select a monitor</option>
              {monitors.map(monitor => <option key={monitor.id} value={monitor.id}>{monitor.name}</option>)}
            </select>
          </label>
          <label className="block text-sm font-medium">Incident title
            <input required maxLength={100} value={title} onChange={event => setTitle(event.target.value)} className="form-input mt-2 w-full" placeholder="For example, delayed API requests" />
          </label>
          <label className="block text-sm font-medium">First update
            <textarea required maxLength={3000} rows={5} value={message} onChange={event => setMessage(event.target.value)} className="form-input mt-2 w-full" placeholder="What is affected and what are you checking?" />
          </label>
        </fieldset>
        <p className="text-xs leading-6 text-muted">Starts as investigating and stays private until you publish it. Monitor checks will not close a manual incident. You can add updates and resolve it from its details page.</p>
        <button disabled={busy || !title.trim() || !message.trim() || !monitorId} className="button button-green">{busy ? 'Creating...' : 'Create incident'}</button>
      </form>}
    </div>
  )
}
