import { useEffect, useState } from 'react'
import {
  Activity,
  Pause,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  Search,
  Trash2
} from 'lucide-react'
import { monitorApi } from '../services/monitors'
import type { Monitor } from '../types/monitor'
import { teamApi } from '../services/teams'
import type { Workspace } from '../services/teams'
import { MonitorHistoryPanel } from '../components/MonitorHistoryPanel'

export function MonitoringPage({ workspaceId }: { workspaceId: number | null }) {
  const [monitors, setMonitors] = useState<Monitor[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [deleting, setDeleting] = useState<number | null>(null)
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [historyId, setHistoryId] = useState<number | null>(null)
  const [historyRefresh, setHistoryRefresh] = useState(0)
  const workspace = workspaces.find(item => workspaceId === null ? item.role === 'OWNER' : item.id === workspaceId)
  const canEdit = workspace?.role === 'OWNER' || workspace?.role === 'EDITOR'
  const workspaceQuery = workspaceId === null ? '' : `?workspace=${workspaceId}`

  useEffect(() => {
    let active = true
    Promise.all([monitorApi.list(workspaceId), teamApi.list()])
      .then(([list, team]) => {
        if (active) {
          setMonitors(list)
          setWorkspaces(team.workspaces)
        }
      })
      .catch((exception) => {
        if (active) setError(exception.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [workspaceId])

  async function refresh() {
    setLoading(true)
    setError('')
    try {
      const [list, team] = await Promise.all([monitorApi.list(workspaceId), teamApi.list()])
      setMonitors(list)
      setWorkspaces(team.workspaces)
      setHistoryRefresh(value => value + 1)
    } catch (exception) {
      setMonitors([])
      setWorkspaces([])
      setDeleting(null)
      setHistoryId(null)
      setError(message(exception))
    } finally {
      setLoading(false)
    }
  }

  async function change(monitor: Monitor, remove = false) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      if (remove) {
        await monitorApi.remove(monitor.id)
        setMonitors((list) => list.filter((item) => item.id !== monitor.id))
        setDeleting(null)
        setNotice('Monitor, check history and incidents deleted.')
      } else {
        const updated = await monitorApi.togglePause(monitor.id)
        setMonitors((list) =>
          list.map((item) => (item.id === updated.id ? updated : item))
        )
        setNotice(
          updated.status === 'PAUSED'
            ? 'Monitoring paused.'
            : 'Monitoring resumed.'
        )
      }
    } catch (exception) {
      setError(message(exception))
    } finally {
      setBusy(false)
    }
  }

  const count = (status: string) =>
    monitors.filter(
      (m) => m.status === status && (status === 'PAUSED' || m.lastCheckedAt)
    ).length
  const waiting = monitors.filter(
    (m) => !m.lastCheckedAt && m.status !== 'PAUSED'
  ).length
  const matching = monitors.filter(
    (m) =>
      `${m.name} ${m.url}`
        .toLowerCase()
        .includes(search.trim().toLowerCase()) &&
      (filter === 'all' ||
        (filter === 'waiting'
          ? !m.lastCheckedAt && m.status !== 'PAUSED'
          : m.status === filter && (filter === 'PAUSED' || m.lastCheckedAt)))
  )
  const disabled = busy || loading

  return (
    <>
      <header className="mb-8 flex items-center justify-between gap-4">
        <div>
          <p className="eyebrow mb-2">Monitoring</p>
          <h1 className="dashboard-heading">
            Monitors<span className="text-green">.</span>
          </h1>
        </div>
        {canEdit && <a
          href={`#dashboard/monitoring/new/${workspaceQuery}`}
          className="button button-green px-4 py-2.5"
        >
          <Plus size={17} />
          New monitor
        </a>}
      </header>
      {workspaces.length > 0 && (
        <div className="mb-6 flex flex-wrap items-center gap-3">
          <label className="text-xs text-muted">Workspace
            <select value={workspace?.id ?? ''} onChange={event => { window.location.hash = `#dashboard/monitoring/?workspace=${event.target.value}` }}
              className="form-input mt-2 sm:ml-3 sm:mt-0 sm:inline-block sm:w-auto">
              {workspaces.map(item => <option key={item.id} value={item.id}>{item.role === 'OWNER' ? 'My monitors' : item.email}</option>)}
            </select>
          </label>
          {workspace && workspace.role !== 'OWNER' && <span className="rounded-lg bg-sage px-3 py-2 text-xs text-green">{workspace.role === 'EDITOR' ? 'Editor access' : 'Viewer access'}</span>}
        </div>
      )}
      {error && (
        <p role="alert" className="dashboard-error mb-5">
          {error}
          {workspaceId !== null && <a href="#dashboard/monitoring/" className="ml-3 underline">Back to my monitors</a>}
        </p>
      )}
      {notice && (
        <p
          role="status"
          className="mb-5 rounded-lg bg-sage p-4 text-sm text-green"
        >
          {notice}
        </p>
      )}
      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_240px]">
        <section aria-label="Your monitor list" className="min-w-0">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs text-muted">
              {loading
                ? 'Loading...'
                : `${monitors.length} ${monitors.length === 1 ? 'monitor' : 'monitors'}`}
            </p>
            <div className="flex w-full flex-wrap gap-2 sm:w-auto">
              <label className="flex min-w-0 flex-1 basis-full items-center gap-2 rounded-lg border border-line bg-panel px-3 text-muted sm:basis-auto">
                <Search size={15} />
                <span className="sr-only">Search monitors</span>
                <input
                  type="search"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search by name or URL"
                  className="min-w-0 w-full bg-transparent py-2.5 text-xs text-ink"
                />
              </label>
              <label>
                <span className="sr-only">Filter monitors</span>
                <select
                  value={filter}
                  onChange={(e) => setFilter(e.target.value)}
                  className="form-input py-2.5 text-xs"
                >
                  <option value="all">All statuses</option>
                  <option value="UP">Up</option>
                  <option value="DOWN">Down</option>
                  <option value="DEGRADED">Slow response</option>
                  <option value="PAUSED">Paused</option>
                  <option value="waiting">Awaiting check</option>
                </select>
              </label>
              <button
                type="button"
                onClick={refresh}
                disabled={disabled}
                className="dashboard-action"
                aria-label="Refresh monitors"
              >
                <RefreshCw
                  size={15}
                  className={loading ? 'animate-spin' : ''}
                />
              </button>
            </div>
          </div>
          <div className="overflow-hidden rounded-xl border border-line bg-panel">
            {loading && (
              <p role="status" className="p-10 text-center text-sm text-muted">
                Loading monitors...
              </p>
            )}
            {!loading && monitors.length === 0 && !error && (
              <div className="px-6 py-16 text-center">
                <span className="mx-auto mb-5 flex size-14 items-center justify-center rounded-2xl border border-line bg-sage text-green">
                  <Activity size={25} />
                </span>
                <h2 className="font-heading text-lg font-bold">
                  {canEdit ? 'Your first monitor starts here.' : 'No monitors yet.'}
                </h2>
                <p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-muted">
                  {canEdit ? 'Add a website or API endpoint. We will check its availability and keep you informed.'
                    : 'The owner or an editor can add monitors to this workspace.'}
                </p>
                {canEdit && <a
                  href={`#dashboard/monitoring/new/${workspaceQuery}`}
                  className="button button-green mt-6 px-5 py-3"
                >
                  <Plus size={16} />
                  Create monitor
                </a>}
              </div>
            )}
            {!loading && monitors.length > 0 && matching.length === 0 && (
              <p className="p-10 text-center text-sm text-muted">
                No monitors match your search.
              </p>
            )}
            <div className="divide-y divide-line">
              {matching.map((monitor) => {
                const paused = monitor.status === 'PAUSED'
                const label = paused
                  ? 'Paused'
                  : !monitor.lastCheckedAt
                    ? 'Awaiting check'
                    : monitor.status === 'UP'
                      ? 'Up'
                      : monitor.status === 'DOWN'
                        ? 'Down'
                        : 'Slow response'
                const color =
                  paused || !monitor.lastCheckedAt
                    ? 'text-muted'
                    : monitor.status === 'UP'
                      ? 'text-green'
                      : monitor.status === 'DOWN'
                        ? 'text-rose-700'
                        : 'text-amber-800'
                return (
                  <article
                    key={monitor.id}
                    aria-label={monitor.name}
                    className="p-5"
                  >
                    <div className="flex items-start gap-3">
                      <span
                        className={`mt-1.5 size-2.5 shrink-0 rounded-full bg-current ${color}`}
                      />
                      <div className="min-w-0 flex-1">
                        {canEdit ? <a
                          href={`#dashboard/monitoring/${monitor.id}/edit/${workspaceQuery}`}
                          className="break-words text-sm font-semibold hover:text-green"
                        >
                          {monitor.name}
                        </a> : <h2 className="break-words text-sm font-semibold">{monitor.name}</h2>}
                        <p className="mt-1 break-all text-xs text-muted">
                          {monitor.url}
                        </p>
                      </div>
                      <span className={`shrink-0 text-xs ${color}`}>
                        {label}
                      </span>
                    </div>
                    <div className="mt-4 flex flex-wrap items-end justify-between gap-3 pl-5">
                      <div className="text-[11px] leading-5 text-muted">
                        <p>
                          {monitor.httpMethod} · Every {monitor.intervalSeconds}
                          s · Timeout {monitor.timeoutSeconds}s
                        </p>
                        <p>
                          {monitor.lastCheckedAt
                            ? `Last check: ${new Date(monitor.lastCheckedAt).toLocaleString()}`
                            : paused ? 'No checks yet' : 'First check pending'}
                        </p>
                      </div>
                      <div className="flex gap-1.5">
                        <button className="dashboard-action" onClick={() => setHistoryId(historyId === monitor.id ? null : monitor.id)} aria-expanded={historyId === monitor.id}>
                          History<span className="sr-only"> for {monitor.name}</span>
                        </button>
                        {canEdit && <>
                        <a
                          href={`#dashboard/monitoring/${monitor.id}/edit/${workspaceQuery}`}
                          className="dashboard-action"
                          aria-label={`Edit ${monitor.name}`}
                        >
                          <Pencil size={14} />
                        </a>
                        <button
                          disabled={disabled}
                          onClick={() => change(monitor)}
                          className="dashboard-action"
                          aria-label={`${paused ? 'Resume' : 'Pause'} ${monitor.name}`}
                        >
                          {paused ? <Play size={14} /> : <Pause size={14} />}
                        </button>
                        <button
                          disabled={disabled}
                          onClick={() => setDeleting(monitor.id)}
                          className="dashboard-action text-rose-700"
                          aria-label={`Delete ${monitor.name}`}
                        >
                          <Trash2 size={14} />
                        </button>
                        </>}
                      </div>
                    </div>
                    {historyId === monitor.id && <MonitorHistoryPanel monitorId={monitor.id} refresh={historyRefresh} />}
                    {canEdit && deleting === monitor.id && (
                      <div
                        role="group"
                        aria-label={`Delete ${monitor.name}`}
                        className="mt-4 rounded-lg border border-line bg-canvas p-4"
                      >
                        <p className="text-sm">
                          Delete {monitor.name} and its check and incident history? This
                          cannot be undone.
                        </p>
                        <div className="mt-3 flex gap-3">
                          <button
                            disabled={busy}
                            onClick={() => change(monitor, true)}
                            className="dashboard-action text-rose-700"
                          >
                            Delete permanently
                          </button>
                          <button
                            disabled={busy}
                            onClick={() => setDeleting(null)}
                            className="dashboard-action"
                          >
                            Keep monitor
                          </button>
                        </div>
                      </div>
                    )}
                  </article>
                )
              })}
            </div>
          </div>
          <p className="mt-4 text-xs text-muted">
            {workspace?.role === 'OWNER' ? 'Your monitors appear here.' : 'These monitors belong to the selected workspace.'} Refresh to load the latest checks.
          </p>
        </section>
        <aside className="space-y-4">
          <section className="dashboard-panel p-5">
            <h2 className="text-sm font-semibold">
              Current status<span className="text-green">.</span>
            </h2>
            <dl className="mt-6 grid grid-cols-3 gap-y-5 text-center">
              {[
                { label: 'Down', value: count('DOWN'), color: 'text-rose-700' },
                { label: 'Up', value: count('UP'), color: 'text-green' },
                { label: 'Paused', value: count('PAUSED'), color: 'text-muted' }
              ].map(({ label, value, color }) => (
                <div key={label}>
                  <dd className={`font-heading text-2xl font-bold ${color}`}>
                    {loading ? '-' : value}
                  </dd>
                  <dt className="mt-1 text-[11px] text-muted">{label}</dt>
                </div>
              ))}
            </dl>
            <div className="mt-5 space-y-2 border-t border-line pt-4 text-xs text-muted">
              <p className="flex justify-between">
                <span>Slow response</span>
                <span>{loading ? '-' : count('DEGRADED')}</span>
              </p>
              <p className="flex justify-between">
                <span>Awaiting check</span>
                <span>{loading ? '-' : waiting}</span>
              </p>
            </div>
          </section>
          <section className="dashboard-panel p-5">
            <h2 className="text-sm font-semibold">Stay in the loop.</h2>
            <p className="mt-3 text-xs leading-6 text-muted">
              {workspace?.role === 'OWNER'
                ? 'Send outage and recovery alerts straight to your Discord channel.'
                : 'The workspace owner manages Discord integrations. Editors can select them when editing a monitor.'}
            </p>
            {workspace?.role === 'OWNER' && <a
              href="#dashboard/integrations/"
              className="mt-4 inline-block text-xs font-semibold text-green hover:underline"
            >
              Manage integrations
            </a>}
          </section>
        </aside>
      </div>
    </>
  )
}

function message(exception: unknown) {
  return exception instanceof Error
    ? exception.message
    : 'Something went wrong. Please try again.'
}
