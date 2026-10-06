import { useEffect, useState } from 'react'
import { api } from '../services/api'
import type { MonitorHistory } from '../types/history'
import { ResponseTimeChart } from './ResponseTimeChart'

export function MonitorHistoryPanel({
  monitorId,
  refresh
}: {
  monitorId: number
  refresh: number
}) {
  const [hours, setHours] = useState(24)
  const [history, setHistory] = useState<MonitorHistory | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    api
      .getHistory(monitorId, hours)
      .then((data) => {
        if (active) {
          setHistory(data)
          setError('')
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
  }, [monitorId, hours, refresh])

  return (
    <div className="mt-6 space-y-6 border-t border-line pt-6">
      <section aria-label="Response time history">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-sm font-semibold">Response time</h3>
          <label className="flex items-center gap-2 text-xs text-muted">
            History
            <select
              value={hours}
              onChange={(event) => {
                setHours(Number(event.target.value))
                setLoading(true)
              }}
              className="rounded-lg border border-line bg-canvas px-3 py-2 text-xs text-ink"
            >
              <option value={6}>Last 6 hours</option>
              <option value={24}>Last 24 hours</option>
              <option value={168}>Last 7 days</option>
            </select>
          </label>
        </div>
        {loading ? (
          <p role="status" className="py-8 text-center text-xs text-muted">
            Loading check history...
          </p>
        ) : error ? (
          <p
            role="status"
            className="rounded-lg bg-amber-50 p-4 text-xs text-amber-800"
          >
            {error} Refresh status to try again.
          </p>
        ) : (
          history && (
            <ResponseTimeChart
              key={`${hours}-${history.generatedAt}`}
              history={history}
            />
          )
        )}
      </section>
      {history && !loading && !error && (
        <>
          <section aria-label="Uptime over the last 90 days">
            <div className="mb-4 flex flex-wrap items-center gap-2">
              <h3 className="text-sm font-semibold">Uptime</h3>
              <span className="text-xs text-muted">Last 90 days</span>
            </div>
            <div className="flex h-7 gap-[2px]">
              {history.daily.map((day, index) => (
                <div key={day.date} className="group relative min-w-0 flex-1">
                  <button
                    type="button"
                    aria-label={`${day.date} UTC: ${day.uptimePercentage === null ? 'No data' : `${day.uptimePercentage.toFixed(3)}% uptime`}, ${day.checks} checks`}
                    className={`block h-full w-full rounded-sm ${day.uptimePercentage === null ? 'bg-line' : day.uptimePercentage === 100 ? 'bg-green' : day.uptimePercentage === 0 ? 'bg-rose-600' : 'bg-amber-500'}`}
                  />
                  <div
                    role="tooltip"
                    className={`pointer-events-none absolute bottom-10 z-10 hidden w-max max-w-56 rounded-lg bg-ink px-3 py-2 text-xs text-white group-hover:block group-focus-within:block ${index < 12 ? 'left-0' : index > 77 ? 'right-0' : 'left-1/2 -translate-x-1/2'}`}
                  >
                    <p>{day.date} UTC</p>
                    <p className="mt-1 font-semibold">
                      {day.uptimePercentage === null
                        ? 'No data'
                        : `${day.uptimePercentage.toFixed(3)}% uptime`}
                    </p>
                    <p className="mt-1 text-white/75">
                      {day.successful} of {day.checks} checks successful
                    </p>
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-2 flex justify-between text-[10px] text-muted">
              <span>{history.daily[0]?.date}</span>
              <span>Today (UTC)</span>
            </div>
            <p className="mt-3 text-[11px] text-muted">
              Green: available. Amber: partial outage. Red: unavailable. Gray:
              no checks.
            </p>
          </section>
          <section aria-label="Overall uptime">
            <h3 className="mb-4 text-sm font-semibold">Overall uptime</h3>
            <dl className="grid grid-cols-2 gap-y-5 rounded-lg bg-canvas p-4 sm:grid-cols-4">
              {history.periods.map((period) => (
                <div key={period.days} className="px-2">
                  <dd className="font-heading text-lg font-bold">
                    {period.uptimePercentage === null
                      ? 'No data'
                      : `${period.uptimePercentage.toFixed(3)}%`}
                  </dd>
                  <dt className="mt-1 text-xs text-muted">
                    {period.days === 1
                      ? 'Last 24 hours'
                      : `Last ${period.days} days`}
                  </dt>
                </div>
              ))}
            </dl>
            <p className="mt-3 text-[11px] text-muted">
              Uptime is based on recorded checks. Pauses and missing checks are
              not counted.
            </p>
          </section>
        </>
      )}
    </div>
  )
}
