import { StatusPage } from './pages/StatusPage'

function App() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col font-sans antialiased selection:bg-emerald-500/20 selection:text-emerald-300">
      {/* Navigation Header */}
      <header className="border-b border-zinc-800/80 bg-zinc-900/40 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-4xl mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
            </div>
            <span className="font-semibold text-base tracking-tight text-white">StatusRobot</span>
          </div>

          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 bg-zinc-900 border border-zinc-800 text-zinc-300 text-xs px-2.5 py-1 rounded-full font-mono">
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
              Live Telemetry
            </span>
          </div>
        </div>
      </header>

      {/* Main Monitoring View */}
      <div className="flex-1">
        <StatusPage />
      </div>

      {/* Footer */}
      <footer className="border-t border-zinc-900 py-6 text-center text-xs text-zinc-500">
        <p>StatusRobot · Automated Health Checks & Telemetry</p>
      </footer>
    </div>
  )
}

export default App