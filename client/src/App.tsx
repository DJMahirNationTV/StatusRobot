import { useEffect, useState, useSyncExternalStore } from 'react'
import { LandingPage } from './pages/LandingPage'
import { StatusPage } from './pages/StatusPage'
import { AuthPage } from './pages/AuthPage'
import { AccountPage } from './pages/AccountPage'
import { authApi } from './services/auth'
import type { AuthUser } from './services/auth'

const repositoryUrl = 'https://github.com/DJMahirNationTV/StatusRobot'

function subscribeToLocation(callback: () => void) {
  window.addEventListener('hashchange', callback)
  return () => window.removeEventListener('hashchange', callback)
}

const repositoryUrl = 'https://github.com/DJMahirNationTV/StatusRobot'

function subscribeToLocation(callback: () => void) {
  window.addEventListener('hashchange', callback)
  return () => window.removeEventListener('hashchange', callback)
}

function App() {
  const hash = useSyncExternalStore(subscribeToLocation, () => window.location.hash, () => '')
  const [route, query = ''] = hash.split('?')
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [authError, setAuthError] = useState('')

  useEffect(() => {
    let active = true
    authApi.me()
      .then(account => { if (active) setUser(account) })
      .catch(exception => { if (active) setAuthError(exception.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  function signedIn(account: AuthUser) {
    setUser(account)
    setAuthError('')
    window.location.hash = '#account'
  }

  function signedOut() {
    setUser(null)
    window.location.hash = '#login'
  }

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
            <span className="font-heading text-lg font-extrabold tracking-[-0.8px] sm:text-[23px]">StatusRobot</span>
          </a>
          <nav aria-label="Main navigation" className="flex items-center gap-4 text-sm font-medium text-muted sm:gap-7">
            <a href="#status" className="nav-link hidden sm:inline">Live status</a>
            <a href={user ? '#account' : '#login'} className="nav-link">{user ? 'Account' : 'Sign in'}</a>
          </nav>
        </div>
      </header>
      {route === '#status' ? <StatusPage />
        : route === '#login' || route === '#register' ? <AuthPage key={hash} mode={route === '#register' ? 'register' : 'login'} query={query} onLogin={signedIn} />
        : route === '#account' ? <AccountPage user={user} loading={loading} error={authError} onLogout={signedOut} />
        : <LandingPage repositoryUrl={repositoryUrl} />}
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
