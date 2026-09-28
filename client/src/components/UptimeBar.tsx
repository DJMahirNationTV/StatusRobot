import type { PingLog } from '../types/monitor'

export function UptimeBar({ pings }: { pings: PingLog[] }) {
  const orderedPings = [...pings].reverse()

  return (
    <div>
      <div className="flex h-9 w-full gap-1" role="img" aria-label={`${pings.filter(ping => ping.successful).length} of the last ${pings.length} checks were successful`}>
        {orderedPings.map(ping => (
          <span
            key={ping.id}
            className={`h-full min-w-0 flex-1 rounded-sm transition-opacity hover:opacity-75 ${ping.successful ? 'bg-[#55a56b]' : 'bg-rose-500'}`}
            title={`${new Date(ping.timestamp).toLocaleString()}: ${ping.statusCode || 'Error'} (${ping.responseTimeMs} ms)`}
          />
        ))}
      </div>
      <div className="mt-2 flex justify-between text-[10px] text-muted"><span>Earlier checks</span><span>Latest check</span></div>
    </div>
  )
}
