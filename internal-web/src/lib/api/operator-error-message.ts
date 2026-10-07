import { ApiError } from './errors'

// Only confirmed contract codes receive a business explanation. Transport and
// unfamiliar failures retain the caller's recovery guidance, never API prose.
const explanations = new Map<string, string>([
  ['VALIDATION_ERROR', 'Review the required fields and correct any invalid entries.'],
  ['MAKER_CHECKER_VIOLATION', 'A different authorized Approver must complete the credit decision.'],
  ['STAFF_CORRECTION_MAKER_CHECKER_VIOLATION', 'Another authorized Staff member must complete the Staff tasks in this correction request.'],
  ['LOAN_REVIEW_ASSIGNED_TO_ANOTHER_OFFICER', 'Only the assigned Loan Officer can continue this review or record its recommendation.'],
  ['STALE_DOCUMENT_VERSION', 'The document version changed. Refresh and review the current version before taking action.'],
  ['DOCUMENT_ALREADY_REVIEWED', 'This document version has already been reviewed. Refresh to inspect the recorded review.'],
  ['STALE_REVIEW_CYCLE', 'The review cycle changed. Refresh and review the current cycle before taking action.'],
  ['STALE_REVIEW_RECOMMENDATION', 'The recommendation changed. Refresh and review the current recommendation before recording a decision.'],
  ['STALE_COLLATERAL_VERIFICATION', 'The Collateral assessment cycle changed. Refresh and review the current assessment before taking action.'],
  ['CONTRACT_VERSION_STALE', 'The contract version changed. Refresh and review the current contract before taking action.'],
  ['CORRECTION_TASK_PROOF_MISSING', 'The required correction evidence is missing. Review the task and its document evidence.'],
  ['CORRECTION_TASKS_INCOMPLETE', 'Required correction tasks remain incomplete. Review all tasks before resubmitting.'],
  ['CORRECTION_ALREADY_RESUBMITTED', 'This correction has already been resubmitted. Refresh to inspect the current application.'],
  ['REPAYMENT_EXCEEDS_OUTSTANDING', 'The payment exceeds the outstanding balance. Review the latest balance and payment amount.'],
  ['SETTLEMENT_AMOUNT_INVALID', 'The settlement amount does not match the current full outstanding balance. Refresh and review the balance before taking action.'],
  ['LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED', 'This loan account is not eligible for administrative closure. Review its settlement status and outstanding balance.'],
  ['INTERNAL_USER_NOT_ACTIVE', 'Password setup requires an active Staff account. Review the access status before sending another link.'],
  ['EMAIL_ALREADY_REGISTERED', 'An account with this email already exists. Review the existing account before creating another.'],
  ['INTERNAL_ROLE_NOT_FOUND', 'A selected role is no longer assignable. Refresh the role list before trying again.'],
  ['INVALID_EFFECTIVE_MONTH', 'Enter a valid effective month in YYYY-MM format.'],
  ['PARTNER_EMPLOYEE_ROWS_REQUIRED', 'Include the complete employee roster for the selected month.'],
])

export function operatorErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError) || error.status >= 500 || error.status === 408) return fallback
  if (error.status === 401) return 'Your session has ended. Sign in again before continuing.'
  if (error.status === 404) return 'This record is not available in your current workspace.'
  // These controlled separation-of-duty codes describe the caller's restriction
  // without disclosing another actor or the existence of a concealed resource.
  if (error.status === 403) {
    if (error.errorCode === 'MAKER_CHECKER_VIOLATION'
      || error.errorCode === 'STAFF_CORRECTION_MAKER_CHECKER_VIOLATION') return explanations.get(error.errorCode)!
    return 'You do not have access to complete this action. Review your Staff access with an administrator.'
  }
  return explanations.get(error.errorCode) ?? fallback
}
