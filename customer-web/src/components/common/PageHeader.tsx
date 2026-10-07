import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

export interface PageHeaderProps {
  title: string
  description?: string
  eyebrow?: string
  actions?: ReactNode
  className?: string
  headingRole?: 'editorial' | 'browse' | 'transactional'
}

export function PageHeader({
  title,
  description,
  eyebrow,
  actions,
  className,
  headingRole = 'transactional',
}: PageHeaderProps) {
  return (
    <header className={cn('flex min-w-0 flex-col gap-6 sm:flex-row sm:items-end sm:justify-between', className)}>
      <div className="min-w-0 space-y-2 [overflow-wrap:anywhere]">
        {eyebrow ? (
          <p className="type-eyebrow text-muted-foreground">
            {eyebrow}
          </p>
        ) : null}
        <h1
          id="page-heading"
          tabIndex={-1}
          className={cn('text-foreground', { 'type-editorial': headingRole === 'editorial', 'type-browse': headingRole === 'browse', 'type-transactional': headingRole === 'transactional' })}
        >
          {title}
        </h1>
        {description ? (
          <p className="max-w-[70ch] text-base leading-6 text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {actions ? <div className="flex min-w-0 flex-wrap gap-3">{actions}</div> : null}
    </header>
  )
}
