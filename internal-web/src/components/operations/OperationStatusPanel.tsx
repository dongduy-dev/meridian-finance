import { AlertCircle, CheckCircle2, Clock3, LoaderCircle, ShieldAlert } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

export const operationStatuses = [
  'DRAFT',
  'IN_FLIGHT',
  'RESULT_UNKNOWN',
  'RECONCILING',
  'RESOLVED',
  'BLOCKED',
] as const

export type OperationStatus = (typeof operationStatuses)[number]

const presentation = {
  DRAFT: { title: 'Ready to review', description: 'The operation has not been submitted.', variant: 'information', icon: Clock3 },
  IN_FLIGHT: { title: 'Submitting operation', description: 'Wait for Meridian to confirm the result.', variant: 'information', icon: LoaderCircle },
  RESULT_UNKNOWN: { title: 'Result not yet confirmed', description: 'Do not start a contradictory operation while the result is checked.', variant: 'warning', icon: AlertCircle },
  RECONCILING: { title: 'Checking authoritative state', description: 'Meridian is reconciling the durable result.', variant: 'information', icon: LoaderCircle },
  RESOLVED: { title: 'Operation confirmed', description: 'The authoritative result has been reconciled.', variant: 'success', icon: CheckCircle2 },
  BLOCKED: { title: 'Operation blocked', description: 'Review the current authority before continuing.', variant: 'destructive', icon: ShieldAlert },
} as const

export function OperationStatusPanel({ status }: { status: OperationStatus }) {
  const item = presentation[status]
  const Icon = item.icon
  return (
    <Alert variant={item.variant} aria-live="polite">
      <Icon aria-hidden="true" className={status === 'IN_FLIGHT' || status === 'RECONCILING' ? 'animate-spin' : undefined} />
      <AlertTitle>{item.title}</AlertTitle>
      <AlertDescription>{item.description}</AlertDescription>
    </Alert>
  )
}
