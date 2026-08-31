import { CheckCircle2, CircleAlert, FileCheck2, History } from 'lucide-react'

import { unavailableStatus, type StatusPresentation } from '@/components/common/status-presentation'
import { ApiError } from '@/lib/api'

import { interestMethodLabel, repaymentMethodLabel } from '@/features/offers/offer-presentation'

const contractStatuses: Record<string, StatusPresentation> = {
  PREPARED: { label: 'Ready for acknowledgment', tone: 'warning', icon: CircleAlert },
  ACKNOWLEDGED: { label: 'Acknowledged', tone: 'success', icon: CheckCircle2 },
  READY_FOR_DISBURSEMENT: { label: 'Ready for disbursement', tone: 'information', icon: FileCheck2 },
  SUPERSEDED: { label: 'Superseded', tone: 'neutral', icon: History },
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
    CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED: 'This contract is not currently available for Customer acknowledgment.',
    IDEMPOTENCY_KEY_REUSED: 'This acknowledgment identity cannot be used for the displayed contract. Refresh and begin again.',
    CURRENT_CONTRACT_MISSING: 'The current operational contract is not ready yet.',
    INVALID_APPLICATION_STATE: 'The application is not currently eligible for contract acknowledgment.',
    LOAN_APPLICATION_ACCESS_DENIED: 'This contract is not available to this Customer.',
  }
  return messages[error.errorCode] ?? 'The contract acknowledgment could not be completed. Refresh the current contract and try again if the action remains available.'
}
