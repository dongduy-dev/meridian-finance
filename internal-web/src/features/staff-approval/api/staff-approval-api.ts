import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  staffApprovalQueuePageSchema,
  staffDecisionCaseSchema,
  type ApprovalDecisionRequest,
  type StaffApprovalQueueFilters,
  type StaffApprovalQueuePage,
  type StaffDecisionCase,
} from './contracts'

export async function getStaffApprovalQueue(
  manager: AuthSessionManager,
  filters: StaffApprovalQueueFilters,
): Promise<StaffApprovalQueuePage> {
  const search = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.productCode) search.set('productCode', filters.productCode)
  const payload = await manager.protectedRequest<unknown>(`/staff/approval-work?${search}`)
  return staffApprovalQueuePageSchema.parse(payload)
}

export async function getStaffDecisionCase(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<StaffDecisionCase> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/decision`,
  )
  return staffDecisionCaseSchema.parse(payload)
}

export async function submitApprovalDecision(
  manager: AuthSessionManager,
  loanApplicationId: string,
  request: ApprovalDecisionRequest,
): Promise<void> {
  await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/approval-decisions`,
    { method: 'POST', body: request },
  )
}
