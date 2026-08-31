import { Ban, CheckCircle2, Clock3, CircleAlert } from 'lucide-react'

import { unavailableStatus, type StatusPresentation } from '@/components/common/status-presentation'
import { ApiError } from '@/lib/api'

const offerStatuses: Record<string, StatusPresentation> = {
  PENDING: { label: 'Awaiting your response', tone: 'warning', icon: CircleAlert },
  ACCEPTED: { label: 'Accepted', tone: 'success', icon: CheckCircle2 },
  DECLINED: { label: 'Declined', tone: 'neutral', icon: Ban },
  EXPIRED: { label: 'Expired', tone: 'neutral', icon: Clock3 },
}

const interestMethods: Record<string, string> = {
  FLAT_ORIGINAL_PRINCIPAL: 'Flat rate on original principal',
}

const repaymentMethods: Record<string, string> = {
  ON_SALARY_DATE: 'On salary date',
  MONTHLY_INSTALLMENT: 'Monthly installments',
}

export type SupportedOfferAction = 'ACCEPT' | 'DECLINE'

export function offerStatusPresentation(value: string) {
  return offerStatuses[value] ?? unavailableStatus
}

export function interestMethodLabel(value: string) {
  return interestMethods[value] ?? 'Method unavailable'
}

export function repaymentMethodLabel(value: string) {
  return repaymentMethods[value] ?? 'Method unavailable'
}

export function repaymentTimingLabel(value: string) {
  return repaymentMethods[value] ?? 'Timing unavailable'
}

export function supportedOfferAction(value: string): value is SupportedOfferAction {
  return value === 'ACCEPT' || value === 'DECLINE'
}

export function offerErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    return 'The offer response could not be confirmed. Check the current offer status before retrying.'
  }
  const messages: Record<string, string> = {
    OFFER_EXPIRED: 'This offer has expired. Meridian refreshed the current offer state.',
    OFFER_ACTION_CONFLICT: 'The offer changed before this response was completed. Meridian refreshed the current state.',
    APPROVED_OFFER_NOT_FOUND: 'The approved offer is not currently available.',
    LOAN_APPLICATION_NOT_FOUND: 'This application is not currently available.',
    ACCESS_DENIED: 'This offer is not available to this Customer.',
  }
  return messages[error.errorCode] ?? 'The offer response could not be completed. Refresh the offer and try again if an action remains available.'
}
