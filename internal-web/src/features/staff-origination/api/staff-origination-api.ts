import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { ApiBinaryResponse } from '@/lib/api'
import {
  assistedOriginationSchema, bankAccountSchema, intakeEvidenceSchema, intakeOcrJobSchema,
  intakeOcrReviewSchema, intakeVersionSchema, staffCustomerSchema,
  type AssistedOrigination, type BankAccount, type CustomerProfileInput,
  type CollateralLoanInput, type IntakeEvidence, type IntakeEvidenceVersion, type IntakeOcrJob,
  type IntakeOcrReview, type StaffCustomer, type UpdateCustomerProfileInput,
} from './contracts'

export async function listOpenIntakes(manager: AuthSessionManager): Promise<AssistedOrigination[]> {
  return assistedOriginationSchema.array().parse(
    await manager.protectedRequest('/staff/assisted-originations?status=OPEN'),
  )
}
export async function getIntake(manager: AuthSessionManager, id: string): Promise<AssistedOrigination> {
  return assistedOriginationSchema.parse(await manager.protectedRequest(`/staff/assisted-originations/${id}`))
}
export async function createIntake(manager: AuthSessionManager, productCode: string): Promise<AssistedOrigination> {
  return assistedOriginationSchema.parse(await manager.protectedRequest('/staff/assisted-originations', { method: 'POST', body: { productCode } }))
}
export async function attachCustomer(manager: AuthSessionManager, id: string, customerId: string): Promise<AssistedOrigination> {
  return assistedOriginationSchema.parse(await manager.protectedRequest(`/staff/assisted-originations/${id}/customer`, { method: 'PUT', body: { customerId } }))
}
export async function abandonIntake(manager: AuthSessionManager, id: string): Promise<AssistedOrigination> {
  return assistedOriginationSchema.parse(await manager.protectedRequest(`/staff/assisted-originations/${id}/abandon`, { method: 'POST' }))
}
export async function submitUclIntake(
  manager: AuthSessionManager,
  id: string,
  input: { requestedAmount: number; requestedTermMonths: number },
): Promise<AssistedOrigination> {
  return assistedOriginationSchema.parse(await manager.protectedRequest(
    `/staff/assisted-originations/${id}/unsecured-consumer-loan/submit`,
    { method: 'POST', body: input },
  ))
}
export async function submitCollateralIntake(
  manager: AuthSessionManager,
  id: string,
  input: CollateralLoanInput,
): Promise<AssistedOrigination> {
  return assistedOriginationSchema.parse(await manager.protectedRequest(
    `/staff/assisted-originations/${id}/collateral-loan/submit`,
    { method: 'POST', body: input },
  ))
}
export async function searchCustomer(manager: AuthSessionManager, input: { customerNumber?: string; identityReference?: string }): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest('/staff/customers/search', { method: 'POST', body: input }))
}
export async function getCustomer(manager: AuthSessionManager, id: string): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest(`/staff/customers/${id}`))
}
export async function createCustomer(manager: AuthSessionManager, input: CustomerProfileInput & { identityReference: string }): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest('/staff/customers', { method: 'POST', body: input }))
}
export async function updateCustomer(manager: AuthSessionManager, id: string, input: UpdateCustomerProfileInput): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest(`/staff/customers/${id}/profile`, { method: 'PUT', body: input }))
}
export async function correctIdentityReference(manager: AuthSessionManager, id: string, identityReference: string): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest(`/staff/customers/${id}/identity-reference`,
    { method: 'PUT', body: { identityReference } }, { replayAfterSessionRefresh: false }))
}
export async function listBankAccounts(manager: AuthSessionManager, id: string): Promise<BankAccount[]> {
  return bankAccountSchema.array().parse(await manager.protectedRequest(`/staff/customers/${id}/bank-accounts`))
}
export async function addBankAccount(manager: AuthSessionManager, id: string, input: Record<string, string>): Promise<BankAccount> {
  return bankAccountSchema.parse(await manager.protectedRequest(`/staff/customers/${id}/bank-accounts`, { method: 'POST', body: input }))
}
export async function bankAction(manager: AuthSessionManager, customerId: string, bankId: string, action: 'make-primary' | 'deactivate'): Promise<BankAccount> {
  return bankAccountSchema.parse(await manager.protectedRequest(`/staff/customers/${customerId}/bank-accounts/${bankId}/${action}`, { method: 'POST' }))
}
export async function listEvidence(manager: AuthSessionManager, id: string): Promise<IntakeEvidence[]> {
  const evidence = intakeEvidenceSchema.array().parse(await manager.protectedRequest(`/staff/assisted-originations/${id}/evidence`))
  if (evidence.some(item => item.assistedOriginationCaseId !== id)
    || new Set(evidence.map(item => item.evidenceType)).size !== evidence.length) {
    throw new Error('Intake evidence metadata is inconsistent.')
  }
  return evidence
}
export function getIntakeEvidenceContent(manager: AuthSessionManager, caseId: string, evidenceType: string, versionId: string) {
  return manager.protectedRequest<ApiBinaryResponse>(
    `/staff/assisted-originations/${encodeURIComponent(caseId)}/evidence/${encodeURIComponent(evidenceType)}/versions/${encodeURIComponent(versionId)}/content`,
    { responseType: 'blob', cache: 'no-store' },
  )
}
export async function uploadEvidence(manager: AuthSessionManager, id: string, evidenceType: string, file: File, uploadRequestId: string, expected?: string): Promise<IntakeEvidenceVersion> {
  const data = new FormData(); data.set('file', file); data.set('uploadRequestId', uploadRequestId)
  if (expected) data.set('expectedCurrentVersionId', expected)
  return intakeVersionSchema.parse(await manager.protectedRequest(`/staff/assisted-originations/${id}/evidence/${evidenceType}/versions`, { method: 'POST', body: data }))
}

const ocrPath = (caseId: string, evidenceType: string, versionId: string) =>
  `/staff/assisted-originations/${caseId}/evidence/${evidenceType}/versions/${versionId}/ocr`

export async function getIntakeOcrStatus(manager: AuthSessionManager, caseId: string, evidenceType: string, versionId: string): Promise<IntakeOcrJob> {
  return intakeOcrJobSchema.parse(await manager.protectedRequest(ocrPath(caseId, evidenceType, versionId)))
}

export async function startIntakeOcr(manager: AuthSessionManager, caseId: string, evidenceType: string, versionId: string): Promise<IntakeOcrJob> {
  return intakeOcrJobSchema.parse(await manager.protectedRequest(ocrPath(caseId, evidenceType, versionId), { method: 'POST' }))
}

export async function getIntakeOcrReview(manager: AuthSessionManager, caseId: string, evidenceType: string, versionId: string): Promise<IntakeOcrReview> {
  return intakeOcrReviewSchema.parse(await manager.protectedRequest(`${ocrPath(caseId, evidenceType, versionId)}/review`))
}

export async function finalizeIntakeOcrReview(
  manager: AuthSessionManager,
  caseId: string,
  evidenceType: string,
  versionId: string,
  input: { expectedOcrResultId: string; reviewedFields: Record<string, string> },
): Promise<IntakeOcrReview> {
  return intakeOcrReviewSchema.parse(await manager.protectedRequest(
    `${ocrPath(caseId, evidenceType, versionId)}/review`, { method: 'POST', body: input },
  ))
}
