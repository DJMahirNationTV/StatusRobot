import { useEffect, useSyncExternalStore } from 'react'
import { LandingPage } from './pages/LandingPage'
import { StatusPage } from './pages/StatusPage'

const repositoryUrl = 'https://github.com/DJMahirNationTV/StatusRobot'

function subscribeToLocation(callback: () => void) {
  window.addEventListener('hashchange', callback)
  return () => window.removeEventListener('hashchange', callback)
}

function App() {
  const hash = useSyncExternalStore(subscribeToLocation, () => window.location.hash, () => '')
  const isStatusPage = hash === '#status'

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [hash])

  return (
    <div className="flex min-h-svh flex-col">
      <a
        href="#main"
        onClick={event => {
          event.preventDefault()
          document.getElementById('main')?.focus()
        }}
        className="sr-only z-50 bg-white p-4 focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Skip to content
      </a>
      <header className="border-b border-line">
        <div className="page-shell flex h-20 items-center justify-between gap-4">
          <a href="#" aria-label="StatusRobot home" className="flex items-center gap-2.5">
            <img src="/logo.png" alt="" width={36} height={36} className="size-9 object-contain" />
            <span className="font-heading text-xl font-extrabold tracking-[-0.8px] sm:text-[23px]">StatusRobot</span>
          </a>
          <nav aria-label="Main navigation" className="flex items-center gap-7 text-sm font-medium text-muted">
            <a href={repositoryUrl} className="nav-link hidden sm:inline">GitHub</a>
            <a href={isStatusPage ? '#' : '#status'} className="nav-link">
              {isStatusPage ? 'Home' : 'Live status'}
            </a>
          </nav>
        </div>
      </header>
      {isStatusPage ? <StatusPage /> : <LandingPage repositoryUrl={repositoryUrl} />}
      <footer className="border-t border-line">
        <div className="page-shell flex flex-wrap items-center justify-between gap-3 py-6 text-xs text-muted">
          <p>Open source. MIT licensed.</p>
          <a href={repositoryUrl} className="nav-link">Source code on GitHub</a>
        </div>
      </footer>
    </div>
  )
}

export default App