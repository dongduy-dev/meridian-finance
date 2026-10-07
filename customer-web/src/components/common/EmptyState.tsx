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
        'min-w-0 border-t border-border py-6 text-left [overflow-wrap:anywhere]',
        className,
      )}
      aria-labelledby={titleId}
    >
      <div className="mb-4 text-primary">
        <Icon aria-hidden="true" className="size-5" />
      </div>
      <h2 id={titleId} className="type-section text-foreground">
        {title}
      </h2>
      <p className="mt-2 max-w-[70ch] text-base leading-6 text-muted-foreground">{description}</p>
      {action ? <div className="mt-6">{action}</div> : null}
    </section>
  )
}
