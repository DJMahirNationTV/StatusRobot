import type {Monitor, MonitorStats, PingLog} from '../types/monitor';
import type { MonitorHistory } from '../types/history';

export const API_BASE = import.meta.env.VITE_API_URL || '/api';

export const api = {
  async getHistory(monitorId: number, hours = 24): Promise<MonitorHistory> {
    const res = await fetch(`${API_BASE}/monitors/${monitorId}/history?hours=${hours}`, { signal: AbortSignal.timeout(15000) });
    if (!res.ok) throw new Error('History is temporarily unavailable.');
    return res.json();
  },
  async getMonitors(): Promise<Monitor[]> {
    const res = await fetch(`${API_BASE}/monitors`, { signal: AbortSignal.timeout(15000) });
    if (!res.ok) throw new Error('Error on loading Monitors');
    return res.json();
  },

  async getStats(monitorId: number): Promise<MonitorStats> {
    const res = await fetch(`${API_BASE}/monitors/${monitorId}/stats`);
    if (!res.ok) throw new Error('Error on loading the Statistics');
    return res.json();
  },

  async getRecentPings(monitorId: number, limit = 30): Promise<PingLog[]> {
    const res = await fetch(`${API_BASE}/monitors/${monitorId}/pings?limit=${limit}`, { signal: AbortSignal.timeout(15000) });
    if (!res.ok) throw new Error('Error on loading the Pings');
    return res.json();
  }
};
