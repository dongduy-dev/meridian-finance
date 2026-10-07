import { Outlet } from 'react-router-dom'

import { MeridianLogo } from '@/components/common/MeridianLogo'

export function AuthLayout() {
  return (
    <main className="min-h-svh bg-background lg:grid lg:grid-cols-[45%_55%]">
      <section className="auth-brand on-navy flex min-w-0 flex-col justify-between gap-16">
        <MeridianLogo variant="wordmark" className="w-36 lg:hidden" />
        <MeridianLogo variant="expanded" className="hidden w-56 lg:block" />
        <div className="hidden max-w-[60ch] space-y-6 lg:block">
          <div className="h-0.5 w-12 bg-accent" aria-hidden="true" />
          <p className="type-editorial">Confidence at every financial step.</p>
          <p className="type-intro">
            From application to repayment, Meridian brings clarity, control, and thoughtful guidance to every stage.
          </p>
        </div>
      </section>
      <section className="auth-form-area flex min-w-0 items-center">
        <div className="auth-form"><Outlet /></div>
      </section>
    </main>
  )
}
