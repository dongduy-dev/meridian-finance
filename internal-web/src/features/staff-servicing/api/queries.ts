import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { StaffServicingWorkFilters } from './contracts'
import { getLoanAccount, getRepaymentHistory, getStaffServicingWork } from './staff-servicing-api'

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
