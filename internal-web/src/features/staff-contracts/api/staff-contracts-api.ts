import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  loanContractSchema,
  staffContractCaseSchema,
  staffContractWorkPageSchema,
  type ConfirmContractReadinessRequest,
  type LoanContract,
  type PrepareLoanContractRequest,
  type StaffContractCase,
  type StaffContractWorkFilters,
  type StaffContractWorkPage,
} from './contracts'

export async function getStaffContractWork(
  manager: AuthSessionManager,
  filters: StaffContractWorkFilters,
): Promise<StaffContractWorkPage> {
  const search = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.productCode) search.set('productCode', filters.productCode)
  const payload = await manager.protectedRequest<unknown>(`/staff/contract-work?${search}`)
  return staffContractWorkPageSchema.parse(payload)
}

export async function getStaffContractCase(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<StaffContractCase> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/contract`,
  )
  return staffContractCaseSchema.parse(payload)
}

export async function prepareLoanContract(
  manager: AuthSessionManager,
  loanApplicationId: string,
  request: PrepareLoanContractRequest,
): Promise<LoanContract> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/contracts`,
    { method: 'POST', body: request },
  )
  return loanContractSchema.parse(payload)
}

export async function confirmContractReadiness(
  manager: AuthSessionManager,
  loanApplicationId: string,
  request: ConfirmContractReadinessRequest,
): Promise<LoanContract> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/contracts/current/readiness/confirm`,
    { method: 'POST', body: request },
  )
  return loanContractSchema.parse(payload)
}
