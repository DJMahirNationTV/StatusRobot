export interface Monitor {
  id: number;
  name: string;
  url: string;
  httpMethod: 'GET' | 'HEAD' | 'POST';
  intervalSeconds: number;
  timeoutSeconds: number;
  lastCheckedAt: string | null;
  createdAt: string;
  status: 'UP' | 'DOWN' | 'DEGRADED' | 'PAUSED';
}

export type MonitorInput = Pick<Monitor, 'name' | 'url' | 'httpMethod' | 'intervalSeconds' | 'timeoutSeconds'> & { integrationIds: number[] };

export interface MonitorStats {
  monitorId: number;
  uptimePercentage: number;
  averageResponseTimeMs: number;
  totalPings: number;
  successfulPings: number;
}

export interface PingLog {
  id: string;
  statusCode: number;
  responseTimeMs: number;
  successful: boolean;
  errorMessage: string | null;
  timestamp: string;
}

export interface MonitorWithDetails extends Monitor {
  stats?: MonitorStats;
  pings?: PingLog[];
}
