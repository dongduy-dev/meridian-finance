import { Outlet } from 'react-router-dom'

import { MeridianLogo } from '@/components/common/MeridianLogo'

export function AuthLayout() {
  return (
    <main className="grid min-h-svh bg-background lg:grid-cols-[minmax(22rem,0.9fr)_minmax(32rem,1.1fr)]">
      <section className="relative hidden overflow-hidden bg-primary p-10 text-primary-foreground lg:flex lg:flex-col lg:justify-between xl:p-14">
        <div className="absolute top-0 right-0 h-56 w-56 rounded-full bg-accent/10 blur-3xl" aria-hidden="true" />
        <MeridianLogo variant="expanded" className="relative w-56 xl:w-64" />
        <div className="relative max-w-md space-y-4 pb-5">
          <div className="h-1 w-14 rounded-full bg-accent" aria-hidden="true" />
          <p className="text-3xl leading-tight font-semibold tracking-[-0.025em]">
            Clear steps for important financial moments.
          </p>
          <p className="text-sm leading-6 text-primary-foreground/72">
            Meridian Customer Web is designed to keep every action calm, legible, and
            grounded in authoritative platform state.
          </p>
        </div>
      </section>

      <section className="flex min-h-svh items-center justify-center px-4 py-10 sm:px-8 lg:px-12">
        <div className="w-full max-w-md">
          <div className="mb-8 flex justify-center lg:hidden">
            <MeridianLogo variant="primary" className="w-36" />
          </div>
          <Outlet />
        </div>
      </section>
    </main>
  )
}
