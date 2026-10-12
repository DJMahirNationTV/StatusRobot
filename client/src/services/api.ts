import axios from 'axios'
import type { Monitor, MonitorStats, PingLog } from '../types/monitor'
import type { MonitorHistory } from '../types/history'

export const API_BASE = import.meta.env.VITE_API_URL || '/api'

export const http = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  withCredentials: true, // Send the session cookie with API requests.
  withXSRFToken: false, // We get the CSRF header from /auth/csrf ourselves.
})

export function requestError(error: unknown, fallback: string, redirectToLogin = false) {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    if (redirectToLogin && error.response?.status === 401) {
      window.location.hash = '#login'
      return new Error('Your session expired. Please sign in again.')
    }
    if (!error.response) return new Error('Could not reach the server. Please try again.')
    const message = error.response.data?.message
    if (typeof message === 'string' && message.trim()) return new Error(message)
  }
  return new Error(fallback)
}

export const api = {
  async getHistory(monitorId: number, hours = 24): Promise<MonitorHistory> {
    try {
      const response = await http.get<MonitorHistory>(`/monitors/${monitorId}/history`, { params: { hours } })
      return response.data
    } catch (error) {
      throw requestError(error, 'History is temporarily unavailable.')
    }
  },
  async getMonitors(): Promise<Monitor[]> {
    try {
      const response = await http.get<Monitor[]>('/monitors')
      return response.data
    } catch (error) {
      throw requestError(error, 'Error on loading Monitors')
    }
  },
  async getStats(monitorId: number): Promise<MonitorStats> {
    try {
      const response = await http.get<MonitorStats>(`/monitors/${monitorId}/stats`)
      return response.data
    } catch (error) {
      throw requestError(error, 'Error on loading the Statistics')
    }
  },
  async getRecentPings(monitorId: number, limit = 30): Promise<PingLog[]> {
    try {
      const response = await http.get<PingLog[]>(`/monitors/${monitorId}/pings`, { params: { limit } })
      return response.data
    } catch (error) {
      throw requestError(error, 'Error on loading the Pings')
    }
  },
}
