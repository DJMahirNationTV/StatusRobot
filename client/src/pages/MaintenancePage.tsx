import { useEffect, useRef, useState } from 'react'
import { Plus, RefreshCw, Wrench } from 'lucide-react'
import { maintenanceApi, maintenanceLabels } from '../services/maintenance'
import type { Maintenance, MaintenanceListing } from '../services/maintenance'
import { monitorApi } from '../services/monitors'
import type { Monitor } from '../types/monitor'

export function MaintenancePage() {
  const [result, setResult] = useState<MaintenanceListing | null>(null)
  const [monitors, setMonitors] = useState<Monitor[]>([])
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [confirm, setConfirm] = useState<{ window: Maintenance; action: 'cancel' | 'delete' } | null>(null)
  const confirmation = useRef<HTMLElement>(null)

  useEffect(() => {
    // Bring the confirmation into view for long lists.
    if (confirm) confirmation.current?.focus()
  }, [confirm])

  useEffect(() => {
    let active = true
    let pending = false
    async function load() {
      if (pending) return
      pending = true
      try {
        const [listing, owned] = await Promise.all([maintenanceApi.list(page), monitorApi.list()])
        if (active) { setResult(listing); setMonitors(owned); setError('') }
      } catch (exception) {
        if (active) setError(message(exception))
      } finally {
        pending = false
      }
    }
    void load()
    const interval = window.setInterval(() => void load(), 30000)
    return () => { active = false; window.clearInterval(interval) }
  }, [page, reload])

  function refresh(nextPage = page) {
    setResult(null)
    setError('')
    setConfirm(null)
    setPage(nextPage)
    setReload(value => value + 1)
  }

  async function change(window: Maintenance, action: 'cancel' | 'delete' | 'publish') {
    if (busy) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      if (action === 'cancel') await maintenanceApi.cancel(window.id)
      else if (action === 'delete') await maintenanceApi.remove(window.id)
      else await maintenanceApi.publish(window.id, !window.published)
      setConfirm(null)
      setNotice(action === 'cancel' ? 'Maintenance cancelled.' : action === 'delete'
        ? 'Maintenance deleted. Your monitors and check history are unchanged.'
        : window.published ? 'Notice hidden from status pages.' : 'Notice published on matching status pages.')
      refresh(action === 'delete' && result?.maintenance.length === 1 && page > 0 ? page - 1 : page)
    } catch (exception) {
      setError(message(exception))
    } finally {
      setBusy(false)
    }
  }

  const loading = result === null && !error

  return (
    <>
      <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow mb-2">Planned work</p>
          <h1 className="dashboard-heading">Maintenance<span className="text-green">.</span></h1>
          <p className="mt-3 text-sm text-muted">Schedule work without triggering outage alerts.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <a href="#dashboard/maintenance/new/" className="button button-green"><Plus size={16} />Schedule maintenance</a>
          <button disabled={loading || busy} onClick={() => refresh()} className="dashboard-action"><RefreshCw size={15} />Refresh</button>
        </div>
      </header>
      {error && <p role="alert" className="dashboard-error mb-5">{error}
        <button disabled={busy} onClick={() => refresh()} className="ml-3 underline">Try again</button>
      </p>}
      {notice && <p role="status" className="mb-5 rounded-lg bg-sage p-4 text-sm text-green">{notice}</p>}
      {confirm && <section ref={confirmation} tabIndex={-1} aria-label="Confirm maintenance change" className="dashboard-panel mb-6 p-5">
        <h2 className="break-words text-sm font-semibold">{confirm.action === 'cancel' ? 'Cancel' : 'Delete'} {confirm.window.title}?</h2>
        <p className="mt-2 text-xs leading-6 text-muted">{confirm.action === 'cancel'
          ? 'Automatic checks resume when due, unless another active window covers these monitors. Manually paused monitors stay paused.'
          : 'This removes the maintenance schedule permanently. It does not delete any monitors or check history.'}</p>
        <div className="mt-4 flex flex-wrap gap-3">
          <button disabled={busy || loading || Boolean(error)} onClick={() => change(confirm.window, confirm.action)} className="dashboard-action text-rose-700">
            {confirm.action === 'cancel' ? 'Confirm cancellation' : 'Delete permanently'}</button>
          <button disabled={busy} onClick={() => setConfirm(null)} className="dashboard-action">Keep maintenance</button>
        </div>
      </section>}
      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_240px]">
        <section aria-label="Your maintenance" className="min-w-0">
          <p className="mb-4 text-xs text-muted">{loading ? 'Loading...' : result
            ? `${result.totalElements} ${result.totalElements === 1 ? 'maintenance window' : 'maintenance windows'}` : 'Schedules unavailable'}</p>
          {error && result && <p className="mb-4 text-xs text-amber-800">Showing the last available schedules. Refresh before making changes.</p>}
          <div className="dashboard-panel overflow-hidden">
            {loading && <p role="status" className="p-10 text-center text-sm text-muted">Loading maintenance...</p>}
            {result?.maintenance.length === 0 && <div className="px-6 py-16 text-center">
              <span className="mx-auto mb-5 flex size-14 items-center justify-center rounded-2xl border border-line bg-sage text-green"><Wrench size={25} /></span>
              <h2 className="font-heading text-lg font-bold">No maintenance scheduled.</h2>
              <p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-muted">Choose your monitors and set a time for planned work. Completed and cancelled windows stay in your history.</p>
            </div>}
            <div className="divide-y divide-line">
              {result?.maintenance.map(item => <article key={item.id} aria-label={item.title} className="p-5 sm:p-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <h2 className="break-words text-sm font-semibold">{item.status === 'SCHEDULED'
                      ? <a href={`#dashboard/maintenance/${item.id}/edit/`} className="hover:text-green">{item.title}</a> : item.title}</h2>
                    <p className="mt-2 break-words text-xs text-muted">{item.monitorIds.length > 0
                      ? item.monitorIds.map(id => monitors.find(monitor => monitor.id === id)?.name || `Monitor #${id}`).join(', ')
                      : 'No monitors remain in this window.'}</p>
                  </div>
                  <span className={`shrink-0 rounded-md px-2 py-1 text-xs font-medium ${item.status === 'IN_PROGRESS'
                    ? 'bg-amber-50 text-amber-800' : item.status === 'SCHEDULED' ? 'bg-sage text-green' : 'bg-canvas text-muted'}`}>
                    {maintenanceLabels[item.status]}</span>
                </div>
                <dl className="mt-5 grid gap-4 text-xs sm:grid-cols-3">
                  <div><dt className="text-muted">Starts</dt><dd className="mt-1"><time dateTime={item.startsAt}>{new Date(item.startsAt).toLocaleString()}</time></dd></div>
                  <div><dt className="text-muted">Ends</dt><dd className="mt-1"><time dateTime={item.endsAt}>{new Date(item.endsAt).toLocaleString()}</time></dd></div>
                  <div><dt className="text-muted">Status page notice</dt><dd className={`mt-1 ${item.published ? 'text-green' : ''}`}>{item.published ? 'Published' : 'Private'}</dd></div>
                </dl>
                {item.description && <details className="mt-4 text-xs"><summary className="cursor-pointer text-muted">Description</summary>
                  <p className="mt-3 whitespace-pre-wrap break-words leading-6 text-muted">{item.description}</p>
                </details>}
                <div className="mt-5 flex flex-wrap gap-2">
                  {item.status === 'SCHEDULED' && <a href={`#dashboard/maintenance/${item.id}/edit/`} className="dashboard-action">Edit<span className="sr-only"> {item.title}</span></a>}
                  {item.status !== 'COMPLETED' && <button disabled={busy || loading || Boolean(error)} onClick={() => change(item, 'publish')} className="dashboard-action">
                    {item.published ? 'Hide notice' : 'Publish notice'}<span className="sr-only"> for {item.title}</span></button>}
                  {(item.status === 'SCHEDULED' || item.status === 'IN_PROGRESS') && <button disabled={busy || loading || Boolean(error)} onClick={() => setConfirm({ window: item, action: 'cancel' })} className="dashboard-action">
                    Cancel<span className="sr-only"> {item.title}</span></button>}
                  {item.status !== 'IN_PROGRESS' && <button disabled={busy || loading || Boolean(error)} onClick={() => setConfirm({ window: item, action: 'delete' })} className="dashboard-action text-rose-700">
                    Delete<span className="sr-only"> {item.title}</span></button>}
                </div>
              </article>)}
            </div>
          </div>
          {result && result.totalPages > 1 && <nav aria-label="Maintenance pages" className="mt-4 flex items-center justify-between gap-3">
            <button disabled={page === 0 || busy} onClick={() => refresh(page - 1)} className="dashboard-action">Previous</button>
            <p className="text-xs text-muted">Page {page + 1} of {result.totalPages}</p>
            <button disabled={page + 1 >= result.totalPages || busy} onClick={() => refresh(page + 1)} className="dashboard-action">Next</button>
          </nav>}
          <p className="mt-4 text-xs text-muted">Only your schedules appear here. Times use your local timezone. Updates every 30 seconds.</p>
        </section>
        <aside className="dashboard-panel p-5">
          <h2 className="text-sm font-semibold">How maintenance works.</h2>
          <p className="mt-3 text-xs leading-6 text-muted">Automatic checks are skipped during the window and resume afterwards. Existing incidents are not closed automatically by maintenance.</p>
          <p className="mt-3 text-xs leading-6 text-muted">Overlapping windows keep checks skipped until no active window remains. A manually paused monitor stays paused.</p>
          <p className="mt-3 text-xs leading-6 text-muted">Publishing makes the title, description and times visible on status pages that include the selected monitors. Keep private details out of notices.</p>
        </aside>
      </div>
    </>
  )
}

function message(exception: unknown) {
  return exception instanceof Error ? exception.message : 'Could not load maintenance. Please try again.'
}
