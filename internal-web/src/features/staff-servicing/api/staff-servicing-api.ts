import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  loanAccountSchema,
  recordRepaymentResultSchema,
  repaymentHistoryPageSchema,
  staffServicingWorkPageSchema,
  type LoanAccount,
  type RecordRepaymentRequest,
  type RecordRepaymentResult,
  type RepaymentHistoryPage,
  type StaffServicingWorkFilters,
  type StaffServicingWorkPage,
} from './contracts'

export async function getStaffServicingWork(
  manager: AuthSessionManager,
  filters: StaffServicingWorkFilters,
): Promise<StaffServicingWorkPage> {
  const search = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.productCode) search.set('productCode', filters.productCode)
  if (filters.accountStatus) search.set('accountStatus', filters.accountStatus)
  const payload = await manager.protectedRequest<unknown>(`/staff/servicing-work?${search}`)
  return staffServicingWorkPageSchema.parse(payload)
}

export async function getLoanAccount(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<LoanAccount> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/loan-account`,
  )
  return loanAccountSchema.parse(payload)
}

export async function getRepaymentHistory(
  manager: AuthSessionManager,
  loanApplicationId: string,
  page: number,
  size: number,
): Promise<RepaymentHistoryPage> {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/repayments?${search}`,
  )
  return repaymentHistoryPageSchema.parse(payload)
}

export async function recordRepayment(
  manager: AuthSessionManager,
  loanApplicationId: string,
  request: RecordRepaymentRequest,
): Promise<RecordRepaymentResult> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/repayments`,
    { method: 'POST', body: request },
  )
  return recordRepaymentResultSchema.parse(payload)
}
