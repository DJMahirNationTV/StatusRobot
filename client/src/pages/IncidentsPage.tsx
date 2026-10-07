import { useEffect, useState } from 'react'
import { ChevronLeft, Plus, RefreshCw, ShieldAlert } from 'lucide-react'
import { incidentApi, stageLabels } from '../services/incidents'
import type { Incident, IncidentFilter, IncidentListing, IncidentStage } from '../services/incidents'
import { IncidentHelp } from '../components/IncidentHelp'

export function IncidentsPage() {
  const [result, setResult] = useState<IncidentListing | null>(null)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState<IncidentFilter>('all')
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    incidentApi.list(filter, page)
      .then(list => { if (active) setResult(list) })
      .catch(exception => { if (active) setError(exception.message) })
    return () => { active = false }
  }, [filter, page, reload])

  function refresh() {
    setResult(null)
    setError('')
    setReload(value => value + 1)
  }

  function changePage(next: number) {
    setResult(null)
    setError('')
    setPage(next)
  }

  const loading = result === null && !error

  return (
    <>
      <header className="mb-8 flex items-center justify-between gap-4">
        <div>
          <p className="eyebrow mb-2">Monitoring history</p>
          <h1 className="dashboard-heading">Incidents<span className="text-green">.</span></h1>
        </div>
        <div className="flex flex-wrap justify-end gap-2"><a href="#dashboard/incidents/new/" className="button button-green"><Plus size={15} />New incident</a>
        <button type="button" onClick={refresh} disabled={loading} className="dashboard-action">
          <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
          Refresh
        </button></div>
      </header>
      {error && <p role="alert" className="dashboard-error mb-5">{error}</p>}
      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_240px]">
        <section aria-label="Your incident history" className="min-w-0">
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="text-xs text-muted">
              {loading ? 'Loading...' : result ? `${result.totalElements} ${result.totalElements === 1 ? 'incident' : 'incidents'}` : 'History unavailable'}
            </p>
            <label>
              <span className="sr-only">Filter incidents</span>
              <select value={filter} className="form-input py-2.5 text-xs" onChange={event => {
                setResult(null)
                setError('')
                setPage(0)
                setFilter(event.target.value as IncidentFilter)
              }}>
                <option value="all">All incidents</option>
                <option value="open">Open</option>
                <option value="resolved">Resolved</option>
              </select>
            </label>
          </div>
          <div className="dashboard-panel overflow-hidden">
            {loading && <p role="status" className="p-10 text-center text-sm text-muted">Loading incidents...</p>}
            {result && result.incidents.length === 0 && (
              <div className="px-6 py-16 text-center">
                <span className="mx-auto mb-5 flex size-14 items-center justify-center rounded-2xl border border-line bg-sage text-green">
                  <ShieldAlert size={25} />
                </span>
                <h2 className="font-heading text-lg font-bold">
                  {filter === 'all' ? 'No incidents yet.' : `No ${filter} incidents.`}
                </h2>
                <p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-muted">
                  {filter === 'all' ? 'A failed monitor check will appear here. Your history starts with the next outage.'
                    : 'Choose all incidents to see the rest of your history.'}
                </p>
              </div>
            )}
            <div className="divide-y divide-line">
              {result?.incidents.map(incident => (
                <article key={incident.id} aria-label={`Incident for ${incident.monitorName}`} className="p-5">
                  <div className="flex items-start gap-3">
                    <span className={`mt-1.5 size-2.5 shrink-0 rounded-full ${incident.resolvedAt ? 'bg-green' : 'bg-rose-600'}`} />
                    <div className="min-w-0 flex-1">
                      <h2 className="break-words text-sm font-semibold">
                        <a href={`#dashboard/incidents/${incident.id}/`} className="hover:text-green">{incident.title}</a>
                      </h2>
                      <p className="mt-1 text-xs text-muted">{incident.monitorName} | {incident.manual ? 'Manual incident' : incident.cause}</p>
                    </div>
                    <span className={`shrink-0 rounded-md px-2 py-1 text-xs font-medium ${incident.resolvedAt ? 'bg-sage text-green' : 'bg-rose-50 text-rose-700'}`}>
                      {stageLabels[incident.stage]}
                    </span>
                  </div>
                  <dl className="mt-4 grid gap-3 pl-5 text-xs sm:grid-cols-3">
                    <div>
                      <dt className="text-muted">Started</dt>
                      <dd className="mt-1"><time dateTime={incident.startedAt}>{new Date(incident.startedAt).toLocaleString()}</time></dd>
                    </div>
                    <div>
                      <dt className="text-muted">Recovered</dt>
                      <dd className="mt-1">{incident.resolvedAt
                        ? <time dateTime={incident.resolvedAt}>{new Date(incident.resolvedAt).toLocaleString()}</time>
                        : incident.manual ? 'Waiting for an update' : 'Waiting for a successful check'}</dd>
                    </div>
                    <div>
                      <dt className="text-muted">Duration</dt>
                      <dd className="mt-1">{duration(incident)}</dd>
                    </div>
                  </dl>
                </article>
              ))}
            </div>
            {error && <div className="p-10 text-center"><button type="button" onClick={refresh} className="dashboard-action">Try again</button></div>}
          </div>
          {result && result.totalPages > 1 && (
            <nav aria-label="Incident pages" className="mt-4 flex items-center justify-between gap-3">
              <button type="button" disabled={page === 0} onClick={() => changePage(page - 1)} className="dashboard-action">Previous</button>
              <p className="text-xs text-muted">Page {page + 1} of {result.totalPages}</p>
              <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => changePage(page + 1)} className="dashboard-action">Next</button>
            </nav>
          )}
          <p className="mt-4 text-xs text-muted">Only your incidents appear here. Times use your local timezone.</p>
        </section>
        <aside className="dashboard-panel p-5">
          <h2 className="text-sm font-semibold">How incidents work.</h2>
          <p className="mt-3 text-xs leading-6 text-muted">A failed check opens an incident. Further failures stay in the same incident until the monitor responds successfully.</p>
          <p className="mt-3 text-xs leading-6 text-muted">Pausing does not confirm recovery. Deleting a monitor also deletes its incident history.</p>
          <p className="mt-3 text-xs leading-6 text-muted">Manual incidents stay open until you resolve them. Publish an incident to share its updates on your status pages.</p>
          <a href="#dashboard/monitoring/" className="mt-4 inline-block text-xs font-semibold text-green hover:underline">Manage monitors</a>
        </aside>
      </div>
    </>
  )
}

