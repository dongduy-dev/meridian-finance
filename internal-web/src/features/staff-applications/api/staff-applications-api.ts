import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  staffLoanApplicationCaseSchema,
  staffLoanApplicationPageSchema,
  type StaffLoanApplicationCase,
  type StaffLoanApplicationPage,
} from './contracts'

export type StaffApplicationFilters = {
  productCode?: string
  status?: string
  page: number
  size: number
}

export async function getStaffLoanApplications(
  manager: AuthSessionManager,
  filters: StaffApplicationFilters,
): Promise<StaffLoanApplicationPage> {
  const search = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.productCode) search.set('productCode', filters.productCode)
  if (filters.status) search.set('status', filters.status)
  const payload = await manager.protectedRequest<unknown>(`/staff/loan-applications?${search}`)
  return staffLoanApplicationPageSchema.parse(payload)
}

export async function getStaffLoanApplicationCase(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<StaffLoanApplicationCase> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}`,
  )
  return staffLoanApplicationCaseSchema.parse(payload)
}
