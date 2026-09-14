import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  loanAccountSchema,
  recordRepaymentResultSchema,
  repaymentHistoryPageSchema,
  staffServicingWorkPageSchema,
  staffSettlementWorkPageSchema,
  staffClosureWorkPageSchema,
  approvedSettlementResultSchema,
  approvedSettlementEvidenceSchema,
  closedLoanAccountResultSchema,
  type LoanAccount,
  type RecordRepaymentRequest,
  type RecordRepaymentResult,
  type RepaymentHistoryPage,
  type StaffServicingWorkFilters,
  type StaffServicingWorkPage,
  type StaffSettlementWorkPage,
  type StaffClosureWorkPage,
  type StaffTerminalWorkFilters,
  type ApproveSettlementRequest,
  type ApprovedSettlementResult,
  type ApprovedSettlementEvidence,
  type ClosedLoanAccountResult,
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

function terminalWorkSearch(filters: StaffTerminalWorkFilters) {
  const search = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.productCode) search.set('productCode', filters.productCode)
  return search
}

export async function getStaffSettlementWork(
  manager: AuthSessionManager,
  filters: StaffTerminalWorkFilters,
): Promise<StaffSettlementWorkPage> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/settlement-work?${terminalWorkSearch(filters)}`,
  )
  return staffSettlementWorkPageSchema.parse(payload)
}

export async function getStaffClosureWork(
  manager: AuthSessionManager,
  filters: StaffTerminalWorkFilters,
): Promise<StaffClosureWorkPage> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/closure-work?${terminalWorkSearch(filters)}`,
  )
  return staffClosureWorkPageSchema.parse(payload)
}

export async function getApprovedSettlementEvidence(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<ApprovedSettlementEvidence> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/settlements/approved`,
  )
  return approvedSettlementEvidenceSchema.parse(payload)
}

export async function approveSettlement(
  manager: AuthSessionManager,
  loanApplicationId: string,
  request: ApproveSettlementRequest,
): Promise<ApprovedSettlementResult> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/settlements`,
    { method: 'POST', body: request },
  )
  return approvedSettlementResultSchema.parse(payload)
}

export async function closeLoanAccount(
  manager: AuthSessionManager,
  loanApplicationId: string,
  requestId: string,
): Promise<ClosedLoanAccountResult> {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/loan-account/closure`,
    { method: 'POST', body: { requestId } },
  )
  return closedLoanAccountResultSchema.parse(payload)
}