export function IncidentDetailsPage({ incidentId }: { incidentId: number }) {
  const [incident, setIncident] = useState<Incident | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [title, setTitle] = useState('')
  const [published, setPublished] = useState(false)
  const [stage, setStage] = useState<IncidentStage>('INVESTIGATING')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')

  function accept(result: Incident) {
    setIncident(result)
    setTitle(result.title)
    setPublished(result.published)
    setStage(result.stage)
  }

  useEffect(() => {
    let active = true
    incidentApi.get(incidentId)
      .then(result => { if (active) accept(result) })
      .catch(exception => { if (active) setError(exception.message) })
    return () => { active = false }
  }, [incidentId, reload])

  function refresh() {
    setIncident(null)
    setError('')
    setReload(value => value + 1)
  }

  const loading = incident === null && !error

  async function save(kind: 'update' | 'publication') {
    if (!incident || busy) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const result = kind === 'update'
        ? await incidentApi.update(incident.id, stage, message.trim())
        : await incidentApi.publish(incident.id, title.trim(), published)
      accept(result)
      if (kind === 'update') setMessage('')
      setNotice(kind === 'update' ? 'Update saved.' : 'Publication settings saved.')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Could not save this change.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="max-w-4xl">
      <a href="#dashboard/incidents/" className="dashboard-action mb-6"><ChevronLeft size={14} />Incidents</a>
      <header className="mb-8 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="eyebrow mb-2">Incident #{incidentId}</p>
          <h1 className="dashboard-heading break-words">{incident?.title || 'Incident details'}<span className="text-green">.</span></h1>
        </div>
        <button type="button" onClick={refresh} disabled={loading || busy} className="dashboard-action shrink-0">
          <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />Refresh
        </button>
      </header>
      {error && <p role="alert" className="dashboard-error mb-5">{error}</p>}
      {notice && <p role="status" className="mb-5 text-sm text-green">{notice}</p>}
      {loading && <p role="status" className="dashboard-panel p-10 text-center text-sm text-muted">Loading incident...</p>}
      {incident && (
        <>
          <section aria-label="Incident summary" className="dashboard-panel p-6">
            <div className="flex items-center justify-between gap-4">
              <h2 className="text-sm font-semibold">{incident.monitorName} | {incident.manual ? 'Manual incident' : 'Automatic outage'}</h2>
              <span className={`rounded-md px-2 py-1 text-xs font-medium ${incident.resolvedAt ? 'bg-sage text-green' : 'bg-rose-50 text-rose-700'}`}>
                {stageLabels[incident.stage]}
              </span>
            </div>
            <dl className="mt-6 grid gap-5 text-sm sm:grid-cols-2">
              <div><dt className="text-xs text-muted">Source</dt><dd className="mt-2">{incident.cause}</dd></div>
              <div><dt className="text-xs text-muted">Outage duration</dt><dd className="mt-2">{duration(incident)}</dd></div>
            </dl>
            <a href={`#dashboard/monitoring/${incident.monitorId}/edit/`} className="mt-6 inline-block text-xs font-semibold text-green hover:underline">View monitor</a>
          </section>
          <section aria-label="Incident timeline" className="dashboard-panel mt-6 p-6">
            <h2 className="text-sm font-semibold">Timeline</h2>
            <ol className="mt-6 space-y-6">
              <li className="flex gap-3">
                <span className="mt-1.5 size-2.5 shrink-0 rounded-full bg-rose-600" />
                <div><h3 className="text-sm font-medium">{incident.manual ? 'Incident created' : 'Outage detected'}</h3>
                  <time dateTime={incident.startedAt} className="mt-1 block text-xs text-muted">{new Date(incident.startedAt).toLocaleString()}</time>
                  <p className="mt-2 text-xs text-muted">{incident.cause}</p>
                </div>
              </li>
              {incident.updates.map((update, index) => <li key={index} className="flex gap-3">
                <span className="mt-1.5 size-2.5 shrink-0 rounded-full bg-green" />
                <div className="min-w-0"><h3 className="text-sm font-medium">{stageLabels[update.stage]}</h3>
                  <time dateTime={update.createdAt} className="mt-1 block text-xs text-muted">{new Date(update.createdAt).toLocaleString()}</time>
                  <p className="mt-2 whitespace-pre-wrap break-words text-sm text-muted">{update.message}</p>
                </div>
              </li>)}
              <li className="flex gap-3">
                <span className={`mt-1.5 size-2.5 shrink-0 rounded-full ${incident.resolvedAt ? 'bg-green' : 'bg-line'}`} />
                <div><h3 className="text-sm font-medium">{incident.resolvedAt ? 'Incident resolved' : 'Waiting for recovery'}</h3>
                  {incident.resolvedAt
                    ? <time dateTime={incident.resolvedAt} className="mt-1 block text-xs text-muted">{new Date(incident.resolvedAt).toLocaleString()}</time>
                    : <p className="mt-1 text-xs text-muted">{incident.manual ? 'Add a resolved update when the service has recovered.' : 'A successful check will close this incident.'}</p>}
                </div>
              </li>
            </ol>
          </section>
          <form onSubmit={event => { event.preventDefault(); void save('update') }} className="dashboard-panel mt-6 space-y-5 p-6">
            <h2 className="text-sm font-semibold">Add an update</h2>
            <fieldset disabled={busy} className="space-y-4">
              <label className="block text-xs font-medium">Progress
                <select value={stage} onChange={event => setStage(event.target.value as IncidentStage)} className="form-input mt-2 w-full">
                  {(Object.keys(stageLabels) as IncidentStage[]).filter(value => incident.resolvedAt ? value === 'RESOLVED' : incident.manual || value !== 'RESOLVED')
                    .map(value => <option key={value} value={value}>{stageLabels[value]}</option>)}
                </select>
              </label>
              <label className="block text-xs font-medium">Message
                <textarea required maxLength={3000} rows={4} value={message} onChange={event => setMessage(event.target.value)} className="form-input mt-2 w-full" />
              </label>
            </fieldset>
            <p className="text-xs leading-6 text-muted">All updates become public when this incident is published. Do not include passwords, webhook URLs or internal details.</p>
            <button disabled={busy || !message.trim() || incident.updates.length >= 100} className="button button-green">{busy ? 'Saving...' : 'Save update'}</button>
          </form>
          <form onSubmit={event => { event.preventDefault(); void save('publication') }} className="dashboard-panel mt-6 space-y-5 p-6">
            <h2 className="text-sm font-semibold">Status page publication</h2>
            <fieldset disabled={busy} className="space-y-4">
              <label className="block text-xs font-medium">Public title
                <input required maxLength={100} value={title} onChange={event => setTitle(event.target.value)} className="form-input mt-2 w-full" />
              </label>
              <label className="flex items-center gap-3 text-sm"><input type="checkbox" checked={published} onChange={event => setPublished(event.target.checked)} className="accent-green" />Publish this incident</label>
            </fieldset>
            <p className="text-xs leading-6 text-muted">The title, progress, timestamps and all updates will appear on every status page in your account that includes this monitor. Uncheck to hide them. The automatic failure reason stays private.</p>
            <button disabled={busy || !title.trim()} className="dashboard-action">Save publication settings</button>
          </form>
          <p className="mt-4 text-xs text-muted">Times use your local timezone. Duration is based on when checks detected the outage and recovery.</p>
          <IncidentHelp incidentId={incident.id} onUseDraft={text => {
            if (message.trim() && !window.confirm('Replace your current update draft with these suggestions?')) return
            setMessage(text)
          }} />
        </>
      )}
      {error && <button type="button" onClick={refresh} className="dashboard-action">Try again</button>}
    </div>
  )
}

function duration(incident: Incident) {
  if (!incident.resolvedAt) return 'Ongoing'
  const seconds = Math.max(0, Math.floor((Date.parse(incident.resolvedAt) - Date.parse(incident.startedAt)) / 1000))
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ${Math.floor(seconds % 3600 / 60)}m`
  return `${Math.floor(seconds / 86400)}d ${Math.floor(seconds % 86400 / 3600)}h`
}
