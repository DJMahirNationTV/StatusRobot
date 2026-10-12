import axios from 'axios'
import { http, requestError } from './api'
import { csrfHeaders } from './auth'

interface RequestOptions {
  timeout?: number
  fallback?: string
  notFound?: string
}

export async function requestApi(path: string, method = 'GET', data?: unknown, options: RequestOptions = {}) {
  // Reads need no CSRF token. Every write gets a fresh one.
  const headers = method === 'GET' ? {} : await csrfHeaders()
  try {
    return await http.request({ url: path, method, data, headers, timeout: options.timeout ?? 15000 })
  } catch (error) {
    const fallback = axios.isAxiosError(error) && error.response?.status === 404 && options.notFound
      ? options.notFound : options.fallback ?? 'Could not complete this request. Please try again.'
    throw requestError(error, fallback, true)
  }
}
