import { useEffect, useState } from 'react'
import { Activity, Pause, Pencil, Play, Plus, RefreshCw, Search, Trash2 } from 'lucide-react'
import { MonitorForm } from '../components/MonitorForm'
import { monitorApi } from '../services/monitors'
import type { AuthUser } from '../services/auth'
import type { Monitor, MonitorInput } from '../types/monitor'

interface DashboardPageProps {
  user: AuthUser | null
  loading: boolean
  error: string
}

export function DashboardPage({ user, loading, error }: DashboardPageProps) {
  if (loading || !user) {
    return <main id="main" tabIndex={-1} className="page-shell flex-1 py-16">
      <h1 className="section-heading">{loading ? 'Checking your session.' : 'Your monitors, in one place.'}</h1>
      <p role={error ? 'alert' : 'status'} className="mt-4 text-muted">{error || (loading ? 'Just a moment.' : 'Sign in to create and manage your monitors.')}</p>
      {!loading && <a href="#login" className="button button-green mt-6">Sign in</a>}
    </main>
  }
  return <MonitorDashboard key={user.id} />
}

function MonitorDashboard() {
  const [monitors, setMonitors] = useState<Monitor[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [search, setSearch] = useState('')
  const [editor, setEditor] = useState<{ monitor: Monitor | null } | null>(null)
  const [deleting, setDeleting] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    monitorApi.list()
      .then(list => { if (active) setMonitors(list) })
      .catch(exception => { if (active) setError(exception.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  async function refresh() {
    setLoading(true)
    setError('')
    try { setMonitors(await monitorApi.list()) }
    catch (exception) { setError(message(exception)) }
    finally { setLoading(false) }
  }

  function openEditor(monitor: Monitor | null) {
    setEditor({ monitor })
    setDeleting(null)
    setError('')
    setNotice('')
  }

  async function save(input: MonitorInput) {
    if (!editor || busy) return
    setBusy(true)
    setError('')
    try {
      const saved = editor.monitor
        ? await monitorApi.update(editor.monitor.id, input)
        : await monitorApi.create(input)
      setMonitors(list => editor.monitor ? list.map(item => item.id === saved.id ? saved : item) : [saved, ...list])
      setNotice(editor.monitor ? 'Monitor updated.' : 'Monitor created. Its first check will run shortly.')
      setSearch('')
      setEditor(null)
    } catch (exception) { setError(message(exception)) }
    finally { setBusy(false) }
  }

  async function togglePause(monitor: Monitor) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const updated = await monitorApi.togglePause(monitor.id)
      setMonitors(list => list.map(item => item.id === updated.id ? updated : item))
      setNotice(updated.status === 'PAUSED' ? 'Monitoring paused.' : 'Monitoring resumed.')
    } catch (exception) { setError(message(exception)) }
    finally { setBusy(false) }
  }

  async function remove(monitor: Monitor) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await monitorApi.remove(monitor.id)
      setMonitors(list => list.filter(item => item.id !== monitor.id))
      setDeleting(null)
      setNotice('Monitor and check history deleted.')
    } catch (exception) { setError(message(exception)) }
    finally { setBusy(false) }
  }

  const matching = monitors.filter(monitor => `${monitor.name} ${monitor.url}`.toLowerCase().includes(search.trim().toLowerCase()))
  const paused = monitors.filter(monitor => monitor.status === 'PAUSED').length
  const issues = monitors.filter(monitor => monitor.lastCheckedAt && (monitor.status === 'DOWN' || monitor.status === 'DEGRADED')).length
  const disabled = busy || loading || editor !== null

  return (
    <main id="main" tabIndex={-1} className="page-shell flex-1 py-10 sm:py-14">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-5">
        <div><p className="eyebrow mb-3">Dashboard</p><h1 className="section-heading">Your monitors.</h1><p className="mt-3 text-sm text-muted">Keep an eye on your services. Make changes when you need to.</p></div>
        <button type="button" disabled={disabled} onClick={() => openEditor(null)} className="button button-green disabled:opacity-50"><Plus size={17} />Add monitor</button>
      </div>

      <dl className="mb-8 grid grid-cols-3 divide-x divide-line rounded-xl border border-line bg-white py-5">
        {[['Total monitors', monitors.length], ['Need attention', issues], ['Paused', paused]].map(([label, count]) =>
          <div key={label} className="px-3 sm:px-6"><dt className="min-h-8 text-xs text-muted sm:min-h-0">{label}</dt><dd className="mt-2 font-heading text-2xl font-bold">{loading ? '...' : count}</dd></div>)}
      </dl>

      {error && <div role="alert" className="mb-5 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{error}</div>}
      {notice && <p role="status" className="mb-5 rounded-lg bg-sage p-4 text-sm text-green">{notice}</p>}
      {editor && <MonitorForm key={editor.monitor?.id ?? 'new'} monitor={editor.monitor} busy={busy} onSave={save} onCancel={() => { setEditor(null); setError('') }} />}

      <section aria-label="Your monitor list" className="overflow-hidden rounded-xl border border-line bg-white">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-4 sm:px-6">
          <label className="flex min-w-0 flex-1 items-center gap-2 text-muted"><Search size={17} className="shrink-0" /><span className="sr-only">Search monitors</span><input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Search monitors" className="min-w-0 w-full max-w-sm rounded-md bg-transparent p-2 text-sm text-ink" /></label>
          <button type="button" onClick={refresh} disabled={disabled} className="inline-flex items-center gap-2 rounded-md p-2 text-xs font-medium text-muted hover:text-green disabled:opacity-50"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} />Refresh</button>
        </div>
        {loading && <p role="status" className="p-8 text-center text-sm text-muted">Loading monitors...</p>}
        {!loading && monitors.length === 0 && !error && <div className="px-6 py-14 text-center"><Activity size={28} className="mx-auto mb-4 text-green" /><h2 className="font-heading text-xl font-bold">Start with your first monitor.</h2><p className="mt-2 text-sm text-muted">Add a website or API and we will check it regularly.</p><button type="button" disabled={disabled} onClick={() => openEditor(null)} className="button button-outline mt-6 disabled:opacity-50">Add your first monitor</button></div>}
        {!loading && monitors.length > 0 && matching.length === 0 && <p className="p-10 text-center text-sm text-muted">No monitors match your search.</p>}
        <div className="divide-y divide-line">
          {matching.map(monitor => {
            const paused = monitor.status === 'PAUSED'
            const label = paused ? 'Paused' : !monitor.lastCheckedAt ? 'Awaiting check' : monitor.status === 'UP' ? 'Up' : monitor.status === 'DOWN' ? 'Down' : 'Slow response'
            const color = paused || !monitor.lastCheckedAt ? 'bg-sage text-muted' : monitor.status === 'UP' ? 'bg-sage text-green' : monitor.status === 'DOWN' ? 'bg-rose-50 text-rose-700' : 'bg-amber-50 text-amber-800'
            return <article key={monitor.id} aria-label={monitor.name} className="p-5 sm:p-6">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0 flex-1"><h2 className="break-words font-heading font-bold">{monitor.name}</h2><p className="mt-1 break-all text-sm text-muted">{monitor.url}</p></div>
                <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-medium ${color}`}><span className="size-1.5 rounded-full bg-current" />{label}</span>
              </div>
              <div className="mt-5 flex flex-wrap items-end justify-between gap-4">
                <div className="text-xs leading-6 text-muted"><p>{monitor.httpMethod} · Every {monitor.intervalSeconds}s · Timeout {monitor.timeoutSeconds}s</p><p>Last check: {monitor.lastCheckedAt ? new Date(monitor.lastCheckedAt).toLocaleString() : 'Not checked yet'}</p></div>
                <div className="flex flex-wrap gap-2">
                  <button type="button" disabled={disabled || deleting !== null} onClick={() => openEditor(monitor)} className="dashboard-action"><Pencil size={14} />Edit<span className="sr-only"> {monitor.name}</span></button>
                  <button type="button" disabled={disabled || deleting !== null} onClick={() => togglePause(monitor)} className="dashboard-action">{paused ? <Play size={14} /> : <Pause size={14} />}{paused ? 'Resume' : 'Pause'}<span className="sr-only"> {monitor.name}</span></button>
                  <button type="button" disabled={disabled || deleting !== null} onClick={() => { setDeleting(monitor.id); setError(''); setNotice('') }} className="dashboard-action text-rose-700"><Trash2 size={14} />Delete<span className="sr-only"> {monitor.name}</span></button>
                </div>
              </div>
              {deleting === monitor.id && <div role="group" aria-label={`Delete ${monitor.name}`} className="mt-5 rounded-lg border border-rose-200 bg-rose-50 p-4">
                <p className="text-sm font-medium text-rose-900">Delete {monitor.name}?</p><p className="mt-1 text-xs leading-5 text-rose-800">This permanently deletes the monitor and its check history. This cannot be undone.</p>
                <div className="mt-4 flex flex-wrap gap-3"><button type="button" disabled={busy} onClick={() => remove(monitor)} className="button bg-rose-700 px-4 py-2 text-white hover:bg-rose-800 disabled:opacity-50">{busy ? 'Deleting...' : 'Delete permanently'}</button><button type="button" disabled={busy} onClick={() => setDeleting(null)} className="button button-outline px-4 py-2">Keep monitor</button></div>
              </div>}
            </article>
          })}
        </div>
      </section>
      <p className="mt-5 text-xs text-muted">Only your monitors appear here. <a href="#status" className="font-medium text-green hover:underline">View public status</a></p>
    </main>
  )
}

function message(exception: unknown) {
  return exception instanceof Error ? exception.message : 'Something went wrong. Please try again.'
}
