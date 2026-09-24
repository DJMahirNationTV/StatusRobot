export interface Monitor {
  id: number;
  name: string;
  url: string;
  active: boolean;
}

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