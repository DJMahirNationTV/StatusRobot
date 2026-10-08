import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Users, UserPlus, RefreshCw } from 'lucide-react'
import { teamApi } from '../services/teams'
import type { TeamListing, TeamRole } from '../services/teams'

export function TeamMembersPage() {
  const [listing, setListing] = useState<TeamListing | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [removing, setRemoving] = useState<{ id: number; label: string } | null>(null)

  useEffect(() => {
    let active = true
    teamApi.list()
      .then(result => { if (active) setListing(result) })
      .catch(exception => { if (active) setError(exception.message) })
    return () => { active = false }
  }, [attempt])

  async function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const form = event.currentTarget
    const input = new FormData(form)
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await teamApi.invite(String(input.get('email')).trim(), input.get('role') as TeamRole)
      setListing(await teamApi.list())
      form.reset()
      setNotice('Invitation sent. They can accept it in their Team members tab.')
    } catch (exception) {
      setError(message(exception))
    } finally {
      setBusy(false)
    }
  }

  async function change(id: number, action: 'accept' | 'remove' | TeamRole) {
    if (busy) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      if (action === 'accept') await teamApi.accept(id)
      else if (action === 'remove') await teamApi.remove(id)
      else await teamApi.setRole(id, action)
      // Reload the saved roles rather than guessing what changed on the server.
      setListing(await teamApi.list())
      setRemoving(null)
      setNotice(action === 'accept' ? 'Invitation accepted. Open Monitoring to choose the workspace.'
        : action === 'remove' ? 'Team access removed. No monitors were deleted.' : 'Role updated.')
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
          <p className="eyebrow mb-2">Workspace</p>
          <h1 className="dashboard-heading">Team members<span className="text-green">.</span></h1>
          <p className="mt-3 text-sm text-muted">Share your monitors with the people you work with.</p>
        </div>
        <button disabled={busy} onClick={() => { setError(''); setAttempt(value => value + 1) }}
          className="dashboard-action"><RefreshCw size={15} /> Refresh</button>
      </header>
      {error && <p role="alert" className="dashboard-error mb-5">{error}</p>}
      {notice && <p role="status" className="mb-5 rounded-lg bg-sage p-4 text-sm text-green">{notice}</p>}
      {!listing && !error && <p role="status" className="text-sm text-muted">Loading team members...</p>}
      {removing && (
        <section aria-label="Confirm removal" className="dashboard-panel mb-6 max-w-5xl p-5">
          <p className="text-sm">{removing.label}</p>
          <p className="mt-2 text-xs text-muted">Access ends immediately. The owner keeps their monitors and history.</p>
          <div className="mt-4 flex gap-3">
            <button disabled={busy} onClick={() => change(removing.id, 'remove')} className="dashboard-action text-rose-700">Confirm removal</button>
            <button disabled={busy} onClick={() => setRemoving(null)} className="dashboard-action">Cancel</button>
          </div>
        </section>
      )}
      {listing && (
        <div className="max-w-5xl space-y-6">
          {listing.invitations.length > 0 && (
            <section className="dashboard-panel p-5 sm:p-7" aria-label="Your invitations">
              <h2 className="text-base font-semibold">Your invitations</h2>
              <div className="mt-4 divide-y divide-line">
                {listing.invitations.map(invitation => (
                  <article key={invitation.id} className="flex flex-wrap items-center justify-between gap-4 py-4">
                    <div className="min-w-0">
                      <h3 className="break-all text-sm font-medium">{invitation.email}</h3>
                      <p className="mt-1 text-xs text-muted">Invited you as {invitation.role === 'EDITOR' ? 'an Editor' : 'a Viewer'}.</p>
                    </div>
                    <div className="flex gap-2">
                      <button disabled={busy} onClick={() => change(invitation.id, 'accept')} className="button button-green px-4 py-2">Accept<span className="sr-only"> invitation from {invitation.email}</span></button>
                      <button disabled={busy} onClick={() => setRemoving({ id: invitation.id, label: `Decline the invitation from ${invitation.email}?` })} className="dashboard-action">Decline<span className="sr-only"> invitation from {invitation.email}</span></button>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )}
          <section className="dashboard-panel p-5 sm:p-7" aria-label="Invite a member">
            <h2 className="flex items-center gap-2 text-base font-semibold"><UserPlus size={19} className="text-green" /> Invite a member</h2>
            <p className="mt-2 text-xs leading-6 text-muted">They need an existing StatusRobot account. Invitations appear in their dashboard, not by email.</p>
            <form onSubmit={invite} className="mt-5">
              <fieldset disabled={busy} className="flex flex-wrap items-end gap-4">
                <label className="min-w-0 flex-1 basis-64 text-sm font-medium">Email
                  <input name="email" type="email" autoComplete="email" required maxLength={254} placeholder="teammate@example.com" className="form-input mt-2" />
                </label>
                <label className="text-sm font-medium">Role
                  <select name="role" defaultValue="VIEWER" className="form-input mt-2"><option value="VIEWER">Viewer</option><option value="EDITOR">Editor</option></select>
                </label>
                <button className="button button-green py-3" type="submit">{busy ? 'Please wait...' : 'Send invitation'}</button>
              </fieldset>
            </form>
            <p className="mt-4 text-xs leading-6 text-muted">Viewers can browse your monitors. Editors can create, edit, pause and delete monitors, including their history. Only you can manage the team.</p>
          </section>
          <section className="dashboard-panel overflow-hidden" aria-label="Your team">
            <div className="flex items-center gap-3 border-b border-line p-5">
              <span className="rounded-lg bg-sage p-2 text-green"><Users size={20} /></span>
              <div><h2 className="text-sm font-semibold">Your team</h2><p className="mt-1 text-xs text-muted">{listing.members.length} {listing.members.length === 1 ? 'member or pending invitation' : 'members and pending invitations'}</p></div>
            </div>
            {listing.members.length === 0 && <p className="p-10 text-center text-sm text-muted">No members yet. Invite someone to share your monitors.</p>}
            <div className="divide-y divide-line">
              {listing.members.map(member => (
                <article key={member.id} className="flex flex-wrap items-center justify-between gap-4 p-5">
                  <div className="min-w-0"><h3 className="break-all text-sm font-medium">{member.email}</h3><p className={`mt-1 text-xs ${member.accepted ? 'text-green' : 'text-muted'}`}>{member.accepted ? 'Active member' : 'Waiting for acceptance'}</p></div>
                  <div className="flex flex-wrap items-center gap-3">
                    <label><span className="sr-only">Role for {member.email}</span><select disabled={busy} value={member.role} onChange={event => change(member.id, event.target.value as TeamRole)} className="form-input py-2 text-xs"><option value="VIEWER">Viewer</option><option value="EDITOR">Editor</option></select></label>
                    <button disabled={busy} onClick={() => setRemoving({ id: member.id, label: `Remove ${member.email} from your team?` })} className="dashboard-action text-rose-700">{member.accepted ? 'Remove' : 'Cancel invitation'}<span className="sr-only"> for {member.email}</span></button>
                  </div>
                </article>
              ))}
            </div>
          </section>
          <section className="dashboard-panel p-5 sm:p-7" aria-label="Joined workspaces">
            <h2 className="text-base font-semibold">Workspaces you joined</h2>
            {listing.workspaces.length === 1 && <p className="mt-4 text-sm text-muted">You have not joined another workspace yet.</p>}
            <div className="mt-3 divide-y divide-line">
              {listing.workspaces.filter(workspace => workspace.membershipId !== null).map(workspace => (
                <article key={workspace.id} className="flex flex-wrap items-center justify-between gap-4 py-4">
                  <div className="min-w-0"><h3 className="break-all text-sm font-medium">{workspace.email}</h3><p className="mt-1 text-xs text-muted">{workspace.role === 'EDITOR' ? 'Editor' : 'Viewer'}</p></div>
                  <div className="flex gap-2">
                    <a href={`#dashboard/monitoring/?workspace=${workspace.id}`} className="dashboard-action">View monitors</a>
                    <button disabled={busy} onClick={() => setRemoving({ id: workspace.membershipId!, label: `Leave the workspace of ${workspace.email}?` })} className="dashboard-action text-rose-700">Leave<span className="sr-only"> {workspace.email}</span></button>
                  </div>
                </article>
              ))}
            </div>
          </section>
          <p className="text-xs leading-6 text-muted">Sharing only applies to monitors. Status page and incident management, account settings and integration management remain owner-only. Removing a member does not delete their account or any monitors.</p>
        </div>
      )}
    </>
  )
}

function message(exception: unknown) {
  return exception instanceof Error ? exception.message : 'Could not update the team.'
}
