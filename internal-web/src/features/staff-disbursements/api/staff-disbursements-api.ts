import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  disbursementDestinationRevealSchema,
  manualDisbursementConfirmationSchema,
  staffDisbursementCaseSchema,
  staffDisbursementWorkPageSchema,
  type ConfirmManualDisbursementRequest,
  type DisbursementDestinationReveal,
  type ManualDisbursementConfirmation,
  type StaffDisbursementCase,
  type StaffDisbursementWorkFilters,
  type StaffDisbursementWorkPage,
} from './contracts'

export async function getStaffDisbursementWork(
  manager: AuthSessionManager,
  filters: StaffDisbursementWorkFilters,
): Promise<StaffDisbursementWorkPage> {
  const search = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.productCode) search.set('productCode', filters.productCode)
  const payload = await manager.protectedRequest<unknown>(`/staff/disbursement-work?${search}`)
  return staffDisbursementWorkPageSchema.parse(payload)
}

export async function getStaffDisbursementCase(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<StaffDisbursementCase> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/disbursement`,
  )
  return staffDisbursementCaseSchema.parse(payload)
}

export async function revealDisbursementDestination(
  manager: AuthSessionManager,
  loanApplicationId: string,
  expectedContractVersion: number,
): Promise<DisbursementDestinationReveal> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/contracts/current/disbursement-destination/reveal`,
    { method: 'POST', body: { expectedContractVersion } },
  )
  return disbursementDestinationRevealSchema.parse(payload)
}

export async function confirmManualDisbursement(
  manager: AuthSessionManager,
  loanApplicationId: string,
  request: ConfirmManualDisbursementRequest,
): Promise<ManualDisbursementConfirmation> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/disbursements`,
    { method: 'POST', body: request },
  )
  return manualDisbursementConfirmationSchema.parse(payload)
}
