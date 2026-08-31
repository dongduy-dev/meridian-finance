import { Ban, CheckCircle2, CircleAlert, Clock3, Landmark } from 'lucide-react'

import { unavailableStatus, type StatusPresentation } from '@/components/common/status-presentation'

const loanAccountStatuses: Record<string, StatusPresentation> = {
  ACTIVE: { label: 'Active', tone: 'information', icon: Landmark },
  OVERDUE: { label: 'Overdue', tone: 'danger', icon: CircleAlert },
  SETTLED: { label: 'Settled', tone: 'success', icon: CheckCircle2 },
  CLOSED: { label: 'Closed', tone: 'neutral', icon: Ban },
}

const installmentStatuses: Record<string, StatusPresentation> = {
  NOT_DUE: { label: 'Not due', tone: 'neutral', icon: Clock3 },
  DUE: { label: 'Due', tone: 'warning', icon: Clock3 },
  PARTIALLY_PAID: { label: 'Partially paid', tone: 'information', icon: Clock3 },
  PAID: { label: 'Paid', tone: 'success', icon: CheckCircle2 },
  OVERDUE: { label: 'Overdue', tone: 'danger', icon: CircleAlert },
}

const repaymentAllocationComponents: Record<string, string> = {
  FEE: 'Fee',
  INTEREST: 'Interest',
  PRINCIPAL: 'Principal',
}

export function loanAccountStatusPresentation(value: string) {
  return loanAccountStatuses[value] ?? unavailableStatus
}

export function installmentStatusPresentation(value: string) {
  return installmentStatuses[value] ?? unavailableStatus
}

export function repaymentAllocationComponentLabel(value: string) {
  return repaymentAllocationComponents[value] ?? 'Component unavailable'
}
