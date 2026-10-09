import { useState } from 'react'
import {
  Activity,
  Radio,
  ShieldAlert,
  Wrench,
  Users,
  Cable,
  Menu,
  X,
  ExternalLink,
  ChevronRight
} from 'lucide-react'
import type { AuthUser } from '../services/auth'
import { MonitoringPage } from './MonitoringPage'
import { MonitorEditorPage } from './MonitorEditorPage'
import { IntegrationsPage } from './IntegrationsPage'
import { StatusPagesDashboard } from './StatusPagesDashboard'
import { IncidentsPage, IncidentDetailsPage } from './IncidentsPage'
import { IncidentEditorPage } from './IncidentEditorPage'
import { TeamMembersPage } from './TeamMembersPage'

interface DashboardPageProps {
  user: AuthUser | null
  loading: boolean
  error: string
  route: string
  query: string
}
const navigation = [
  { icon: Activity, label: 'Monitoring', href: '#dashboard/monitoring/' },
  { icon: ShieldAlert, label: 'Incidents', href: '#dashboard/incidents/' },
  { icon: Radio, label: 'Status pages', href: '#dashboard/status-pages/' },
  { icon: Wrench, label: 'Maintenance' },
  { icon: Users, label: 'Team members', href: '#dashboard/team-members/' },
  { icon: Cable, label: 'Integrations & API', href: '#dashboard/integrations/' }
]

