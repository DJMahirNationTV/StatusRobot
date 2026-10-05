import { useState } from 'react'
import { authApi } from '../services/auth'
import type { AuthUser } from '../services/auth'

interface AccountPageProps {
  user: AuthUser | null
  loading: boolean
  error: string
  onLogout: () => void
}

export function AccountPage({ user, loading, error, onLogout }: AccountPageProps) {
  const [busy, setBusy] = useState(false)
  const [logoutError, setLogoutError] = useState('')

  async function logout() {
    setBusy(true)
    try {
      await authApi.logout()
      onLogout()
    } catch (exception) {
      setLogoutError(exception instanceof Error ? exception.message : 'Could not sign out. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main id="main" tabIndex={-1} className="page-shell flex flex-1 items-center justify-center py-14">
      <section className="w-full max-w-md rounded-2xl border border-line bg-white p-7 sm:p-9">
        <p className="eyebrow mb-3">Your account</p>
        <h1 className="font-heading text-3xl font-bold tracking-tight">{loading ? 'Checking your session.' : user ? 'You’re signed in.' : 'Sign in to continue.'}</h1>
        {(error || logoutError) && <p role="alert" className="mt-5 text-sm leading-6 text-rose-800">{logoutError || error}</p>}
        {user ? <>
          <dl className="my-7 space-y-5 text-sm">
            <div><dt className="text-xs text-muted">Email</dt><dd className="mt-1 break-all font-medium">{user.email}</dd></div>
            <div><dt className="text-xs text-muted">Sign-in method</dt><dd className="mt-1 font-medium">{user.provider === 'local' ? 'Email and password' : user.provider === 'github' ? 'GitHub' : 'Discord'}</dd></div>
          </dl>
          <a href="#dashboard" className="button button-green w-full">Manage monitors</a>
          <button type="button" onClick={logout} disabled={busy} className="button button-outline mt-3 w-full disabled:opacity-60">{busy ? 'Signing out...' : 'Sign out'}</button>
        </> : !loading && <a href="#login" className="button button-green mt-7 w-full">Sign in</a>}
      </section>
    </main>
  )
}
