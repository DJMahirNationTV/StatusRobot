import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ChevronLeft, Wrench } from 'lucide-react'
import { maintenanceApi } from '../services/maintenance'
import type { Maintenance } from '../services/maintenance'
import { monitorApi } from '../services/monitors'
import type { Monitor } from '../types/monitor'

export function MaintenanceEditorPage({ maintenanceId }: { maintenanceId: number | null }) {
  const [data, setData] = useState<{ maintenance: Maintenance | null; monitors: Monitor[] } | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [selected, setSelected] = useState<number[]>([])
  const [published, setPublished] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([monitorApi.list(), maintenanceId === null ? Promise.resolve(null) : maintenanceApi.get(maintenanceId)])
      .then(([monitors, maintenance]) => {
        if (!active) return
        setData({ maintenance, monitors })
        setTitle(maintenance?.title ?? '')
        setDescription(maintenance?.description ?? '')
        setStartsAt(toLocalDateTime(maintenance?.startsAt ?? new Date(Date.now() + 3600000).toISOString()))
        setEndsAt(toLocalDateTime(maintenance?.endsAt ?? new Date(Date.now() + 7200000).toISOString()))
        setSelected(maintenance?.monitorIds ?? [])
        setPublished(maintenance?.published ?? false)
      })
      .catch(exception => { if (active) setError(exception.message) })
    return () => { active = false }
  }, [maintenanceId, attempt])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || !data) return
    setError('')
    try {
      const start = toUtc(startsAt, data.maintenance?.startsAt)
      const end = toUtc(endsAt, data.maintenance?.endsAt)
      if (Date.parse(start) <= Date.now()) throw new Error('Choose a start time in the future.')
      if (Date.parse(end) <= Date.parse(start)) throw new Error('The end time must be after the start time.')
      if (!title.trim()) throw new Error('Enter a maintenance title.')
      if (selected.length === 0 || selected.length > 30) throw new Error('Select between 1 and 30 monitors.')
      setBusy(true)
      await maintenanceApi.save(maintenanceId, { title: title.trim(), description: description.trim(), startsAt: start, endsAt: end, monitorIds: selected, published })
      window.location.hash = '#dashboard/maintenance/'
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Could not save maintenance.')
    } finally {
      setBusy(false)
    }
  }

  const editable = !data?.maintenance || data.maintenance.status === 'SCHEDULED'

  return (
    <div className="max-w-4xl">
      <a href="#dashboard/maintenance/" className="dashboard-action mb-6"><ChevronLeft size={14} />Maintenance</a>
      <h1 className="dashboard-heading mb-7">{maintenanceId === null ? 'Schedule maintenance' : 'Edit maintenance'}<span className="text-green">.</span></h1>
      {error && <p role="alert" className="dashboard-error mb-5">{error}
        {!data && <button onClick={() => { setError(''); setAttempt(value => value + 1) }} className="ml-3 underline">Retry</button>}
      </p>}
      {!data && !error && <p role="status" className="text-sm text-muted">Loading your monitors...</p>}
      {data && !editable && <div className="dashboard-panel p-6">
        <p className="text-sm">Only upcoming maintenance can be edited. You can cancel ongoing work from the maintenance list.</p>
        <a href="#dashboard/maintenance/" className="dashboard-action mt-5">Back to maintenance</a>
      </div>}
      {data && editable && data.monitors.length === 0 && <div className="dashboard-panel p-6">
        <p className="text-sm text-muted">Add a monitor before scheduling maintenance.</p>
        <a href="#dashboard/monitoring/new/" className="button button-green mt-5">Add a monitor</a>
      </div>}
      {data && editable && data.monitors.length > 0 && <form onSubmit={save} className="space-y-6">
        <fieldset disabled={busy} className="dashboard-panel space-y-5 p-5 sm:p-7">
          <h2 className="flex items-center gap-2 text-sm font-semibold"><Wrench size={18} className="text-green" />Maintenance details</h2>
          <label className="block text-sm font-medium">Title
            <input required maxLength={100} value={title} onChange={event => setTitle(event.target.value)} placeholder="For example, database update" className="form-input mt-2 w-full" />
          </label>
          <label className="block text-sm font-medium">Description <span className="font-normal text-muted">(optional)</span>
            <textarea maxLength={3000} rows={4} value={description} onChange={event => setDescription(event.target.value)} placeholder="What will be affected during this work?" className="form-input mt-2 w-full" />
          </label>
          <div className="grid gap-5 sm:grid-cols-2">
            <label className="block min-w-0 text-sm font-medium">Start time
              <input type="datetime-local" required value={startsAt} onChange={event => setStartsAt(event.target.value)} className="form-input mt-2 w-full min-w-0" />
            </label>
            <label className="block min-w-0 text-sm font-medium">End time
              <input type="datetime-local" required value={endsAt} onChange={event => setEndsAt(event.target.value)} className="form-input mt-2 w-full min-w-0" />
            </label>
          </div>
          <p className="text-xs leading-6 text-muted">Times use your browser's local timezone. The backend stores them in UTC. Checks already running are not interrupted.</p>
        </fieldset>
        <fieldset disabled={busy} className="dashboard-panel p-5 sm:p-7">
          <legend className="sr-only">Affected monitors</legend>
          <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-sm font-semibold">Affected monitors</h2><p className="text-xs text-muted">{selected.length} of 30 selected</p></div>
          <p className="mt-2 text-xs leading-6 text-muted">Only monitors from your own account can be selected.</p>
          <div className="mt-4 max-h-80 divide-y divide-line overflow-y-auto rounded-lg border border-line">
            {data.monitors.map(monitor => <label key={monitor.id} className="flex cursor-pointer items-start gap-3 p-4">
              <input type="checkbox" checked={selected.includes(monitor.id)} disabled={!selected.includes(monitor.id) && selected.length >= 30}
                onChange={event => {
                  const checked = event.target.checked
                  setSelected(current => checked ? [...current, monitor.id] : current.filter(id => id !== monitor.id))
                }}
                className="mt-1 shrink-0 accent-green" />
              <span className="min-w-0"><span className="block break-words text-sm font-medium">{monitor.name}</span><span className="mt-1 block break-all text-xs text-muted">{monitor.url}</span></span>
            </label>)}
          </div>
        </fieldset>
        <fieldset disabled={busy} className="dashboard-panel p-5 sm:p-7">
          <label className="flex items-center gap-3 text-sm font-medium"><input type="checkbox" checked={published} onChange={event => setPublished(event.target.checked)} className="accent-green" />Publish a status page notice</label>
          <p className="mt-3 text-xs leading-6 text-muted">Your title, description and times will appear on status pages that include the selected monitors. Leave unchecked to keep the notice private. Do not include passwords or internal details.</p>
        </fieldset>
        <p className="text-xs leading-6 text-muted">Automatic checks are skipped during this window and resume when it ends. Existing incidents stay open until recovery is confirmed. Manually paused monitors stay paused.</p>
        <div className="flex flex-wrap gap-3">
          <button disabled={busy} className="button button-green">{busy ? 'Saving...' : maintenanceId === null ? 'Schedule maintenance' : 'Save changes'}</button>
          <a href="#dashboard/maintenance/" className="dashboard-action">Back to list</a>
        </div>
      </form>}
    </div>
  )
}

function toLocalDateTime(value: string) {
  const date = new Date(value)
  // datetime-local needs local time without the timezone suffix.
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

function toUtc(value: string, saved?: string) {
  // Keep the original time when an existing field has not changed.
  if (saved && value === toLocalDateTime(saved)) return saved
  const date = new Date(value)
  if (Number.isNaN(date.getTime()) || toLocalDateTime(date.toISOString()) !== value) {
    throw new Error('Choose valid start and end times in your local timezone.')
  }
  return date.toISOString()
}
