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
  return transitionActionLabels[value] ?? 'Application activity unavailable'
}

export function productLabel(value: string): string {
  return productOptions.find(([code]) => code === value)?.[1] ?? 'Product unavailable'
}

// Existing Staff workspaces share these closed vocabularies. An unfamiliar API value
// must never be turned into a plausible business status by changing its casing.
const knownValueLabels: Record<string, string> = {
  SECURED: 'Secured', UNSECURED: 'Unsecured', SALARY_BASED: 'Salary-based',
  SALARY_ADVANCE: 'Salary Advance',
  UNSECURED_CONSUMER_LOAN: 'Unsecured Consumer Loan',
  COLLATERAL_LOAN: 'Collateral Loan',
  DRAFT: 'Draft', SUBMITTED: 'Submitted',
  VERIFICATION_PENDING: 'Verification pending',
  VERIFICATION_FAILED: 'Verification failed',
  DOCUMENTS_PENDING: 'Documents pending',
  UNDER_REVIEW: 'Under review',
  RETURNED_FOR_REVISION: 'Returned for revision',
  RETURNED_TO_REVIEW: 'Returned to review',
  APPROVAL_PENDING: 'Approval pending', APPROVED: 'Approved',
  REJECTED: 'Rejected', CUSTOMER_ACCEPTANCE_PENDING: 'Customer acceptance pending',
  CUSTOMER_DECLINED: 'Customer declined', CONTRACT_PENDING: 'Contract pending',
  DISBURSEMENT_PENDING: 'Disbursement pending', DISBURSED: 'Disbursed',
  EXPIRED: 'Expired',
  CUSTOMER_DIGITAL: 'Customer digital',
  STAFF_ASSISTED: 'Staff assisted',
  MOTORBIKE: 'Motorbike', CAR: 'Car', ELECTRONICS: 'Electronics',
  PROPERTY_DOCUMENT: 'Property document', OTHER: 'Other',
  MATCHED_ACTIVE: 'Active employee matched',
  MATCHED_INACTIVE: 'Inactive employee matched',
  NOT_FOUND: 'No employee match',
  MULTIPLE_MATCHES: 'Multiple employee matches',
  PENDING_MANUAL_REVIEW: 'Manual review pending',
  MANUAL_REVIEW_APPROVED: 'Employment confirmed by review',
  MANUAL_REVIEW_REJECTED: 'Employment not confirmed by review',
  UNVERIFIED: 'Unverified', VERIFIED: 'Verified', FAILED: 'Failed',
  REQUIRES_MORE_INFORMATION: 'More information needed',
  FLAT_ORIGINAL_PRINCIPAL: 'Flat interest on original principal',
  ON_SALARY_DATE: 'On salary date', MONTHLY_INSTALLMENT: 'Monthly installment',
  FINAL: 'Final schedule',
  PREPARED: 'Prepared', ACKNOWLEDGED: 'Acknowledged',
  READY_FOR_DISBURSEMENT: 'Ready for disbursement',
  SUPERSEDED: 'Replaced by a newer version',
  ACTIVE: 'Active', OVERDUE: 'Overdue', SETTLED: 'Settled', CLOSED: 'Closed',
  OPEN: 'Open', COMPLETED: 'Completed', CANCELLED: 'Cancelled',
  READY_FOR_RESUBMISSION: 'Ready for resubmission', RESUBMITTED: 'Resubmitted',
  CORRECTION_REQUIRED: 'Correction needed', CORRECTED: 'Corrected',
  SUPPORTING_DOCUMENT_UPLOAD: 'Upload supporting document',
  DOCUMENT_REPLACEMENT: 'Replace document', DOCUMENT_REVIEW: 'Review document',
  CUSTOMER: 'Customer', STAFF: 'Staff',
  SATISFIED: 'Complete', MISSING: 'Missing', NOT_APPLICABLE: 'Not applicable',
  RECENT_PAYSLIP: 'Recent payslip', INCOME_PROOF: 'Income proof',
  BANK_STATEMENT: 'Bank statement', EMPLOYMENT_PROOF: 'Employment proof',
  COLLATERAL_OWNERSHIP_EVIDENCE: 'Collateral ownership evidence',
  REQUIRED: 'Required', OPTIONAL: 'Optional', NOT_REQUIRED: 'Not required',
  SUBMISSION: 'Submission',
  ACCEPTED: 'Accepted', WAIVED: 'Waived',
  REPLACEMENT_REQUESTED: 'Replacement requested',
  NOT_UPLOADED: 'Not uploaded', AWAITING_REVIEW: 'Awaiting review',
  ACCEPT_DOCUMENT: 'Accept document', WAIVE_DOCUMENT: 'Waive document',
  REQUEST_REPLACEMENT: 'Request replacement',
  EVIDENCE_SATISFIED_BY_VERIFIED_SOURCE: 'Verified source satisfies requirement',
  DOCUMENT_NOT_APPLICABLE: 'Document does not apply',
  RECOMMEND_APPROVAL: 'Recommend approval',
  RECOMMEND_REJECTION: 'Recommend rejection',
  RETURN_TO_CUSTOMER_REVISION: 'Return for Customer revision',
  REQUEST_STAFF_CORRECTION: 'Request Staff correction',
  APPROVE: 'Approve', REJECT: 'Reject',
  RETURN_TO_LOAN_OFFICER_REVIEW: 'Return to Loan Officer',
  REQUEST_CUSTOMER_OR_STAFF_CORRECTION: 'Request correction',
  SUPPORTING_DOCUMENT_REQUIRED: 'Supporting document required',
  RECENT_PAYSLIP_REQUIRED: 'Recent payslip required',
  DOCUMENT_REPLACEMENT_REQUIRED: 'Document replacement required',
  DOCUMENT_REVIEW_REQUIRED: 'Document review required',
}

export function humanizeKnownValue(value: string): string {
  return knownValueLabels[value] ?? 'Information unavailable'
}

export function isSupportedProduct(value: string | null): boolean {
  return value === null || productOptions.some(([code]) => code === value)
}

export function isSupportedApplicationStatus(
  value: string | null,
): boolean {
  return value === null || applicationStatusOptions.includes(value as typeof applicationStatusOptions[number])
}
