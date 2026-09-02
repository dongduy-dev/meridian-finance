import { AlertCircle, CheckCircle2, Circle, Clock3, XCircle } from 'lucide-react'
import { cn } from '@/lib/cn'
import { applicationStatusLabel } from '../model/presentation'

const successStatuses = new Set(['APPROVED', 'DISBURSED'])
const dangerStatuses = new Set(['VERIFICATION_FAILED', 'REJECTED', 'CUSTOMER_DECLINED'])
const warningStatuses = new Set(['DOCUMENTS_PENDING', 'RETURNED_FOR_REVISION'])
const neutralStatuses = new Set(['DRAFT', 'CANCELLED', 'EXPIRED'])

export function StatusBadge({ status }: { status: string }) {
  const known = applicationStatusLabel(status) !== 'Status unavailable'
  const treatment = !known || neutralStatuses.has(status) ? 'neutral'
    : successStatuses.has(status) ? 'success'
      : dangerStatuses.has(status) ? 'danger'
        : warningStatuses.has(status) ? 'warning'
          : 'information'
  const Icon = treatment === 'success' ? CheckCircle2
    : treatment === 'danger' ? XCircle
      : treatment === 'warning' ? AlertCircle
        : treatment === 'information' ? Clock3
          : Circle
  return (
    <span className={cn(
      'inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold',
      treatment === 'neutral' && 'border-border bg-muted text-muted-foreground',
      treatment === 'information' && 'border-information/25 bg-information-subtle text-information',
      treatment === 'success' && 'border-success/25 bg-success-subtle text-success',
      treatment === 'warning' && 'border-warning/25 bg-warning-subtle text-warning',
      treatment === 'danger' && 'border-danger/25 bg-danger-subtle text-danger',
    )} title={!known ? status : undefined}>
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      <span>{applicationStatusLabel(status)}</span>
    </span>
  )
}
