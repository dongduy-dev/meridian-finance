import type { LucideIcon } from 'lucide-react'
import { useId, type ReactNode } from 'react'

import { cn } from '@/lib/cn'

export interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description: string
  action?: ReactNode
  className?: string
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  const titleId = useId()

  return (
    <section
      className={cn(
        'flex min-h-64 flex-col items-center justify-center rounded-lg border border-dashed border-border bg-card/65 px-6 py-12 text-center',
        className,
      )}
      aria-labelledby={titleId}
    >
      <div className="mb-5 flex size-12 items-center justify-center rounded-lg bg-selected text-primary">
        <Icon aria-hidden="true" className="size-5" />
      </div>
      <h2 id={titleId} className="text-xl font-semibold text-foreground">
        {title}
      </h2>
      <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">{description}</p>
      {action ? <div className="mt-6">{action}</div> : null}
    </section>
  )
}
