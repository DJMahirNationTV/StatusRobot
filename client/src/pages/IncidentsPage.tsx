import { useEffect, useState } from 'react'
import { ChevronLeft, RefreshCw, ShieldAlert } from 'lucide-react'
import { incidentApi } from '../services/incidents'
import type { Incident, IncidentFilter, IncidentListing } from '../services/incidents'

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
        <button type="button" onClick={refresh} disabled={loading} className="dashboard-action">
          <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
          Refresh
        </button>
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
                        <a href={`#dashboard/incidents/${incident.id}/`} className="hover:text-green">{incident.monitorName}</a>
                      </h2>
                      <p className="mt-1 text-xs text-muted">{incident.cause}</p>
                    </div>
                    <span className={`shrink-0 rounded-md px-2 py-1 text-xs font-medium ${incident.resolvedAt ? 'bg-sage text-green' : 'bg-rose-50 text-rose-700'}`}>
                      {incident.resolvedAt ? 'Resolved' : 'Open'}
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
                        : 'Waiting for a successful check'}</dd>
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

  useEffect(() => {
    let active = true
    incidentApi.get(incidentId)
      .then(result => { if (active) setIncident(result) })
      .catch(exception => { if (active) setError(exception.message) })
    return () => { active = false }
  }, [incidentId, reload])

  function refresh() {
    setIncident(null)
    setError('')
    setReload(value => value + 1)
  }

  const loading = incident === null && !error

  return (
    <div className="max-w-4xl">
      <a href="#dashboard/incidents/" className="dashboard-action mb-6"><ChevronLeft size={14} />Incidents</a>
      <header className="mb-8 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="eyebrow mb-2">Incident #{incidentId}</p>
          <h1 className="dashboard-heading break-words">{incident?.monitorName || 'Incident details'}<span className="text-green">.</span></h1>
        </div>
        <button type="button" onClick={refresh} disabled={loading} className="dashboard-action shrink-0">
          <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />Refresh
        </button>
      </header>
      {error && <p role="alert" className="dashboard-error mb-5">{error}</p>}
      {loading && <p role="status" className="dashboard-panel p-10 text-center text-sm text-muted">Loading incident...</p>}
      {incident && (
        <>
          <section aria-label="Incident summary" className="dashboard-panel p-6">
            <div className="flex items-center justify-between gap-4">
              <h2 className="text-sm font-semibold">{incident.resolvedAt ? 'Monitor recovered' : 'Monitor unavailable'}</h2>
              <span className={`rounded-md px-2 py-1 text-xs font-medium ${incident.resolvedAt ? 'bg-sage text-green' : 'bg-rose-50 text-rose-700'}`}>
                {incident.resolvedAt ? 'Resolved' : 'Open'}
              </span>
            </div>
            <dl className="mt-6 grid gap-5 text-sm sm:grid-cols-2">
              <div><dt className="text-xs text-muted">First failed check</dt><dd className="mt-2">{incident.cause}</dd></div>
              <div><dt className="text-xs text-muted">Outage duration</dt><dd className="mt-2">{duration(incident)}</dd></div>
            </dl>
            <a href={`#dashboard/monitoring/${incident.monitorId}/edit/`} className="mt-6 inline-block text-xs font-semibold text-green hover:underline">View monitor</a>
          </section>
          <section aria-label="Incident timeline" className="dashboard-panel mt-6 p-6">
            <h2 className="text-sm font-semibold">Timeline</h2>
            <ol className="mt-6 space-y-6">
              <li className="flex gap-3">
                <span className="mt-1.5 size-2.5 shrink-0 rounded-full bg-rose-600" />
                <div><h3 className="text-sm font-medium">Outage detected</h3>
                  <time dateTime={incident.startedAt} className="mt-1 block text-xs text-muted">{new Date(incident.startedAt).toLocaleString()}</time>
                  <p className="mt-2 text-xs text-muted">{incident.cause}</p>
                </div>
              </li>
              <li className="flex gap-3">
                <span className={`mt-1.5 size-2.5 shrink-0 rounded-full ${incident.resolvedAt ? 'bg-green' : 'bg-line'}`} />
                <div><h3 className="text-sm font-medium">{incident.resolvedAt ? 'Recovery confirmed' : 'Waiting for recovery'}</h3>
                  {incident.resolvedAt
                    ? <time dateTime={incident.resolvedAt} className="mt-1 block text-xs text-muted">{new Date(incident.resolvedAt).toLocaleString()}</time>
                    : <p className="mt-1 text-xs text-muted">A successful check will close this incident.</p>}
                </div>
              </li>
            </ol>
          </section>
          <p className="mt-4 text-xs text-muted">Times use your local timezone. Duration is based on when checks detected the outage and recovery.</p>
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
