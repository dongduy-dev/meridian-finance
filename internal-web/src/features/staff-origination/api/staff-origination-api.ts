import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  assistedOriginationSchema, bankAccountSchema, intakeEvidenceSchema, intakeVersionSchema, staffCustomerSchema,
  type AssistedOrigination, type BankAccount, type CustomerProfileInput,
  type IntakeEvidence, type IntakeEvidenceVersion, type StaffCustomer,
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
export async function searchCustomer(manager: AuthSessionManager, input: { customerNumber?: string; identityReference?: string }): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest('/staff/customers/search', { method: 'POST', body: input }))
}
export async function getCustomer(manager: AuthSessionManager, id: string): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest(`/staff/customers/${id}`))
}
export async function createCustomer(manager: AuthSessionManager, input: CustomerProfileInput & { identityReference: string }): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest('/staff/customers', { method: 'POST', body: input }))
}
export async function updateCustomer(manager: AuthSessionManager, id: string, input: CustomerProfileInput): Promise<StaffCustomer> {
  return staffCustomerSchema.parse(await manager.protectedRequest(`/staff/customers/${id}/profile`, { method: 'PUT', body: input }))
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
  return intakeEvidenceSchema.array().parse(await manager.protectedRequest(`/staff/assisted-originations/${id}/evidence`))
}
export async function uploadEvidence(manager: AuthSessionManager, id: string, evidenceType: string, file: File, uploadRequestId: string, expected?: string): Promise<IntakeEvidenceVersion> {
  const data = new FormData(); data.set('file', file); data.set('uploadRequestId', uploadRequestId)
  if (expected) data.set('expectedCurrentVersionId', expected)
  return intakeVersionSchema.parse(await manager.protectedRequest(`/staff/assisted-originations/${id}/evidence/${evidenceType}/versions`, { method: 'POST', body: data }))
}
