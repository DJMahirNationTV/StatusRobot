import React from 'react';
import type {PingLog} from '../types/monitor';

interface Props {
  pings: PingLog[];
}

export const UptimeBar: React.FC<Props> = ({ pings }) => {
  // Old stuff on the left and the new on the Right
  const orderedPings = [...pings].reverse();

  return (
    <div className="flex items-center gap-1 w-full h-8 bg-zinc-900/40 p-1 rounded-md border border-zinc-800">
      {orderedPings.map((ping) => {
        const isSuccess = ping.successful && ping.statusCode >= 200 && ping.statusCode < 300;
        return (
          <div
            key={ping.id}
            className={`flex-1 h-full rounded-xs transition-opacity hover:opacity-80 cursor-pointer ${
              isSuccess ? 'bg-emerald-500' : 'bg-rose-500'
            }`}
            title={`${new Date(ping.timestamp).toLocaleTimeString()}: ${ping.statusCode || 'ERR'} (${ping.responseTimeMs}ms)`}
          />
        );
      })}
    </div>
  );
};