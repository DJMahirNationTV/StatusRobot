import { useEffect, useState } from 'react'
import { Activity, CircleAlert, RefreshCw, Server } from 'lucide-react'
import { api } from '../services/api'
import type { MonitorWithDetails } from '../types/monitor'
import { UptimeBar } from '../components/UptimeBar'

export function StatusPage() {
  const [monitors, setMonitors] = useState<MonitorWithDetails[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    let disposed = false
    let pending = false

    async function loadData() {
      if (pending) return
      pending = true
      try {
        const list = await api.getMonitors()
        const detailed = await Promise.all(list.map(async (monitor) => {
          const [stats, pings] = await Promise.all([
            api.getStats(monitor.id).catch(() => undefined),
            api.getRecentPings(monitor.id, 30).catch(() => []),
          ])
          return { ...monitor, stats, pings }
        }))
        if (!disposed) {
          setMonitors(detailed)
          setUpdatedAt(new Date())
          setError(false)
        }
      } catch {
        if (!disposed) setError(true)
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
  }, [refresh])

  const activeMonitors = monitors.filter(monitor => monitor.status !== 'PAUSED')
  const hasChecks = activeMonitors.length > 0 && activeMonitors.every(monitor => monitor.pings?.length)
  const allOperational = hasChecks && activeMonitors.every(monitor => monitor.pings?.[0].successful)
  const hasFailure = activeMonitors.some(monitor => monitor.pings?.[0]?.successful === false)
  const heading = error ? 'Status is currently unavailable' : monitors.length === 0 ? 'No monitors to show yet' : hasFailure ? 'Some services need attention' : activeMonitors.length === 0 ? 'All monitors are paused' : !hasChecks ? 'Waiting for service checks' : 'All systems operational'

  return (
    <main id="main" tabIndex={-1} className="page-shell flex-1 py-12 sm:py-18">
      <div className="mx-auto max-w-4xl">
        <div className="mb-9 flex flex-wrap items-end justify-between gap-5">
          <div><p className="eyebrow mb-3">The latest from your services</p><h1 className="section-heading">Live status.</h1></div>
          <button type="button" disabled={loading} onClick={() => { setLoading(true); setRefresh(value => value + 1) }} className="button button-outline disabled:opacity-50"><RefreshCw size={15} className={loading ? 'animate-spin' : ''} />{loading ? 'Refreshing' : 'Refresh status'}</button>
        </div>
        <div role="status" className={`mb-8 flex items-center gap-4 rounded-xl border p-5 ${error ? 'border-amber-200 bg-amber-50' : allOperational ? 'border-[#dbead7] bg-[#f0f7ed]' : 'border-line bg-white'}`}>
          {error ? <CircleAlert className="shrink-0 text-amber-700" size={23} /> : <Activity className="shrink-0 text-green" size={23} />}
          <div><h2 className="font-heading text-lg font-bold">{loading ? 'Checking your services' : heading}</h2><p className="mt-1 text-xs leading-5 text-muted">{loading ? 'Fetching the latest uptime and response times.' : error ? 'We could not reach the monitoring service. Please try refreshing in a moment.' : updatedAt ? `Last refreshed at ${updatedAt.toLocaleTimeString()}. Updates every 30 seconds.` : 'Updates every 30 seconds.'}</p></div>
        </div>
        {!loading && !error && monitors.length === 0 && <div className="rounded-xl border border-dashed border-line px-6 py-16 text-center"><Server size={32} strokeWidth={1.5} className="mx-auto mb-4 text-green" /><h2 className="font-heading text-xl font-bold">A fresh start.</h2><p className="mx-auto mt-3 max-w-sm text-sm leading-6 text-muted">Your connected monitors will appear here with their latest checks and uptime history.</p><a href="#" className="button button-outline mt-6">Back to home</a></div>}
        {error && monitors.length > 0 && <p className="mb-4 text-xs text-amber-800">Showing the last available results. These may be out of date.</p>}
        <div className="space-y-4">
          {monitors.map(monitor => {
            const latestPing = monitor.pings?.[0]
            const paused = monitor.status === 'PAUSED'
            const statusLabel = paused ? 'Paused' : latestPing ? latestPing.successful ? 'Operational' : 'Not responding' : 'No checks yet'
            const statusColor = paused || !latestPing ? 'text-muted' : latestPing.successful ? 'text-green' : 'text-rose-700'
            return (
              <article key={monitor.id} className="rounded-xl border border-line bg-white p-5 sm:p-6">
                <div className="mb-5 flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0"><h2 className="font-heading font-bold">{monitor.name}</h2><p className="mt-1 break-all text-xs text-muted">{monitor.url}</p></div>
                  <span className={`flex items-center gap-2 text-xs font-medium ${statusColor}`}><span className="size-1.5 rounded-full bg-current" />{statusLabel}</span>
                </div>
                {monitor.pings?.length ? <UptimeBar pings={monitor.pings} /> : <p className="rounded-lg bg-canvas p-4 text-xs text-muted">Check history is not available yet.</p>}
                <div className="mt-4 flex flex-wrap justify-between gap-2 text-xs text-muted">
                  <span>{monitor.stats && monitor.stats.totalPings > 0 ? `${monitor.stats.uptimePercentage.toFixed(2)}% uptime` : 'Uptime not available'}</span>
                  <span>{monitor.stats && monitor.stats.totalPings > 0 ? `${Math.round(monitor.stats.averageResponseTimeMs)} ms average response` : 'Response time not available'}</span>
                </div>
              </article>
            )
          })}
        </div>
        <p className="mt-8 text-center text-xs text-muted">Powered by StatusRobot</p>
      </div>
    </main>
  )
}
