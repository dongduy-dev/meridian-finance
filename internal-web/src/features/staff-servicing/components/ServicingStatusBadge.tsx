import { AlertCircle, CheckCircle2, Circle, Clock3 } from 'lucide-react'
import { cn } from '@/lib/cn'
import { accountStatusLabel, knownAccountStatuses, knownInstallmentStatuses } from '../model/presentation'

export function ServicingStatusBadge({ status }: { status: string }) {
  const known = knownAccountStatuses.has(status) || knownInstallmentStatuses.has(status)
  const treatment = !known || status === 'CLOSED' ? 'neutral'
    : status === 'SETTLED' ? 'success'
      : status === 'OVERDUE' ? 'warning'
        : 'information'
  const Icon = treatment === 'success' ? CheckCircle2
    : treatment === 'warning' ? AlertCircle
      : treatment === 'information' ? Clock3
        : Circle
  return <span className={cn(
    'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold',
    treatment === 'neutral' && 'border-border bg-muted text-muted-foreground',
    treatment === 'success' && 'border-success/25 bg-success-subtle text-success',
    treatment === 'warning' && 'border-warning/25 bg-warning-subtle text-warning',
    treatment === 'information' && 'border-information/25 bg-information-subtle text-information',
  )} title={known ? undefined : status}><Icon aria-hidden="true" className="size-3.5" />{known ? status.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase()) : accountStatusLabel(status)}</span>
}
