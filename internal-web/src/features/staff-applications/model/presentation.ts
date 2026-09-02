export const productOptions = [
  ['SALARY_ADVANCE', 'Salary Advance'],
  ['UNSECURED_CONSUMER_LOAN', 'Unsecured Consumer Loan'],
  ['COLLATERAL_LOAN', 'Collateral Loan'],
] as const

export const applicationStatusOptions = [
  'DRAFT',
  'SUBMITTED',
  'VERIFICATION_PENDING',
  'VERIFICATION_FAILED',
  'DOCUMENTS_PENDING',
  'UNDER_REVIEW',
  'RETURNED_FOR_REVISION',
  'RETURNED_TO_REVIEW',
  'APPROVAL_PENDING',
  'APPROVED',
  'REJECTED',
  'CUSTOMER_ACCEPTANCE_PENDING',
  'CUSTOMER_DECLINED',
  'CONTRACT_PENDING',
  'DISBURSEMENT_PENDING',
  'DISBURSED',
  'CANCELLED',
  'EXPIRED',
] as const

const applicationStatusLabels: Record<string, string> = {
  DRAFT: 'Draft',
  SUBMITTED: 'Submitted',
  VERIFICATION_PENDING: 'Verification pending',
  VERIFICATION_FAILED: 'Verification failed',
  DOCUMENTS_PENDING: 'Documents pending',
  UNDER_REVIEW: 'Under review',
  RETURNED_FOR_REVISION: 'Returned for revision',
  RETURNED_TO_REVIEW: 'Returned to review',
  APPROVAL_PENDING: 'Approval pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CUSTOMER_ACCEPTANCE_PENDING: 'Customer acceptance pending',
  CUSTOMER_DECLINED: 'Customer declined',
  CONTRACT_PENDING: 'Contract pending',
  DISBURSEMENT_PENDING: 'Disbursement pending',
  DISBURSED: 'Disbursed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
}

const transitionActionLabels: Record<string, string> = {
  SUBMIT_APPLICATION: 'Application submitted',
  COMPLETE_DOCUMENT_UPLOADS: 'Document uploads completed',
  START_PRODUCT_VERIFICATION: 'Product verification started',
  COMPLETE_PRODUCT_VERIFICATION: 'Product verification completed',
  START_REVIEW: 'Loan Officer review started',
  RECOMMEND_APPROVAL: 'Approval recommended',
  RECOMMEND_REJECTION: 'Rejection recommended',
  RETURN_TO_CUSTOMER_REVISION: 'Returned for Customer revision',
  REQUEST_STAFF_CORRECTION: 'Staff correction requested',
  APPROVE: 'Application approved',
  REJECT: 'Application rejected',
  RETURN_TO_LOAN_OFFICER_REVIEW: 'Returned to Loan Officer review',
  REQUEST_CUSTOMER_OR_STAFF_CORRECTION: 'Customer or Staff correction requested',
  RESUBMIT_CORRECTION: 'Correction resubmitted',
  CANCEL_APPLICATION: 'Application cancelled',
  GENERATE_APPROVED_OFFER: 'Approved offer generated',
  ACCEPT_APPROVED_OFFER: 'Approved offer accepted',
  DECLINE_APPROVED_OFFER: 'Approved offer declined',
  EXPIRE_APPROVED_OFFER: 'Approved offer expired',
  CONFIRM_DISBURSEMENT_READINESS: 'Disbursement readiness confirmed',
  CONFIRM_MANUAL_DISBURSEMENT: 'Manual disbursement confirmed',
}

export function applicationStatusLabel(value: string): string {
  return applicationStatusLabels[value] ?? 'Status unavailable'
}

export function transitionActionLabel(value: string): string {
  return transitionActionLabels[value] ?? 'Lifecycle action unavailable'
}

export function productLabel(value: string): string {
  return productOptions.find(([code]) => code === value)?.[1] ?? 'Product unavailable'
}

export function humanizeKnownValue(value: string): string {
  return value.toLowerCase().split('_').map((part) => (
    part.length > 0 ? `${part.charAt(0).toUpperCase()}${part.slice(1)}` : part
  )).join(' ')
}

export function isSupportedProduct(value: string | null): boolean {
  return value === null || productOptions.some(([code]) => code === value)
}

export function isSupportedApplicationStatus(
  value: string | null,
): boolean {
  return value === null || applicationStatusOptions.includes(value as typeof applicationStatusOptions[number])
}
