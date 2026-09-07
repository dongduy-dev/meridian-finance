import type { StaffContractCase } from '../api/contracts'

export const knownContractStatuses = new Set([
  'PREPARED',
  'ACKNOWLEDGED',
  'READY_FOR_DISBURSEMENT',
  'SUPERSEDED',
])

export const knownContractWorkStages = new Set([
  'NEEDS_PREPARATION',
  'CUSTOMER_ACKNOWLEDGMENT_REQUIRED',
  'READINESS_BLOCKED',
  'READY_TO_CONFIRM',
  'READINESS_CONFIRMED',
])

export const knownReadinessBlockers = new Set([
  'INVALID_APPLICATION_STATE',
  'ACCEPTED_OFFER_MISSING',
  'ACCEPTED_OFFER_NOT_ACCEPTED',
  'CURRENT_CONTRACT_MISSING',
  'CONTRACT_VERSION_STALE',
  'ACKNOWLEDGMENT_MISSING',
  'CUSTOMER_INACTIVE',
  'CAPTURED_ACCOUNT_MISSING',
  'CAPTURED_ACCOUNT_INACTIVE',
  'DOCUMENTS_NOT_PROCESSING_READY',
  'ACTIVE_CORRECTION_REQUEST',
  'UCL_VERIFICATION_INVALID',
  'COLLATERAL_VERIFICATION_INVALID',
  'SALARY_ADVANCE_RESERVATION_INVALID',
  'SALARY_ADVANCE_RESERVATION_RELEASED',
  'READINESS_ALREADY_CONFIRMED',
  'CONFLICTING_COMPLETED_TRANSITION',
])

const blockerLabels: Record<string, string> = {
  INVALID_APPLICATION_STATE: 'Application is not in the contract-pending state.',
  ACCEPTED_OFFER_MISSING: 'The accepted offer is unavailable.',
  ACCEPTED_OFFER_NOT_ACCEPTED: 'The approved offer has not been accepted.',
  CURRENT_CONTRACT_MISSING: 'No current contract has been prepared.',
  CONTRACT_VERSION_STALE: 'The displayed contract version is stale.',
  ACKNOWLEDGMENT_MISSING: 'Customer acknowledgment of this exact version is required.',
  CUSTOMER_INACTIVE: 'The Customer is not active.',
  CAPTURED_ACCOUNT_MISSING: 'The captured destination no longer exists.',
  CAPTURED_ACCOUNT_INACTIVE: 'The captured destination is inactive.',
  DOCUMENTS_NOT_PROCESSING_READY: 'Documents are not processing-ready.',
  ACTIVE_CORRECTION_REQUEST: 'An active correction request blocks readiness.',
  UCL_VERIFICATION_INVALID: 'UCL verification is not valid.',
  COLLATERAL_VERIFICATION_INVALID: 'Collateral verification is not valid.',
  SALARY_ADVANCE_RESERVATION_INVALID: 'Salary Advance reservation evidence is invalid.',
  SALARY_ADVANCE_RESERVATION_RELEASED: 'The Salary Advance reservation was released.',
  READINESS_ALREADY_CONFIRMED: 'Readiness was already confirmed.',
  CONFLICTING_COMPLETED_TRANSITION: 'Completed transition evidence is inconsistent.',
}

const stageLabels: Record<string, string> = {
  NEEDS_PREPARATION: 'Needs preparation',
  CUSTOMER_ACKNOWLEDGMENT_REQUIRED: 'Customer acknowledgment required',
  READINESS_BLOCKED: 'Readiness blocked',
  READY_TO_CONFIRM: 'Ready to confirm',
  READINESS_CONFIRMED: 'Readiness confirmed',
}

const statusLabels: Record<string, string> = {
  PREPARED: 'Prepared',
  ACKNOWLEDGED: 'Acknowledged',
  READY_FOR_DISBURSEMENT: 'Ready for disbursement',
  SUPERSEDED: 'Superseded',
}

export function blockerLabel(value: string) {
  return blockerLabels[value] ?? 'Unknown readiness blocker. Refresh authoritative evidence.'
}

export function contractStageLabel(value: string) {
  return stageLabels[value] ?? 'Work stage unavailable'
}

export function contractStatusLabel(value: string) {
  return statusLabels[value] ?? 'Status unavailable'
}

export function hasCoherentContractLifecycle(value: StaffContractCase) {
  const contract = value.currentContract
  const readiness = value.readiness
  const readinessIdentityMatches = contract
    ? readiness.contractId === contract.contractId
      && readiness.contractVersion === contract.contractVersion
    : readiness.contractId === null && readiness.contractVersion === null

  if (!readinessIdentityMatches) return false
  if (value.applicationStatus === 'DISBURSEMENT_PENDING') {
    return value.workStage === 'READINESS_CONFIRMED'
      && contract?.status === 'READY_FOR_DISBURSEMENT'
      && !readiness.ready
      && readiness.blockerCodes.includes('READINESS_ALREADY_CONFIRMED')
  }
  if (value.applicationStatus !== 'CONTRACT_PENDING') return false
  if (!contract) {
    return value.workStage === 'NEEDS_PREPARATION'
      && !readiness.ready
      && readiness.blockerCodes.includes('CURRENT_CONTRACT_MISSING')
  }
  if (contract.status === 'PREPARED') {
    return value.workStage === 'CUSTOMER_ACKNOWLEDGMENT_REQUIRED'
      && !readiness.ready
      && readiness.blockerCodes.includes('ACKNOWLEDGMENT_MISSING')
  }
  if (contract.status === 'ACKNOWLEDGED') {
    return readiness.ready
      ? value.workStage === 'READY_TO_CONFIRM' && readiness.blockerCodes.length === 0
      : value.workStage === 'READINESS_BLOCKED' && readiness.blockerCodes.length > 0
  }
  return false
}
