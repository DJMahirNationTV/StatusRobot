import type { FormEvent } from 'react'
import type { Monitor, MonitorInput } from '../types/monitor'

interface MonitorFormProps {
  monitor: Monitor | null
  busy: boolean
  onSave: (input: MonitorInput) => void
  onCancel: () => void
}

export function MonitorForm({ monitor, busy, onSave, onCancel }: MonitorFormProps) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    onSave({
      name: String(data.get('name')).trim(),
      url: String(data.get('url')).trim(),
      httpMethod: data.get('httpMethod') as MonitorInput['httpMethod'],
      intervalSeconds: Number(data.get('intervalSeconds')),
      timeoutSeconds: Number(data.get('timeoutSeconds')),
    })
  }

  return (
    <section aria-labelledby="monitor-form-title" className="mb-8 rounded-xl border border-line bg-white p-5 sm:p-7">
      <h2 id="monitor-form-title" className="font-heading text-xl font-bold">{monitor ? 'Edit monitor' : 'Add a monitor'}</h2>
      <p className="mt-2 text-sm text-muted">Monitor details and check history are visible on the public status page.</p>
      <form onSubmit={submit} className="mt-6">
        <fieldset disabled={busy} className="grid gap-5 sm:grid-cols-2">
          <label className="text-sm font-medium">Name
            <input name="name" required maxLength={100} defaultValue={monitor?.name || ''} placeholder="My website" autoFocus className="form-input mt-2" />
          </label>
          <label className="text-sm font-medium">URL
            <input name="url" type="url" required pattern="https?://.*" maxLength={255} defaultValue={monitor?.url || ''} placeholder="https://example.com" className="form-input mt-2" />
          </label>
          <label className="text-sm font-medium">HTTP method
            <select name="httpMethod" defaultValue={monitor?.httpMethod || 'GET'} className="form-input mt-2">
              <option value="GET">GET</option><option value="HEAD">HEAD</option><option value="POST">POST</option>
            </select>
          </label>
          <div className="grid grid-cols-2 gap-4">
            <label className="text-sm font-medium">Interval (seconds)
              <input name="intervalSeconds" type="number" min={10} max={86400} step={1} required defaultValue={monitor?.intervalSeconds ?? 60} className="form-input mt-2" />
            </label>
            <label className="text-sm font-medium">Timeout (seconds)
              <input name="timeoutSeconds" type="number" min={1} max={30} step={1} required defaultValue={monitor?.timeoutSeconds ?? 5} className="form-input mt-2" />
            </label>
          </div>
          <p className="text-xs leading-5 text-muted sm:col-span-2">Checks run on a 10-second polling cycle. Timeout must not exceed the interval. Use GET or HEAD for read-only checks; POST can change the target service.</p>
          <div className="flex flex-wrap gap-3 sm:col-span-2">
            <button type="submit" className="button button-green disabled:opacity-50">{busy ? 'Saving...' : monitor ? 'Save changes' : 'Create monitor'}</button>
            <button type="button" onClick={onCancel} className="button button-outline">Cancel</button>
          </div>
        </fieldset>
      </form>
    </section>
  )
}
