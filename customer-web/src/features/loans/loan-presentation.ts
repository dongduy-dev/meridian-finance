import { Ban, CheckCircle2, CircleAlert, Landmark } from 'lucide-react'

import { unavailableStatus, type StatusPresentation } from '@/components/common/status-presentation'

const loanAccountStatuses: Record<string, StatusPresentation> = {
  ACTIVE: { label: 'Active', tone: 'information', icon: Landmark },
  OVERDUE: { label: 'Overdue', tone: 'danger', icon: CircleAlert },
  SETTLED: { label: 'Settled', tone: 'success', icon: CheckCircle2 },
  CLOSED: { label: 'Closed', tone: 'neutral', icon: Ban },
}

export function loanAccountStatusPresentation(value: string) {
  return loanAccountStatuses[value] ?? unavailableStatus
}
