import { useEffect, useState } from 'react'
import { incidentApi } from '../services/incidents'

export function IncidentHelp({ incidentId, onUseDraft }: { incidentId: number, onUseDraft: (text: string) => void }) {
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [context, setContext] = useState('')
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<{ text: string, model: string } | null>(null)
  const [error, setError] = useState('')
  const [draftAdded, setDraftAdded] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    incidentApi.analysisSettings().then(settings => { if (active) setEnabled(settings.enabled) })
      .catch(exception => { if (active) setError(exception.message) })
    return () => { active = false }
  }, [attempt])

  async function analyze(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!consent || busy || !enabled) return
    setBusy(true)
    setError('')
    setResult(null)
    setDraftAdded(false)
    try {
      setResult(await incidentApi.analyze(incidentId, context.trim()))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Could not analyze this incident.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="Incident help" className="dashboard-panel mt-6 space-y-4 p-6">
      <h2 className="text-sm font-semibold">Incident help</h2>
      <p className="text-xs leading-6 text-muted">OpenAI can suggest possible causes and checks to try. It cannot inspect your server or confirm the cause. Suggestions stay private and do not change your monitors.</p>
      {enabled === null && !error && <p role="status" className="text-xs text-muted">Checking availability...</p>}
      {enabled === false && <p className="rounded-lg bg-sage p-4 text-xs leading-6 text-muted">Not configured. The server owner can enable this with OPENAI_API_KEY. Incidents work without it.</p>}
      {error && <p role="alert" className="dashboard-error">{error}
        {enabled === null && <button className="ml-3 underline" onClick={() => { setError(''); setAttempt(value => value + 1) }}>Retry</button>}
      </p>}
      {enabled && <form onSubmit={analyze} className="space-y-4">
        <fieldset disabled={busy} className="space-y-4">
          <label className="block text-xs font-medium">Optional context
            <textarea maxLength={2000} rows={3} value={context} onChange={event => setContext(event.target.value)} className="form-input mt-2 w-full" placeholder="For example, requests started failing after a deployment." />
          </label>
          <p className="text-xs leading-6 text-muted">Sends the HTTP check result, incident progress, start and recovery times, and context entered above to OpenAI. Monitor names, URLs and saved update messages are not sent. Do not enter secrets or personal details. Requests may incur API costs.</p>
          <label className="flex items-start gap-3 text-xs leading-5"><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} className="mt-1 accent-green" />I agree to send these details to OpenAI for this analysis.</label>
        </fieldset>
        <button disabled={busy || !consent} className="dashboard-action">{busy ? 'Analyzing...' : 'Ask OpenAI'}</button>
        <p className="text-xs text-muted">Up to 5 requests per account per hour.</p>
      </form>}
      {result && <div className="rounded-lg border border-line bg-sage p-4">
        <h3 className="text-xs font-semibold">Suggestions to review</h3>
        <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6">{result.text}</p>
        <p className="mt-4 text-xs leading-6 text-muted">Generated with {result.model}. Check these suggestions before using them. This result is not saved.</p>
        <button type="button" disabled={result.text.length > 3000} className="dashboard-action mt-3" onClick={() => {
          onUseDraft(result.text)
          setDraftAdded(true)
        }}>Use as update draft</button>
        {result.text.length > 3000 && <p className="mt-2 text-xs text-muted">Shorten this result before using it in an update. Updates allow 3000 characters.</p>}
        {draftAdded && <p role="status" className="mt-2 text-xs text-green">Added to the message above. Review it before saving. Nothing has been published.</p>}
      </div>}
    </section>
  )
}
