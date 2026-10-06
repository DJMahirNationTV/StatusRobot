import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ExternalLink, Pencil, Pin, Plus, Radio, Trash2 } from 'lucide-react'
import { statusPageApi, statusPageUrl } from '../services/statusPages'
import type { StatusPageData, StatusPageListing } from '../services/statusPages'
import { monitorApi } from '../services/monitors'
import type { Monitor } from '../types/monitor'

export function StatusPagesDashboard() {
  const [listing, setListing] = useState<StatusPageListing | null>(null)
  const [monitors, setMonitors] = useState<Monitor[]>([])
  const [editor, setEditor] = useState<{ page: StatusPageData | null } | null>(
    null
  )
  const [deleting, setDeleting] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([statusPageApi.mine(), monitorApi.list()])
      .then(([pages, items]) => {
        if (active) {
          setListing(pages)
          setMonitors(items)
        }
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
      await statusPageApi.save(editor.page?.id ?? null, {
        name: String(data.get('name')).trim(),
        slug: String(data.get('slug')).trim(),
        description: String(data.get('description')).trim(),
        monitorIds: data.getAll('monitorIds').map(Number),
        pinnedDefault: listing?.canPinDefault
          ? data.has('pinnedDefault')
          : (editor.page?.pinnedDefault ?? false)
      })
      setListing(await statusPageApi.mine())
      setEditor(null)
      setNotice('Status page saved.')
    } catch (exception) {
      setError(message(exception))
    } finally {
      setBusy(false)
    }
  }

  async function change(page: StatusPageData, remove = false) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      if (remove) {
        await statusPageApi.remove(page.id)
        setDeleting(null)
      } else await statusPageApi.pin(page.id, !page.pinnedDefault)
      setListing(await statusPageApi.mine())
      setNotice(
        remove
          ? 'Status page deleted. Its monitors and history are still available.'
          : page.pinnedDefault
            ? 'Default page unpinned. The landing page is back.'
            : 'Default page pinned. It now appears on the homepage.'
      )
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
          <p className="eyebrow mb-2">Your public pages</p>
          <h1 className="dashboard-heading">
            Status pages<span className="text-green">.</span>
          </h1>
          <p className="mt-3 text-sm text-muted">
            A dedicated page for each service, project or audience.
          </p>
        </div>
        <button
          type="button"
          disabled={!listing || busy || editor !== null}
          onClick={() => {
            setEditor({ page: null })
            setDeleting(null)
            setError('')
            setNotice('')
          }}
          className="button button-green px-4 py-2.5 disabled:opacity-50"
        >
          <Plus size={17} />
          New status page
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
          Loading status pages...
        </p>
      )}
      {editor && (
        <section className="dashboard-panel mb-6 max-w-4xl p-5 sm:p-7">
          <h2 className="font-heading text-lg font-bold">
            {editor.page ? 'Edit status page' : 'Create a status page'}
          </h2>
          <form key={editor.page?.id ?? 'new'} onSubmit={save} className="mt-6">
            <fieldset disabled={busy} className="space-y-5">
              <div className="grid gap-5 sm:grid-cols-2">
                <label className="text-sm font-medium">
                  Page name
                  <input
                    name="name"
                    required
                    maxLength={100}
                    defaultValue={editor.page?.name ?? ''}
                    placeholder="Service status"
                    className="form-input mt-2"
                    autoFocus
                  />
                </label>
                <label className="text-sm font-medium">
                  Page address
                  <input
                    name="slug"
                    required
                    minLength={2}
                    maxLength={60}
                    pattern="[a-z0-9]+(-[a-z0-9]+)*"
                    defaultValue={editor.page?.slug ?? ''}
                    placeholder="service-status"
                    className="form-input mt-2"
                  />
                  <span className="mt-2 block text-xs font-normal text-muted">
                    Use lowercase letters, numbers and hyphens. Public link:
                    /#status/your-address
                  </span>
                </label>
              </div>
              <label className="block text-sm font-medium">
                Description
                <textarea
                  name="description"
                  rows={2}
                  maxLength={500}
                  defaultValue={editor.page?.description ?? ''}
                  placeholder="The latest availability of our services."
                  className="form-input mt-2"
                />
              </label>
              <section className="rounded-lg border border-line p-4">
                <h3 className="text-sm font-semibold">Monitors to display</h3>
                <p className="mt-2 text-xs text-muted">
                  Choose which of your monitors appear on this public page.
                </p>
                {monitors.length === 0 ? (
                  <p className="mt-4 text-sm text-muted">
                    Create a monitor in Monitoring first. You can also save an
                    empty page now.
                  </p>
                ) : (
                  <div className="mt-4 max-h-72 divide-y divide-line overflow-y-auto">
                    {monitors.map((monitor) => (
                      <label
                        key={monitor.id}
                        className="flex items-center gap-3 py-3"
                      >
                        <input
                          type="checkbox"
                          name="monitorIds"
                          value={monitor.id}
                          defaultChecked={editor.page?.monitors.some(
                            (item) => item.id === monitor.id
                          )}
                          className="size-4 shrink-0 accent-green"
                        />
                        <span className="min-w-0">
                          <span className="block text-sm font-medium">
                            {monitor.name}
                          </span>
                          <span className="mt-1 block break-all text-xs text-muted">
                            {monitor.url}
                          </span>
                        </span>
                      </label>
                    ))}
                  </div>
                )}
              </section>
              <label className="flex items-start gap-3 rounded-lg bg-sage p-4">
                <input
                  name="pinnedDefault"
                  type="checkbox"
                  defaultChecked={editor.page?.pinnedDefault}
                  disabled={!listing?.canPinDefault}
                  className="mt-0.5 size-4 shrink-0 accent-green"
                />
                <span>
                  <span className="block text-sm font-semibold">
                    Pin Default
                  </span>
                  <span className="mt-1 block text-xs leading-5 text-muted">
                    {listing?.canPinDefault
                      ? 'Show this status page instead of the landing page. Pinning it replaces the previous default.'
                      : 'Only the server owner can replace the homepage.'}
                  </span>
                </span>
              </label>
              <div className="flex flex-wrap gap-3">
                <button type="submit" className="button button-green">
                  {busy ? 'Saving...' : 'Save status page'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEditor(null)
                    setError('')
                  }}
                  className="button button-outline"
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
          aria-label="Your status pages"
          className="dashboard-panel max-w-5xl overflow-hidden"
        >
          {listing.pages.length === 0 ? (
            <div className="px-6 py-14 text-center">
              <Radio size={28} className="mx-auto mb-4 text-green" />
              <h2 className="font-heading text-lg font-bold">
                A home for your service status.
              </h2>
              <p className="mt-2 text-sm text-muted">
                Create a page, select its monitors and share the link.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-line">
              {listing.pages.map((page) => (
                <article key={page.id} className="p-5">
                  <div className="flex flex-wrap items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-3">
                        <h2 className="break-words font-heading font-bold">
                          {page.name}
                        </h2>
                        {page.pinnedDefault && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-sage px-2.5 py-1 text-[11px] font-medium text-green">
                            <Pin size={12} />
                            Default homepage
                          </span>
                        )}
                      </div>
                      <a
                        href={statusPageUrl(page.slug)}
                        className="mt-2 inline-block break-all text-xs text-green hover:underline"
                      >
                        /#status/{page.slug}
                      </a>
                      <p className="mt-2 text-xs text-muted">
                        {page.monitors.length}{' '}
                        {page.monitors.length === 1 ? 'monitor' : 'monitors'}
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <a
                        href={statusPageUrl(page.slug)}
                        className="dashboard-action"
                      >
                        <ExternalLink size={14} />
                        View<span className="sr-only"> {page.name}</span>
                      </a>
                      {listing.canPinDefault && (
                        <button
                          disabled={busy || editor !== null}
                          onClick={() => change(page)}
                          className="dashboard-action"
                        >
                          <Pin size={14} />
                          {page.pinnedDefault ? 'Unpin Default' : 'Pin Default'}
                          <span className="sr-only"> {page.name}</span>
                        </button>
                      )}
                      <button
                        disabled={busy || editor !== null}
                        onClick={() => {
                          setEditor({ page })
                          setDeleting(null)
                          setError('')
                          setNotice('')
                        }}
                        className="dashboard-action"
                      >
                        <Pencil size={14} />
                        Edit<span className="sr-only"> {page.name}</span>
                      </button>
                      <button
                        disabled={busy || editor !== null}
                        onClick={() => setDeleting(page.id)}
                        className="dashboard-action text-rose-700"
                        aria-label={`Delete ${page.name}`}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                  {deleting === page.id && (
                    <div className="mt-4 rounded-lg bg-canvas p-4">
                      <p className="text-sm">
                        Delete {page.name}? Its public link will stop working.
                        {page.pinnedDefault &&
                          ' The homepage will return to the landing page.'}{' '}
                        Your monitors and history will stay.
                      </p>
                      <div className="mt-3 flex flex-wrap gap-3">
                        <button
                          disabled={busy}
                          onClick={() => change(page, true)}
                          className="dashboard-action text-rose-700"
                        >
                          Delete status page
                        </button>
                        <button
                          disabled={busy}
                          onClick={() => setDeleting(null)}
                          className="dashboard-action"
                        >
                          Keep page
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
      <p className="mt-5 text-xs leading-6 text-muted">
        Each page has its own public link. Only one page can be pinned as the
        default homepage.
      </p>
    </>
  )
}

function message(exception: unknown) {
  return exception instanceof Error
    ? exception.message
    : 'Could not save this change.'
}
