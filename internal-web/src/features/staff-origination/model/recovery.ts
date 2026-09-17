import type { BankAccount, CustomerProfileInput, StaffCustomer } from '../api/contracts'
import type { UnresolvedOperation } from '@/lib/operation/unresolved-operation'

export type EvidenceBaseline = string | null

export function evidenceRecoveryResource(caseId: string, evidenceType: string): string {
  return `intake-evidence:${caseId}:${evidenceType}`
}

export function evidenceBaselineFrom(operation: UnresolvedOperation | undefined): EvidenceBaseline | undefined {
  if (!operation?.semanticPayload || typeof operation.semanticPayload !== 'object') return undefined
  const baseline = (operation.semanticPayload as { baseline?: unknown }).baseline
  return baseline === null || typeof baseline === 'string' ? baseline : undefined
}

export function evidenceSemanticPayload(
  caseId: string,
  evidenceType: string,
  baseline: EvidenceBaseline,
  file: File,
  fileDigest: string,
) {
  return {
    caseId,
    evidenceType,
    baseline,
    fileDigest,
    byteSize: file.size,
    declaredMimeType: file.type,
    originalFilename: file.name,
  }
}

export function profileMatches(customer: StaffCustomer, input: CustomerProfileInput): boolean {
  if (input.identityReference) return false
  const profile = customer.profile
  return Boolean(profile
    && profile.fullName === input.fullName
    && profile.phoneNumber === input.phoneNumber
    && profile.residentialAddress === input.residentialAddress
    && profile.employmentStatus === input.employmentStatus
    && (profile.employerName ?? undefined) === input.employerName
    && profile.termsConsentAccepted === input.termsConsentAccepted
    && profile.dataProcessingConsentAccepted === input.dataProcessingConsentAccepted)
}

export function bankActionMatches(
  accounts: BankAccount[],
  bankId: string,
  action: 'make-primary' | 'deactivate',
): boolean {
  const account = accounts.find((item) => item.customerBankAccountId === bankId)
  return action === 'make-primary'
    ? account?.status === 'ACTIVE' && account.primaryAccount
    : Boolean(account && account.status !== 'ACTIVE')
}