export function DashboardPage({
  user,
  loading,
  error,
  route,
  query
}: DashboardPageProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  if (loading || !user)
    return (
      <main id="main" tabIndex={-1} className="page-shell py-16">
        <h1 className="section-heading">
          {loading ? 'Checking your session.' : 'Your monitors, in one place.'}
        </h1>
        <p role={error ? 'alert' : 'status'} className="mt-4 text-muted">
          {error ||
            (loading
              ? 'Just a moment.'
              : 'Sign in to create and manage your monitors.')}
        </p>
        {!loading && (
          <a href="#login" className="button button-green mt-6">
            Sign in
          </a>
        )}
      </main>
    )

  const path = route.replace(/\/$/, '')
  const integrations = path === '#dashboard/integrations'
  const statusPages = path === '#dashboard/status-pages'
  const teamMembers = path === '#dashboard/team-members'
  const requestedWorkspace = Number(new URLSearchParams(query).get('workspace'))
  const workspaceId = Number.isSafeInteger(requestedWorkspace) && requestedWorkspace > 0 ? requestedWorkspace : null
  const incidentDetail = path.match(/^#dashboard\/incidents\/(\d+)$/)
  const newIncident = ['#dashboard/incidents/new', '#dashboard/incidents/create'].includes(path)
  const incidents = path === '#dashboard/incidents' || Boolean(incidentDetail) || newIncident
  const newMonitor = [
    '#dashboard/monitoring/new',
    '#dashboard/monitoring/create'
  ].includes(path)
  const edit = path.match(/^#dashboard\/monitoring\/(\d+)\/edit$/)
  const listing = ['#dashboard', '#dashboard/monitoring'].includes(path)
  const activeHref = integrations ? '#dashboard/integrations/'
    : statusPages ? '#dashboard/status-pages/'
    : teamMembers ? '#dashboard/team-members/'
    : incidents ? '#dashboard/incidents/'
    : listing || newMonitor || edit ? '#dashboard/monitoring/' : null

  return (
    <div className="dashboard min-h-svh bg-canvas text-ink">
      <a
        href="#main"
        onClick={(event) => {
          event.preventDefault()
          document.getElementById('main')?.focus()
        }}
        className="sr-only z-50 rounded bg-sage p-4 focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Skip to content
      </a>
      <div className="flex items-center justify-between border-b border-line bg-panel px-5 py-4 lg:hidden">
        <a
          href="#"
          className="flex items-center gap-2 font-heading font-extrabold"
        >
          <img src="/logo.png" alt="" className="size-7 object-contain" />
          StatusRobot
        </a>
        <button
          type="button"
          onClick={() => setMenuOpen(!menuOpen)}
          aria-expanded={menuOpen}
          aria-controls="dashboard-sidebar"
          aria-label={menuOpen ? 'Close navigation' : 'Open navigation'}
          className="rounded p-2"
        >
          {menuOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>
      <aside
        id="dashboard-sidebar"
        className={`${menuOpen ? 'flex' : 'hidden'} flex-col border-b border-line bg-panel p-5 lg:fixed lg:inset-y-0 lg:flex lg:w-60 lg:border-r lg:border-b-0 lg:p-6`}
      >
        <a
          href="#"
          className="mb-10 hidden items-center gap-2 font-heading text-xl font-extrabold tracking-tight lg:flex"
        >
          <img src="/logo.png" alt="" className="size-8 object-contain" />
          StatusRobot
        </a>
        <p className="mb-3 px-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-muted">
          Workspace
        </p>
        <nav
          aria-label="Dashboard navigation"
          className="space-y-1"
          onClick={() => setMenuOpen(false)}
        >
          {navigation.map(({ icon: Icon, label, href }) =>
            href ? (
              <a
                key={label}
                href={href}
                aria-current={href === activeHref ? 'page' : undefined}
                className={`sidebar-link ${href === activeHref ? 'bg-sage text-green' : 'hover:bg-sage'}`}
              >
                <Icon size={18} />
                {label}
              </a>
            ) : (
              <button
                key={label}
                type="button"
                disabled
                title="Coming later"
                className="sidebar-link w-full cursor-not-allowed text-muted/60"
              >
                <Icon size={18} />
                <span>{label}</span>
                <span className="sr-only"> (coming later)</span>
              </button>
            )
          )}
        </nav>
        <div className="mt-8 border-t border-line pt-5 lg:mt-auto">
          <a
            href="#status"
            className="mb-5 flex items-center gap-2 px-3 text-xs text-muted hover:text-ink"
          >
            <ExternalLink size={14} />
            Public status page
          </a>
          <a
            href="#account"
            className="flex min-w-0 items-center gap-3 rounded-lg p-2 hover:bg-sage"
          >
            <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-sage text-sm font-bold text-green">
              {user.email[0].toUpperCase()}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-xs font-medium">
                {user.email}
              </span>
              <span className="mt-1 block text-[11px] text-muted">
                Your account
              </span>
            </span>
            <ChevronRight size={14} className="shrink-0 text-muted" />
          </a>
        </div>
      </aside>
      <main
        id="main"
        tabIndex={-1}
        className="min-w-0 px-5 py-8 sm:px-8 lg:ml-60 lg:px-10 lg:py-10"
      >
        <div className="mx-auto max-w-[1440px]">
          {integrations ? (
            <IntegrationsPage key={user.id} />
          ) : statusPages ? (
            <StatusPagesDashboard key={user.id} />
          ) : teamMembers ? (
            <TeamMembersPage key={user.id} />
          ) : incidents ? (
            newIncident ? <IncidentEditorPage key={user.id} />
              : incidentDetail ? <IncidentDetailsPage key={`${user.id}-${path}`} incidentId={Number(incidentDetail[1])} />
              : <IncidentsPage key={user.id} />
          ) : newMonitor || edit ? (
            <MonitorEditorPage
              key={`${user.id}-${path}-${workspaceId}`}
              monitorId={edit ? Number(edit[1]) : null}
              workspaceId={workspaceId}
            />
          ) : listing ? (
            <MonitoringPage key={`${user.id}-${workspaceId}`} workspaceId={workspaceId} />
          ) : (
            <>
              <h1 className="dashboard-heading">Page not found.</h1>
              <a
                href="#dashboard/monitoring/"
                className="button button-green mt-6"
              >
                Back to monitoring
              </a>
            </>
          )}
        </div>
      </main>
    </div>
  )
}
