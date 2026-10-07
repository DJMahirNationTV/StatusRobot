import { useEffect, useState } from 'react'
import { RefreshCw, ShieldAlert } from 'lucide-react'
import { incidentApi } from '../services/incidents'
import type { IncidentFilter, IncidentListing } from '../services/incidents'

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
                      <h2 className="break-words text-sm font-semibold">{incident.monitorName}</h2>
                      <p className="mt-1 text-xs text-muted">{incident.cause}</p>
                    </div>
                    <span className={`shrink-0 rounded-md px-2 py-1 text-xs font-medium ${incident.resolvedAt ? 'bg-sage text-green' : 'bg-rose-50 text-rose-700'}`}>
                      {incident.resolvedAt ? 'Resolved' : 'Open'}
                    </span>
                  </div>
                  <dl className="mt-4 grid gap-3 pl-5 text-xs sm:grid-cols-2">
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
