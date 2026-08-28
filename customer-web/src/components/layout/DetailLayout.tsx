import type { ReactNode } from 'react'

export interface DetailLayoutProps {
  header: ReactNode
  children: ReactNode
  rail?: ReactNode
}

export function DetailLayout({ header, children, rail }: DetailLayoutProps) {
  return (
    <main className="min-h-svh bg-background">
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-12 lg:px-8">
        {header}
        <div className="mt-8 grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1fr)_20rem] xl:items-start">
          <section className="min-w-0">{children}</section>
          {rail ? <aside className="order-first xl:order-none">{rail}</aside> : null}
        </div>
      </div>
    </main>
  )
}
