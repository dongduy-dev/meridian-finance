import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { StaffApprovalQueueFilters } from './contracts'
import { getStaffApprovalQueue, getStaffDecisionCase } from './staff-approval-api'

export const staffApprovalKeys = {
  all: ['staff-approval'] as const,
  queue: (filters: StaffApprovalQueueFilters) => ['staff-approval', 'queue', filters.productCode ?? null, filters.page, filters.size] as const,
  case: (loanApplicationId: string) => ['staff-approval', 'case', loanApplicationId] as const,
}

export function staffApprovalQueueQuery(
  manager: AuthSessionManager,
  filters: StaffApprovalQueueFilters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffApprovalKeys.queue(filters),
    queryFn: () => getStaffApprovalQueue(manager, filters),
    enabled,
    staleTime: 30_000,
  })
}

export function staffDecisionCaseQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffApprovalKeys.case(loanApplicationId),
    queryFn: () => getStaffDecisionCase(manager, loanApplicationId),
    enabled,
  })
}
