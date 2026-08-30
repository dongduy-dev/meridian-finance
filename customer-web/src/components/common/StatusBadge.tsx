import { CircleHelp } from 'lucide-react'

import { cn } from '@/lib/cn'
import type { StatusPresentation, StatusTone } from './status-presentation'

const toneClasses: Record<StatusTone, string> = {
  neutral: 'bg-muted text-muted-foreground',
  information: 'bg-information-subtle text-information',
  warning: 'bg-warning-subtle text-warning',
  success: 'bg-success-subtle text-success',
  danger: 'bg-danger-subtle text-danger',
}

export function StatusBadge({
  presentation,
  className,
}: {
  presentation: StatusPresentation
  className?: string
}) {
  const Icon = presentation.icon ?? CircleHelp
  return (
    <span
      className={cn(
        'inline-flex max-w-full min-w-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs leading-5 font-semibold whitespace-normal',
        toneClasses[presentation.tone],
        className,
      )}
    >
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      <span className="min-w-0 break-words">{presentation.label}</span>
    </span>
  )
}
