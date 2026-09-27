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
  DRAFT: { title: 'Ready to review', description: 'This action has not been submitted.', variant: 'information', icon: Clock3 },
  IN_FLIGHT: { title: 'Submitting action', description: 'Meridian is waiting for the result.', variant: 'information', icon: LoaderCircle },
  RESULT_UNKNOWN: { title: 'Result not confirmed', description: 'Meridian could not confirm completion. Do not start a different conflicting action; use the recovery step shown.', variant: 'warning', icon: AlertCircle },
  RECONCILING: { title: 'Checking result', description: 'Meridian is checking the latest information before another action is allowed.', variant: 'information', icon: LoaderCircle },
  RESOLVED: { title: 'Result confirmed', description: 'The latest information confirms this action.', variant: 'success', icon: CheckCircle2 },
  BLOCKED: { title: 'Action needs review', description: 'Review the latest information and the next step before continuing.', variant: 'destructive', icon: ShieldAlert },
} as const

type OperationStatusPanelProps = {
  status: OperationStatus
  headingId?: string
  headingLabel?: string
}

export function OperationStatusPanel({ status, headingId, headingLabel }: OperationStatusPanelProps) {
  const item = presentation[status]
  const Icon = item.icon
  return (
    <Alert variant={item.variant} aria-live="polite">
      <Icon aria-hidden="true" className={status === 'IN_FLIGHT' || status === 'RECONCILING' ? 'animate-spin' : undefined} />
      <AlertTitle id={headingId} tabIndex={headingId ? -1 : undefined} aria-label={headingLabel}>{item.title}</AlertTitle>
      <AlertDescription>{item.description}</AlertDescription>
    </Alert>
  )
}
