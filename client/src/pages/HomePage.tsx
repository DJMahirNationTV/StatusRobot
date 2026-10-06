import { useEffect, useState } from 'react'
import { statusPageApi } from '../services/statusPages'
import type { StatusPageData } from '../services/statusPages'
import { StatusPage } from './StatusPage'
import { LandingPage } from './LandingPage'

export function HomePage({ repositoryUrl }: { repositoryUrl: string }) {
  const [page, setPage] = useState<StatusPageData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    let pending = false
    async function load() {
      if (pending) return
      pending = true
      try {
        const current = await statusPageApi.defaultPage()
        if (active) {
          setPage(current)
          setError('')
        }
      } catch {
        if (active)
          setError('The homepage could not be loaded. Please try again.')
      } finally {
        pending = false
        if (active) setLoading(false)
      }
    }
    void load()
    const timer = window.setInterval(() => void load(), 30000)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [attempt])
  if (loading)
    return (
      <main id="main" tabIndex={-1} className="page-shell flex-1 py-16">
        <p role="status" className="text-sm text-muted">
          Loading...
        </p>
      </main>
    )
  if (error)
    return (
      <main id="main" tabIndex={-1} className="page-shell flex-1 py-16">
        <p role="alert" className="text-sm text-muted">
          {error}
        </p>
        <button
          onClick={() => {
            setLoading(true)
            setAttempt((value) => value + 1)
          }}
          className="button button-outline mt-5"
        >
          Retry
        </button>
      </main>
    )
  return page ? (
    <StatusPage key={page.id} page={page} />
  ) : (
    <LandingPage repositoryUrl={repositoryUrl} />
  )
}
