import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

export interface DetailLayoutProps {
  header: ReactNode
  children: ReactNode
  rail?: ReactNode
}

export function DetailLayout({ header, children, rail }: DetailLayoutProps) {
  return (
    <main className="min-h-svh bg-background">
      <div className="page-container detail-container py-8 md:py-12">
        {header}
        <div className={cn('detail-regions', rail && 'detail-regions-with-rail')}>
          {rail ? <aside>{rail}</aside> : null}
          <section className="min-w-0">{children}</section>
        </div>
      </div>
    </main>
  )
}
