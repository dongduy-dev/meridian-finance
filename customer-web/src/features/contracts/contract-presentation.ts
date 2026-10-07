import { CheckCircle2, CircleAlert, FileCheck2, History } from 'lucide-react'

import { unavailableStatus, type StatusPresentation } from '@/components/common/status-presentation'
import { ApiError } from '@/lib/api'

import { interestMethodLabel, repaymentMethodLabel } from '@/features/offers/offer-presentation'

const contractStatuses: Record<string, StatusPresentation> = {
  PREPARED: { label: 'Ready for review', tone: 'warning', icon: CircleAlert },
  ACKNOWLEDGED: { label: 'Review confirmed', tone: 'success', icon: CheckCircle2 },
  READY_FOR_DISBURSEMENT: { label: 'Ready for disbursement', tone: 'information', icon: FileCheck2 },
  SUPERSEDED: { label: 'Replaced version', tone: 'neutral', icon: History },
}

export { interestMethodLabel, repaymentMethodLabel }

export function contractStatusPresentation(value: string) {
  return contractStatuses[value] ?? unavailableStatus
}

export function contractErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    return 'The acknowledgment result could not be confirmed. Check the current contract before retrying.'
  }
  const messages: Record<string, string> = {
    CONTRACT_VERSION_STALE: 'A newer contract version is current. Review it before acknowledging again.',
    CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED: 'This contract cannot be confirmed right now.',
    IDEMPOTENCY_KEY_REUSED: 'This confirmation could not be completed. Refresh the contract and review the current version before trying again.',
    CURRENT_CONTRACT_MISSING: 'Your contract is not ready yet.',
    INVALID_APPLICATION_STATE: 'Contract review cannot be confirmed at this stage of your application.',
    LOAN_APPLICATION_ACCESS_DENIED: 'This contract is not available to you.',
  }
  return messages[error.errorCode] ?? 'The contract acknowledgment could not be completed. Refresh the current contract and try again if the action remains available.'
}
