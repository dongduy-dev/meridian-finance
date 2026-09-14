import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { StaffServicingWorkFilters, StaffTerminalWorkFilters } from './contracts'
import { getApprovedSettlementEvidence, getLoanAccount, getRepaymentHistory, getStaffClosureWork, getStaffServicingWork, getStaffSettlementWork } from './staff-servicing-api'

export const staffServicingKeys = {
  all: ['staff-servicing'] as const,
  queue: (filters: StaffServicingWorkFilters) => [
    'staff-servicing',
    'queue',
    filters.productCode ?? null,
    filters.accountStatus ?? null,
    filters.page,
    filters.size,
  ] as const,
  account: (loanApplicationId: string) => ['staff-servicing', 'account', loanApplicationId] as const,
  history: (loanApplicationId: string, page: number, size: number) => [
    'staff-servicing', 'history', loanApplicationId, page, size,
  ] as const,
  settlementQueue: (filters: StaffTerminalWorkFilters) => [
    'staff-servicing', 'settlement-queue', filters.productCode ?? null, filters.page, filters.size,
  ] as const,
  closureQueue: (filters: StaffTerminalWorkFilters) => [
    'staff-servicing', 'closure-queue', filters.productCode ?? null, filters.page, filters.size,
  ] as const,
  approvedSettlement: (loanApplicationId: string) => [
    'staff-servicing', 'approved-settlement', loanApplicationId,
  ] as const,
}

export function staffSettlementWorkQuery(
  manager: AuthSessionManager,
  filters: StaffTerminalWorkFilters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffServicingKeys.settlementQueue(filters),
    queryFn: () => getStaffSettlementWork(manager, filters),
    enabled,
  })
}

export function staffClosureWorkQuery(
  manager: AuthSessionManager,
  filters: StaffTerminalWorkFilters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffServicingKeys.closureQueue(filters),
    queryFn: () => getStaffClosureWork(manager, filters),
    enabled,
  })
}

export function approvedSettlementEvidenceQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffServicingKeys.approvedSettlement(loanApplicationId),
    queryFn: () => getApprovedSettlementEvidence(manager, loanApplicationId),
    enabled,
  })
}

export function staffServicingWorkQuery(
  manager: AuthSessionManager,
  filters: StaffServicingWorkFilters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffServicingKeys.queue(filters),
    queryFn: () => getStaffServicingWork(manager, filters),
    enabled,
  })
}

export function loanAccountQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffServicingKeys.account(loanApplicationId),
    queryFn: () => getLoanAccount(manager, loanApplicationId),
    enabled,
  })
}

export function repaymentHistoryQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  page: number,
  size: number,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffServicingKeys.history(loanApplicationId, page, size),
    queryFn: () => getRepaymentHistory(manager, loanApplicationId, page, size),
    enabled,
  })
}
