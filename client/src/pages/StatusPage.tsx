import { useEffect, useState } from 'react'
import { Activity, CircleAlert, RefreshCw, Server, Wrench } from 'lucide-react'
import { api } from '../services/api'
import { statusPageApi } from '../services/statusPages'
import type { StatusPageData } from '../services/statusPages'
import type { MonitorWithDetails } from '../types/monitor'
import { MonitorHistoryPanel } from '../components/MonitorHistoryPanel'
import { stageLabels } from '../services/incidents'
import type { PublicIncident } from '../services/incidents'
import { maintenanceLabels } from '../services/maintenance'
import type { PublicMaintenance } from '../services/maintenance'

export function StatusPage({
  slug,
  page
}: {
  slug?: string
  page?: StatusPageData
}) {
  const [monitors, setMonitors] = useState<MonitorWithDetails[]>([])
  const [incidents, setIncidents] = useState<PublicIncident[]>([])
  const [maintenance, setMaintenance] = useState<PublicMaintenance[]>([])
  const [title, setTitle] = useState(page?.name ?? 'Live status')
  const [description, setDescription] = useState(page?.description ?? '')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)
  const [refresh, setRefresh] = useState(0)
  const [historyRefresh, setHistoryRefresh] = useState(0)

  useEffect(() => {
    let disposed = false
    let pending = false
    async function loadData() {
      if (pending) return
      pending = true
      try {
        const pageSlug = slug ?? page?.slug
        const current = pageSlug
          ? await statusPageApi.publicPage(pageSlug)
          : await statusPageApi.defaultPage()
        const list = current ? current.monitors : await api.getMonitors()
        const detailed = await Promise.all(
          list.map(async (monitor) => {
            const pings = await api
              .getRecentPings(monitor.id, 1)
              .catch(() => [])
            return { ...monitor, pings }
          })
        )
        if (!disposed) {
          setTitle(current?.name ?? 'Live status')
          setDescription(current?.description ?? '')
          setMonitors(detailed)
          setIncidents(current?.incidents ?? [])
          setMaintenance(current?.maintenance ?? [])
          setUpdatedAt(new Date())
          setError('')
          setHistoryRefresh((value) => value + 1)
        }
      } catch (exception) {
        if (!disposed)
          setError(
            exception instanceof Error
              ? exception.message
              : 'Status is currently unavailable.'
          )
      } finally {
        pending = false
        if (!disposed) setLoading(false)
      }
    }
    void loadData()
    const interval = window.setInterval(() => void loadData(), 30000)
    return () => {
      disposed = true
      window.clearInterval(interval)
    }
  }, [page, slug, refresh])

  const maintainedIds = new Set(maintenance
    .filter(item => item.status === 'IN_PROGRESS')
    .flatMap(item => item.monitorIds))
  const hasMaintenance = maintainedIds.size > 0
  // A saved check result is not the current status while checks are skipped.
  const active = monitors.filter(monitor => monitor.status !== 'PAUSED' && !maintainedIds.has(monitor.id))
  const hasChecks =
    active.length > 0 && active.every((monitor) => monitor.lastCheckedAt)
  const hasFailure = active.some(
    (monitor) => monitor.lastCheckedAt && monitor.status === 'DOWN'
  )
  const hasSlow = active.some(
    (monitor) => monitor.lastCheckedAt && monitor.status === 'DEGRADED'
  )
  const hasIncident = incidents.some(incident => !incident.resolvedAt)
  const allOperational = hasChecks && !hasFailure && !hasSlow && !hasIncident && !hasMaintenance
  const heading = error
    ? 'Status is currently unavailable'
    : monitors.length === 0
      ? 'No monitors to show yet'
      : hasFailure || hasIncident
        ? 'Some services need attention'
        : hasMaintenance
          ? 'Maintenance in progress'
          : hasSlow
            ? 'Some services are responding slowly'
            : active.length === 0
              ? 'All monitors are paused'
              : !hasChecks
                ? 'Waiting for service checks'
                : 'All systems operational'

  return (
    <main id="main" tabIndex={-1} className="page-shell flex-1 py-12 sm:py-18">
      <div className="mx-auto max-w-4xl">
        <div className="mb-9 flex flex-wrap items-end justify-between gap-5">
          <div>
            <p className="eyebrow mb-3">Service status</p>
            <h1 className="section-heading">
              {title}
              <span className="text-green">.</span>
            </h1>
            {description && (
              <p className="mt-4 max-w-2xl whitespace-pre-wrap text-sm leading-6 text-muted">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            disabled={loading}
            onClick={() => {
              setLoading(true)
              setRefresh((value) => value + 1)
            }}
            className="button button-outline disabled:opacity-50"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
            {loading ? 'Refreshing' : 'Refresh status'}
          </button>
        </div>
        <div
          role="status"
          className={`mb-8 flex items-center gap-4 rounded-xl border p-5 ${error ? 'border-amber-200 bg-amber-50' : allOperational ? 'border-line bg-sage' : 'border-line bg-white'}`}
        >
          {error || hasFailure || hasSlow || hasIncident ? (
            <CircleAlert className="shrink-0 text-amber-700" size={23} />
          ) : hasMaintenance ? (
            <Wrench className="shrink-0 text-amber-700" size={23} />
          ) : (
            <Activity className="shrink-0 text-green" size={23} />
          )}
          <div>
            <h2 className="font-heading text-lg font-bold">
              {loading ? 'Checking your services' : heading}
            </h2>
            <p className="mt-1 text-xs leading-5 text-muted">
              {loading
                ? 'Fetching the latest uptime and response times.'
                : error ||
                  (updatedAt
                    ? `Last refreshed at ${updatedAt.toLocaleTimeString()}. Updates every 30 seconds.`
                    : 'Updates every 30 seconds.')}
            </p>
          </div>
        </div>
        {!loading && !error && monitors.length === 0 && (
          <div className="rounded-xl border border-dashed border-line px-6 py-16 text-center">
            <Server
              size={32}
              strokeWidth={1.5}
              className="mx-auto mb-4 text-green"
            />
            <h2 className="font-heading text-xl font-bold">
              No services added yet.
            </h2>
            <p className="mx-auto mt-3 max-w-sm text-sm leading-6 text-muted">
              Selected monitors will appear here with their availability and
              check history.
            </p>
          </div>
        )}
        {error && monitors.length > 0 && (
          <p className="mb-4 text-xs text-amber-800">
            Showing the last available results. These may be out of date.
          </p>
        )}
        {maintenance.length > 0 && <section aria-label="Published maintenance" className="mb-8">
          <h2 className="mb-4 font-heading text-xl font-bold">Maintenance</h2>
          <div className="space-y-4">
            {maintenance.map(item => <article key={item.id} aria-label={item.title} className="rounded-xl border border-line bg-white p-5 sm:p-6">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h3 className="min-w-0 flex-1 break-words text-sm font-semibold">{item.title}</h3>
                <span className={`rounded-md px-2 py-1 text-xs font-medium ${item.status === 'IN_PROGRESS'
                  ? 'bg-amber-50 text-amber-800' : item.status === 'SCHEDULED' ? 'bg-sage text-green' : 'bg-canvas text-muted'}`}>
                  {maintenanceLabels[item.status]}</span>
              </div>
              <p className="mt-3 text-xs leading-6 text-muted">
                <time dateTime={item.startsAt}>{new Date(item.startsAt).toLocaleString()}</time>{' to '}
                <time dateTime={item.endsAt}>{new Date(item.endsAt).toLocaleString()}</time>
              </p>
              {item.description && <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-muted">{item.description}</p>}
              <p className="mt-3 break-words text-xs leading-6 text-muted">Affected services: {item.monitorIds
                .map(id => monitors.find(monitor => monitor.id === id)?.name || `Monitor #${id}`).join(', ')}</p>
              {item.status === 'IN_PROGRESS' && <p className="mt-2 text-xs leading-6 text-amber-800">Automatic checks are skipped during this window. History below shows the recorded checks.</p>}
            </article>)}
          </div>
          <p className="mt-3 text-xs text-muted">Published notices only, up to 40. Times use your local timezone.</p>
        </section>}
        {incidents.length > 0 && <section aria-label="Published incidents" className="mb-8">
          <h2 className="mb-4 font-heading text-xl font-bold">Incidents</h2>
          <div className="space-y-4">
            {incidents.map(incident => <details key={incident.id} open={!incident.resolvedAt} className="rounded-xl border border-line bg-white p-5 sm:p-6">
              <summary className="cursor-pointer break-words text-sm font-semibold">
                {incident.title}
                <span className={`ml-3 inline-block rounded-md px-2 py-1 text-xs font-medium ${incident.resolvedAt ? 'bg-sage text-green' : 'bg-amber-50 text-amber-800'}`}>{stageLabels[incident.stage]}</span>
              </summary>
              <p className="mt-3 text-xs text-muted">Started {new Date(incident.startedAt).toLocaleString()}
                {incident.resolvedAt && ` | Resolved ${new Date(incident.resolvedAt).toLocaleString()}`}
              </p>
              <ol className="mt-5 space-y-5 border-l border-line pl-4">
                {incident.updates.map((update, index) => <li key={index}>
                  <h3 className="text-xs font-semibold">{stageLabels[update.stage]}</h3>
                  <time dateTime={update.createdAt} className="mt-1 block text-xs text-muted">{new Date(update.createdAt).toLocaleString()}</time>
                  <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted">{update.message}</p>
                </li>)}
              </ol>
            </details>)}
          </div>
          <p className="mt-3 text-xs text-muted">Published incidents only, up to 40. Times use your local timezone.</p>
        </section>}
        <div className="space-y-6">
          {monitors.map((monitor) => {
            const latest = monitor.pings?.[0]
            const paused = monitor.status === 'PAUSED'
            const underMaintenance = maintainedIds.has(monitor.id)
            let label = 'Awaiting check'
            let color = 'text-muted'
            if (paused) {
              label = 'Paused'
            } else if (underMaintenance) {
              label = 'Under maintenance'
              color = 'text-amber-800'
            } else if (monitor.lastCheckedAt) {
              if (monitor.status === 'UP') {
                label = 'Operational'
                color = 'text-green'
              } else if (monitor.status === 'DEGRADED') {
                label = 'Slow response'
                color = 'text-amber-800'
              } else {
                label = 'Not responding'
                color = 'text-rose-700'
              }
            }
            return (
              <article
                key={monitor.id}
                aria-label={monitor.name}
                className="rounded-xl border border-line bg-white p-5 sm:p-6"
              >
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <h2 className="break-words font-heading font-bold">
                      {monitor.name}
                    </h2>
                    <p className="mt-1 break-all text-xs text-muted">
                      {monitor.url}
                    </p>
                  </div>
                  <span
                    className={`flex items-center gap-2 text-xs font-medium ${color}`}
                  >
                    <span className="size-1.5 rounded-full bg-current" />
                    {label}
                  </span>
                </div>
                <div className="mt-4 flex flex-wrap justify-between gap-3 text-xs text-muted">
                  <p>
                    {underMaintenance ? 'Last recorded response: ' : 'Last response: '}
                    {latest?.successful
                      ? `${latest.responseTimeMs} ms`
                      : latest
                        ? 'Check failed'
                        : 'Not available'}
                  </p>
                  <p>
                    {monitor.lastCheckedAt
                      ? `Checked ${new Date(monitor.lastCheckedAt).toLocaleString()}`
                      : 'First check pending'}
                  </p>
                </div>
                <MonitorHistoryPanel
                  monitorId={monitor.id}
                  refresh={historyRefresh}
                />
              </article>
            )
          })}
        </div>
        <p className="mt-8 text-center text-xs text-muted">
          Powered by StatusRobot
        </p>
      </div>
    </main>
  )
}
