import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Cable, Plus, Pencil, Send, Trash2 } from 'lucide-react'
import { integrationApi } from '../services/integrations'
import type { Integration, IntegrationListing } from '../services/integrations'

export function IntegrationsPage() {
  const [listing, setListing] = useState<IntegrationListing | null>(null)
  const [editor, setEditor] = useState<{
    integration: Integration | null
  } | null>(null)
  const [deleting, setDeleting] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    integrationApi
      .list()
      .then((result) => {
        if (active) setListing(result)
      })
      .catch((exception) => {
        if (active) setError(exception.message)
      })
    return () => {
      active = false
    }
  }, [attempt])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editor || busy) return
    const data = new FormData(event.currentTarget)
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const saved = await integrationApi.save(
        editor.integration?.id ?? null,
        String(data.get('name')).trim(),
        String(data.get('webhookUrl')).trim()
      )
      setListing(
        (current) =>
          current && {
            ...current,
            integrations: editor.integration
              ? current.integrations.map((item) =>
                  item.id === saved.id ? saved : item
                )
              : [saved, ...current.integrations]
          }
      )
      setEditor(null)
      setNotice(
        'Integration saved. Select it in your monitor settings to receive alerts.'
      )
    } catch (exception) {
      setError(message(exception))
    } finally {
      setBusy(false)
    }
  }

  async function action(integration: Integration, remove: boolean) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      if (remove) {
        await integrationApi.remove(integration.id)
        setListing(
          (current) =>
            current && {
              ...current,
              integrations: current.integrations.filter(
                (item) => item.id !== integration.id
              )
            }
        )
        setDeleting(null)
        setNotice(
          'Integration removed from StatusRobot and its monitors. The webhook in Discord is unchanged.'
        )
      } else {
        await integrationApi.test(integration.id)
        setNotice('Test message sent. Check your Discord channel.')
      }
    } catch (exception) {
      setError(message(exception))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow mb-2">Connections</p>
          <h1 className="dashboard-heading">
            Integrations & API<span className="text-green">.</span>
          </h1>
          <p className="mt-3 text-sm text-muted">
            Your alerts, delivered to Discord.
          </p>
        </div>
        <button
          type="button"
          disabled={busy || !listing?.configured || editor !== null}
          onClick={() => {
            setEditor({ integration: null })
            setError('')
            setNotice('')
            setDeleting(null)
          }}
          className="button button-green px-4 py-2.5 disabled:opacity-50"
        >
          <Plus size={16} />
          Add Discord webhook
        </button>
      </header>
      {error && (
        <p role="alert" className="dashboard-error mb-5">
          {error}
          {!listing && (
            <button
              onClick={() => {
                setError('')
                setAttempt((value) => value + 1)
              }}
              className="ml-3 underline"
            >
              Retry
            </button>
          )}
        </p>
      )}
      {notice && (
        <p
          role="status"
          className="mb-5 rounded-lg bg-sage p-4 text-sm text-green"
        >
          {notice}
        </p>
      )}
      {!listing && !error && (
        <p role="status" className="text-sm text-muted">
          Loading...
        </p>
      )}
      {listing && !listing.configured && (
        <p className="mb-5 rounded-lg border border-line bg-panel p-4 text-sm leading-6 text-amber-800">
          Discord connections are not enabled on this server yet. Contact the
          server owner to enable them.
        </p>
      )}
      {editor && (
        <section className="dashboard-panel mb-6 max-w-3xl p-5 sm:p-7">
          <h2 className="text-lg font-semibold">
            {editor.integration
              ? 'Edit Discord integration'
              : 'Connect a Discord channel'}
          </h2>
          <p className="mt-2 text-xs leading-6 text-muted">
            In Discord, open your server settings, then Integrations, Webhooks
            and Copy Webhook URL.
          </p>
          <form
            key={editor.integration?.id ?? 'new'}
            onSubmit={save}
            className="mt-6"
          >
            <fieldset disabled={busy} className="space-y-5">
              <label className="block text-sm font-medium">
                Name
                <input
                  name="name"
                  required
                  maxLength={80}
                  defaultValue={editor.integration?.name || ''}
                  placeholder="Operations alerts"
                  autoFocus
                  className="form-input mt-2"
                />
              </label>
              <label className="block text-sm font-medium">
                Discord webhook URL
                <input
                  name="webhookUrl"
                  type="password"
                  autoComplete="new-password"
                  required={!editor.integration}
                  maxLength={300}
                  placeholder={
                    editor.integration
                      ? 'Leave blank to keep the saved webhook'
                      : 'https://discord.com/api/webhooks/...'
                  }
                  className="form-input mt-2"
                />
              </label>
              <p className="text-xs text-muted">
                Stored encrypted. Saved webhook URLs are never shown again.
              </p>
              <div className="flex flex-wrap gap-3">
                <button className="button button-green py-3" type="submit">
                  {busy ? 'Saving...' : 'Save integration'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEditor(null)
                    setError('')
                  }}
                  className="button button-outline py-3"
                >
                  Cancel
                </button>
              </div>
            </fieldset>
          </form>
        </section>
      )}
      {listing && (
        <section
          aria-label="Discord integrations"
          className="dashboard-panel max-w-5xl overflow-hidden"
        >
          <div className="flex items-center gap-3 border-b border-line p-5">
            <span className="rounded-lg bg-sage p-2 text-green">
              <Cable size={20} />
            </span>
            <div>
              <h2 className="text-sm font-semibold">Discord webhooks</h2>
              <p className="mt-1 text-xs text-muted">
                {listing.integrations.length} connected
              </p>
            </div>
          </div>
          {listing.integrations.length === 0 ? (
            <div className="p-10 text-center">
              <h3 className="text-sm font-semibold">
                No channels connected yet.
              </h3>
              <p className="mt-2 text-sm text-muted">
                Add a webhook, then choose it when creating or editing a
                monitor.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-line">
              {listing.integrations.map((integration) => (
                <article key={integration.id} className="p-5">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div>
                      <h3 className="break-all text-sm font-semibold">
                        {integration.name}
                      </h3>
                      <p className="mt-1 text-xs text-muted">Discord webhook</p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <button
                        disabled={
                          busy || editor !== null || !listing.configured
                        }
                        onClick={() => action(integration, false)}
                        className="dashboard-action"
                      >
                        <Send size={14} />
                        Send test
                        <span className="sr-only"> to {integration.name}</span>
                      </button>
                      <button
                        disabled={busy || editor !== null}
                        onClick={() => {
                          setEditor({ integration })
                          setDeleting(null)
                          setError('')
                          setNotice('')
                        }}
                        className="dashboard-action"
                      >
                        <Pencil size={14} />
                        Edit<span className="sr-only"> {integration.name}</span>
                      </button>
                      <button
                        disabled={busy || editor !== null}
                        onClick={() => setDeleting(integration.id)}
                        className="dashboard-action text-rose-700"
                        aria-label={`Delete ${integration.name}`}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                  {deleting === integration.id && (
                    <div className="mt-4 rounded-lg bg-canvas p-4">
                      <p className="text-sm">
                        Remove {integration.name} from all your monitors? This
                        does not delete the webhook in Discord.
                      </p>
                      <div className="mt-3 flex gap-3">
                        <button
                          disabled={busy}
                          onClick={() => action(integration, true)}
                          className="dashboard-action text-rose-700"
                        >
                          Remove integration
                        </button>
                        <button
                          disabled={busy}
                          onClick={() => setDeleting(null)}
                          className="dashboard-action"
                        >
                          Keep integration
                        </button>
                      </div>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      )}
      <p className="mt-5 text-xs text-muted">
        Discord is available for now only.
      </p>
    </>
  )
}

function message(exception: unknown) {
  return exception instanceof Error
    ? exception.message
    : 'Could not complete this request.'
}
