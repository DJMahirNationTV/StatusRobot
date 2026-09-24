import type {Monitor, MonitorStats, PingLog} from '../types/monitor';

const API_BASE = import.meta.env.VITE_API_URL || '/api';

export const api = {
  async getMonitors(): Promise<Monitor[]> {
    const res = await fetch(`${API_BASE}/monitors`);
    if (!res.ok) throw new Error('Error on loading Monitors');
    return res.json();
  },

  async getStats(monitorId: number): Promise<MonitorStats> {
    const res = await fetch(`${API_BASE}/monitors/${monitorId}/stats`);
    if (!res.ok) throw new Error('Error on loading the Statistics');
    return res.json();
  },

  async getRecentPings(monitorId: number, limit = 30): Promise<PingLog[]> {
    const res = await fetch(`${API_BASE}/monitors/${monitorId}/pings?limit=${limit}`);
    if (!res.ok) throw new Error('Error on loading the Pings');
    return res.json();
  }
};