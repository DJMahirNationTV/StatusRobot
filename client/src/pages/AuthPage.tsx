import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { authApi } from '../services/auth'
import type { AuthUser } from '../services/auth'

interface AuthPageProps {
  mode: 'login' | 'register'
  query: string
  onLogin: (user: AuthUser) => void
}

const oauthErrors: Record<string, string> = {
  account_exists: 'This email already has an account. Use your original sign-in method.',
  email_unverified: 'Verify your email with GitHub or Discord before signing in.',
  oauth: 'Social sign-in was cancelled or could not be completed. Please try again.',
}

export function AuthPage({ mode, query, onLogin }: AuthPageProps) {
  const registering = mode === 'register'
  const params = new URLSearchParams(query)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(oauthErrors[params.get('error') || ''] || '')
  const [providers, setProviders] = useState<string[]>([])
  const [providersLoaded, setProvidersLoaded] = useState(false)

  useEffect(() => {
    let active = true
    authApi.providers()
      .then(available => { if (active) setProviders(available) })
      .catch(() => {})
      .finally(() => { if (active) setProvidersLoaded(true) })
    return () => { active = false }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (registering && new TextEncoder().encode(password).length > 72) {
      setError('This password is too long. Use fewer characters, especially symbols or emoji.')
      return
    }
    setBusy(true)
    try {
      if (registering) {
        await authApi.register(email.trim(), password)
        window.location.hash = '#login?registered=1'
      } else {
        onLogin(await authApi.login(email.trim(), password))
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Something went wrong. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main id="main" tabIndex={-1} className="page-shell flex flex-1 items-center justify-center py-10 sm:py-14">
      <section className="w-full max-w-[420px]" aria-labelledby="auth-heading">
        <div className="mb-7 text-center">
          <p className="eyebrow mb-3">Your StatusRobot account</p>
          <h1 id="auth-heading" className="font-heading text-3xl font-bold tracking-tight">{registering ? 'Make yourself at home.' : 'Welcome back.'}</h1>
          <p className="mt-3 text-sm text-muted">{registering ? 'Create an account to start monitoring.' : 'Sign in to manage your monitors.'}</p>
        </div>
        <div className="rounded-2xl border border-line bg-white p-6 sm:p-8">
          {!registering && params.has('registered') && <p role="status" className="mb-5 rounded-lg bg-sage p-3 text-sm leading-6 text-green">Your account is ready. Sign in with your email and password.</p>}
          {error && <p role="alert" className="mb-5 rounded-lg border border-rose-100 bg-rose-50 p-3 text-sm leading-6 text-rose-800">{error}</p>}
          <form onSubmit={submit} className="space-y-5">
            <div>
              <label htmlFor="email" className="mb-2 block text-sm font-medium">Email</label>
              <input id="email" name="email" type="email" autoComplete="email" maxLength={254} required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com" className="form-input" disabled={busy} />
            </div>
            <div>
              <label htmlFor="password" className="mb-2 block text-sm font-medium">Password</label>
              <div className="relative">
                <input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 12 : undefined} maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} aria-describedby={registering ? 'password-help' : undefined} className="form-input pr-12" disabled={busy} />
                <button type="button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-muted hover:text-green">{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button>
              </div>
              {registering && <p id="password-help" className="mt-2 text-xs text-muted">Use at least 12 characters. A few words work well.</p>}
            </div>
            <button type="submit" disabled={busy} className="button button-green w-full disabled:opacity-60">{busy ? (registering ? 'Creating account...' : 'Signing in...') : (registering ? 'Create account' : 'Sign in')}</button>
          </form>
          <div className="my-6 flex items-center gap-3 text-xs text-muted"><span className="h-px flex-1 bg-line" />or continue with<span className="h-px flex-1 bg-line" /></div>
          <div className="grid grid-cols-2 gap-3">
            {['github', 'discord'].map(provider => {
              const label = provider === 'github' ? 'GitHub' : 'Discord'
              return providers.includes(provider) && !busy
                ? <a key={provider} href={authApi.oauthUrl(provider)} className="button button-outline px-3">{label}</a>
                : <button key={provider} type="button" disabled title={providersLoaded ? `${label} sign-in is not available on this server.` : 'Loading sign-in options'} className="button button-outline cursor-not-allowed px-3 opacity-50">{label}</button>
            })}
          </div>
          {providersLoaded && providers.length < 2 && <p className="mt-3 text-center text-xs leading-5 text-muted">{providers.length === 0 ? 'Social sign-in is not configured on this server.' : 'Unavailable sign-in options are disabled.'}</p>}
        </div>
        <p className="mt-6 text-center text-sm text-muted">{registering ? 'Already have an account?' : 'New to StatusRobot?'}{' '}<a href={registering ? '#login' : '#register'} className="font-semibold text-green hover:underline">{registering ? 'Sign in' : 'Create an account'}</a></p>
      </section>
    </main>
  )
}
