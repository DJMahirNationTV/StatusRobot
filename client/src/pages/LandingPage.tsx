interface LandingPageProps {
  repositoryUrl: string
}

export function LandingPage({ repositoryUrl }: LandingPageProps) {
  return (
    <main id="main" tabIndex={-1} className="page-shell flex flex-1 items-center justify-center py-16 sm:py-20">
      <section className="w-full max-w-2xl text-center" aria-labelledby="landing-heading">
        <p className="eyebrow mb-6">Open source uptime monitoring</p>
        <h1 id="landing-heading" className="font-heading text-[clamp(36px,7vw,64px)] leading-[1.1] font-extrabold tracking-[-1.8px] sm:tracking-[-2.8px]">
          Your uptime.<br />
          <span className="text-green">Your infrastructure.</span>
        </h1>
        <p className="mx-auto mt-6 max-w-lg text-base leading-7 text-muted">
          A simple, self-hosted home for your website and API status.
          Keep track of uptime and response times. Clone the repo and make it yours.
        </p>
        <div className="mt-8 flex flex-col justify-center gap-3 min-[420px]:flex-row">
          <a href={repositoryUrl} className="button button-green">View on GitHub</a>
          <a href="#status" className="button button-outline">View live status</a>
        </div>
      </section>
    </main>
  )
}
