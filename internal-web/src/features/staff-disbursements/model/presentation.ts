import type { StaffDisbursementCase } from '../api/contracts'

export const knownDisbursementWorkStages = new Set(['READY_TO_DISBURSE', 'DISBURSED'])
export const knownDisbursementApplicationStatuses = new Set(['DISBURSEMENT_PENDING', 'DISBURSED'])
export const knownDisbursementContractStatuses = new Set(['READY_FOR_DISBURSEMENT'])
export const knownLoanAccountStatuses = new Set(['ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED'])

export function disbursementStageLabel(value: string) {
  if (value === 'READY_TO_DISBURSE') return 'Ready to disburse'
  if (value === 'DISBURSED') return 'Disbursed'
  return 'Work stage unavailable'
}

export function hasCoherentDisbursementCase(value: StaffDisbursementCase) {
  if (!knownDisbursementWorkStages.has(value.workStage)
    || !knownDisbursementApplicationStatuses.has(value.applicationStatus)
    || !knownDisbursementContractStatuses.has(value.currentContract.status)
    || value.currentContract.readinessConfirmedAt === null) return false
  if (value.applicationStatus === 'DISBURSEMENT_PENDING') {
    return value.workStage === 'READY_TO_DISBURSE' && value.activation === null
  }
  return value.workStage === 'DISBURSED'
    && value.activation !== null
    && value.activation.scheduleType === 'FINAL'
    && knownLoanAccountStatuses.has(value.activation.loanAccountStatus)
}
