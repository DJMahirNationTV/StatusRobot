import type { FormEvent } from 'react'
import { Globe, Cable } from 'lucide-react'
import type { Integration } from '../services/integrations'
import type { Monitor, MonitorInput } from '../types/monitor'

interface MonitorFormProps {
  monitor: Monitor | null
  busy: boolean
  onSave: (input: MonitorInput) => void
  onCancel: () => void
  integrations: Integration[]
  configured: boolean
  selected: number[]
  onRefreshIntegrations: () => void
  shared?: boolean
}

export function MonitorForm({
  monitor,
  busy,
  onSave,
  onCancel,
  integrations,
  configured,
  selected,
  onRefreshIntegrations,
  shared = false
}: MonitorFormProps) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    onSave({
      name: String(data.get('name')).trim(),
      url: String(data.get('url')).trim(),
      httpMethod: data.get('httpMethod') as MonitorInput['httpMethod'],
      intervalSeconds: Number(data.get('intervalSeconds')),
      timeoutSeconds: Number(data.get('timeoutSeconds')),
      integrationIds: data.getAll('integrationIds').map(Number)
    })
  }

  return (
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_200px]">
      <form onSubmit={submit} className="min-w-0">
        <fieldset disabled={busy} className="space-y-5">
          <section id="monitor-details" className="dashboard-panel p-5 sm:p-7">
            <h2 className="flex items-center gap-3 text-sm font-semibold">
              <Globe size={20} className="text-green" />
              HTTP / website monitoring
            </h2>
            <p className="mt-2 text-xs leading-6 text-muted">
              Check the availability of a website or API endpoint.
            </p>
            <div className="mt-6 grid gap-5 border-t border-line pt-6 sm:grid-cols-2">
              <label className="text-sm font-medium">
                Name
                <input
                  name="name"
                  required
                  maxLength={100}
                  defaultValue={monitor?.name || ''}
                  placeholder="My website"
                  autoFocus
                  className="form-input mt-2"
                />
              </label>
              <label className="text-sm font-medium">
                URL
                <input
                  name="url"
                  type="url"
                  required
                  pattern="https?://.*"
                  maxLength={255}
                  defaultValue={monitor?.url || ''}
                  placeholder="https://example.com"
                  className="form-input mt-2"
                />
              </label>
              <label className="text-sm font-medium">
                HTTP method
                <select
                  name="httpMethod"
                  defaultValue={monitor?.httpMethod || 'GET'}
                  className="form-input mt-2"
                >
                  <option value="GET">GET</option>
                  <option value="HEAD">HEAD</option>
                  <option value="POST">POST</option>
                </select>
              </label>
              <div className="grid grid-cols-2 gap-4">
                <label className="text-sm font-medium">
                  Interval (seconds)
                  <input
                    name="intervalSeconds"
                    type="number"
                    min={10}
                    max={86400}
                    step={1}
                    required
                    defaultValue={monitor?.intervalSeconds ?? 60}
                    className="form-input mt-2"
                  />
                </label>
                <label className="text-sm font-medium">
                  Timeout (seconds)
                  <input
                    name="timeoutSeconds"
                    type="number"
                    min={1}
                    max={30}
                    step={1}
                    required
                    defaultValue={monitor?.timeoutSeconds ?? 5}
                    className="form-input mt-2"
                  />
                </label>
              </div>
              <p className="text-xs leading-5 text-muted sm:col-span-2">
                Checks run on a 10-second polling cycle. Timeout must not exceed
                the interval. Use GET or HEAD for read-only checks. POST can
                change the target service.
              </p>
            </div>
          </section>
          <section
            id="monitor-integrations"
            className="dashboard-panel p-5 sm:p-7"
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="flex items-center gap-3 text-sm font-semibold">
                <Cable size={20} className="text-green" />
                Connect integrations
              </h2>
              {!shared && <a
                href="#dashboard/integrations/"
                target="_blank"
                rel="noopener noreferrer"
                className="dashboard-action"
              >
                Manage integrations (new tab)
              </a>}
            </div>
            <p className="mt-3 text-xs leading-6 text-muted">
              Select the Discord channels that should receive outage and
              recovery alerts.
              {shared && ' These channels belong to the workspace owner. Only the owner can manage them.'}
            </p>
            <button
              type="button"
              onClick={onRefreshIntegrations}
              className="mt-3 rounded text-xs font-semibold text-green hover:underline"
            >
              Refresh integrations
            </button>
            {!configured && (
              <p className="mt-4 text-xs text-amber-800">
                The server owner needs to configure Discord integrations before
                alerts can be sent.
              </p>
            )}
            {integrations.length === 0 ? (
              <p className="mt-5 rounded-lg border border-dashed border-line p-5 text-sm text-muted">
                No Discord integrations yet. You can create the monitor now and
                connect one later.
              </p>
            ) : (
              <div className="mt-5 divide-y divide-line border-y border-line">
                {integrations.map((integration) => (
                  <label
                    key={integration.id}
                    className="flex items-center gap-3 py-4 text-sm"
                  >
                    <input
                      type="checkbox"
                      name="integrationIds"
                      value={integration.id}
                      defaultChecked={selected.includes(integration.id)}
                      className="size-4 accent-green"
                    />
                    <span>{integration.name}</span>
                    <span className="ml-auto text-xs text-muted">Discord</span>
                  </label>
                ))}
              </div>
            )}
          </section>
          <p className="text-xs leading-6 text-muted">
            Monitor details and check history are public. Do not put credentials
            in the URL. Discord webhook URLs stay private.
          </p>
          <div className="flex flex-wrap gap-3 border-t border-line pt-5">
            <button
              type="submit"
              className="button button-green disabled:opacity-50"
            >
              {busy ? 'Saving...' : monitor ? 'Save changes' : 'Create monitor'}
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="button button-outline"
            >
              Cancel
            </button>
          </div>
        </fieldset>
      </form>
      <nav
        aria-label="Monitor form sections"
        className="hidden space-y-3 xl:block"
      >
        <button
          type="button"
          onClick={() =>
            document.getElementById('monitor-details')?.scrollIntoView()
          }
          className="block rounded p-2 text-xs font-semibold text-green"
        >
          Monitor details
        </button>
        <button
          type="button"
          onClick={() =>
            document.getElementById('monitor-integrations')?.scrollIntoView()
          }
          className="block rounded p-2 text-xs text-muted hover:text-green"
        >
          Integrations
        </button>
      </nav>
    </div>
  )
}
