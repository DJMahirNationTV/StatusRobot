import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import type {MonitorWithDetails} from '../types/monitor';
import { UptimeBar } from '../components/UptimeBar';

export const StatusPage: React.FC = () => {
  const [monitors, setMonitors] = useState<MonitorWithDetails[]>([]);
  const [loading, setLoading] = useState(true); // alr true by default

  useEffect(() => {
    let isMounted = true;
    const fetchData = async () => {
      try {
        const list = await api.getMonitors();
        const detailed = await Promise.all(
          list.map(async (m) => {
            const [stats, pings] = await Promise.all([
              api.getStats(m.id).catch(() => undefined),
              api.getRecentPings(m.id, 30).catch(() => [])
            ]);
            return { ...m, stats, pings };
          })
        );

        if (isMounted) {
          setMonitors(detailed);
        }
      } catch (err) {
        console.error('Failed to load status data:', err);
      } finally {
        if (isMounted) {
          setLoading(false); // executes only when async complete
        }
      }
    };

    // Initial fetch (all state updates are deferred until after await resolves)
    fetchData();

    const interval = setInterval(fetchData, 30000); // 30 sec
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  const allOperational = monitors.every(
    (m) => m.pings && m.pings.length > 0 && m.pings[0].successful
  );

  if (loading) {
    return <div className="p-8 text-zinc-400">Load Status pages...</div>;
  }

  return (
    <main className="max-w-4xl mx-auto px-4 py-12 text-zinc-100 font-sans">
      {/* Header Banner */}
      <header className={`p-4 rounded-xl mb-8 flex items-center gap-3 border ${
        allOperational ? 'bg-emerald-950/20 border-emerald-900/50 text-emerald-400' : 'bg-rose-950/20 border-rose-900/50 text-rose-400'
      }`}>
        <span className="w-3 h-3 rounded-full bg-current animate-pulse" />
        <h1 className="font-semibold text-lg">
          {allOperational ? 'All the Systems are OK' : 'Limited operation detected'}
        </h1>
      </header>

      {/* Monitor Liste */}
      <div className="space-y-4">
        {monitors.map((m) => (
          <div key={m.id} className="p-5 bg-zinc-900/80 rounded-xl border border-zinc-800/80 space-y-3">
            <div className="flex justify-between items-center">
              <div>
                <h2 className="font-medium text-white">{m.name}</h2>
                <span className="text-xs text-zinc-400">{m.url}</span>
              </div>
              <div className="text-right">
                <span className="text-sm font-semibold text-emerald-400">
                  {m.stats ? `${m.stats.uptimePercentage}%` : '100%'}
                </span>
                <p className="text-xs text-zinc-400">
                  {m.stats ? `${m.stats.averageResponseTimeMs} ms Ø` : '-'}
                </p>
              </div>
            </div>

            {m.pings && <UptimeBar pings={m.pings} />}
          </div>
        ))}
      </div>
    </main>
  );
};