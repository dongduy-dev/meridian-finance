import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  staffLoanApplicationCaseSchema,
  staffLoanApplicationPageSchema,
  customerIdentityReferenceRevealSchema,
  type StaffLoanApplicationCase,
  type StaffLoanApplicationPage,
} from './contracts'

export type StaffApplicationFilters = {
  productCode?: string
  status?: string
  applicationNumber?: string
  originationChannel?: string
  submittedFrom?: string
  submittedTo?: string
  page: number
  size: number
}

export async function revealCustomerIdentityReference(
  manager: AuthSessionManager,
  loanApplicationId: string,
) {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/customer-identity-reference/reveal`,
    { method: 'POST' },
    { replayAfterSessionRefresh: false },
  )
  return customerIdentityReferenceRevealSchema.parse(payload)
}

export async function getStaffLoanApplications(
  manager: AuthSessionManager,
  filters: StaffApplicationFilters,
): Promise<StaffLoanApplicationPage> {
  const search = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.productCode) search.set('productCode', filters.productCode)
  if (filters.status) search.set('status', filters.status)
  if (filters.applicationNumber) search.set('applicationNumber', filters.applicationNumber)
  if (filters.originationChannel) search.set('originationChannel', filters.originationChannel)
  if (filters.submittedFrom) search.set('submittedFrom', filters.submittedFrom)
  if (filters.submittedTo) search.set('submittedTo', filters.submittedTo)
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
