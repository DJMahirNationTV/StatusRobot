import { useState } from 'react'
import type { MonitorHistory, ResponsePoint } from '../types/history'

export function ResponseTimeChart({ history }: { history: MonitorHistory }) {
  const [hovered, setHovered] = useState<number | null>(null)
  const interval = history.bucketMinutes * 60000
  const end = new Date(history.generatedAt).getTime()
  const start =
    Math.floor((end - history.hours * 3600000) / interval) * interval
  const byTime = new Map(
    history.response.map((point) => [
      new Date(point.timestamp).getTime(),
      point
    ])
  )
  const points: ResponsePoint[] = []
  for (let time = start; time <= end; time += interval)
    points.push(
      byTime.get(time) ?? {
        timestamp: new Date(time).toISOString(),
        responseTimeMs: null,
        checks: 0,
        successful: 0
      }
    )
  const measured = points.filter((point) => point.responseTimeMs !== null)
  if (measured.length === 0)
    return (
      <p className="rounded-lg bg-canvas px-4 py-10 text-center text-xs text-muted">
        No successful response-time samples in this period.
      </p>
    )
  const max =
    Math.max(100, ...measured.map((point) => point.responseTimeMs ?? 0)) * 1.1
  const x = (index: number) =>
    44 + (index / Math.max(1, points.length - 1)) * 716
  const y = (value: number) => 180 - (value / max) * 150
  let path = ''
  let connected = false
  points.forEach((point, index) => {
    if (point.responseTimeMs === null) {
      connected = false
      return
    }
    path += `${connected ? 'L' : 'M'} ${x(index)} ${y(point.responseTimeMs)} `
    connected = true
  })
  const selected = hovered === null ? null : points[hovered]

  return (
    <div className="relative">
      <svg
        viewBox="0 0 780 215"
        role="img"
        aria-label={`Average response time over the last ${history.hours} hours. Gaps indicate missing successful checks.`}
        className="block w-full"
        onPointerMove={(event) => {
          const rect = event.currentTarget.getBoundingClientRect()
          const position = ((event.clientX - rect.left) / rect.width) * 780
          setHovered(
            Math.max(
              0,
              Math.min(
                points.length - 1,
                Math.round(((position - 44) / 716) * (points.length - 1))
              )
            )
          )
        }}
        onPointerLeave={() => setHovered(null)}
      >
        {[0, 0.5, 1].map((value) => (
          <g key={value}>
            <line
              x1="44"
              x2="760"
              y1={y(max * value)}
              y2={y(max * value)}
              stroke="var(--color-line)"
              strokeDasharray="3 4"
            />
            <text
              x="36"
              y={y(max * value) + 4}
              textAnchor="end"
              className="text-[22px] sm:text-[10px]"
              fill="var(--color-muted)"
            >
              {Math.round(max * value)}
            </text>
          </g>
        ))}
        <path
          d={path}
          fill="none"
          stroke="var(--color-green)"
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        {points.map((point, index) =>
          point.checks > point.successful ? (
            <circle
              key={point.timestamp}
              cx={x(index)}
              cy="180"
              r="2.5"
              fill="#be123c"
            >
              <title>
                {new Date(point.timestamp).toLocaleString()}:{' '}
                {point.checks - point.successful} failed checks
              </title>
            </circle>
          ) : point.responseTimeMs !== null ? (
            <circle
              key={point.timestamp}
              cx={x(index)}
              cy={y(point.responseTimeMs)}
              r="1.7"
              fill="var(--color-green)"
            >
              <title>
                {new Date(point.timestamp).toLocaleString()}:{' '}
                {Math.round(point.responseTimeMs)} ms
              </title>
            </circle>
          ) : null
        )}
        {hovered !== null && (
          <line
            x1={x(hovered)}
            x2={x(hovered)}
            y1="25"
            y2="180"
            stroke="var(--color-muted)"
            strokeDasharray="3 3"
          />
        )}
        <text
          x="44"
          y="204"
          className="text-[22px] sm:text-[10px]"
          fill="var(--color-muted)"
        >
          {new Date(start).toLocaleString([], {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
          })}
        </text>
        <text
          x="760"
          y="204"
          textAnchor="end"
          className="text-[22px] sm:text-[10px]"
          fill="var(--color-muted)"
        >
          {new Date(end).toLocaleString([], {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
          })}
        </text>
      </svg>
      {selected && (
        <div className="pointer-events-none absolute top-1 right-3 max-w-[230px] rounded-lg border border-line bg-white px-3 py-2 text-xs shadow-sm">
          <p className="text-muted">
            {new Date(selected.timestamp).toLocaleString()}
          </p>
          <p className="mt-1 font-semibold">
            {selected.responseTimeMs === null
              ? 'No successful checks'
              : `${Math.round(selected.responseTimeMs)} ms average`}
          </p>
          <p className="mt-1 text-muted">
            {selected.checks} checks
            {selected.checks > selected.successful
              ? `, ${selected.checks - selected.successful} failed`
              : ''}
          </p>
        </div>
      )}
      <p className="mt-2 text-[11px] text-muted">
        Response time in milliseconds. Red dots mark failed checks. Gaps have no
        successful samples.
      </p>
    </div>
  )
}
