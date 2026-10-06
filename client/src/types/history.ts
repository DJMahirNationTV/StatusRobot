export interface UptimePeriod {
  days: number
  checks: number
  successful: number
  uptimePercentage: number | null
}
export interface UptimeDay {
  date: string
  checks: number
  successful: number
  uptimePercentage: number | null
}
export interface ResponsePoint {
  timestamp: string
  responseTimeMs: number | null
  checks: number
  successful: number
}
export interface MonitorHistory {
  generatedAt: string
  hours: number
  bucketMinutes: number
  periods: UptimePeriod[]
  daily: UptimeDay[]
  response: ResponsePoint[]
}
