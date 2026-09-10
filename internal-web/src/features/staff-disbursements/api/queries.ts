import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { StaffDisbursementWorkFilters } from './contracts'
import { getStaffDisbursementCase, getStaffDisbursementWork } from './staff-disbursements-api'

export const staffDisbursementKeys = {
  all: ['staff-disbursements'] as const,
  queue: (filters: StaffDisbursementWorkFilters) => [
    'staff-disbursements',
    'queue',
    filters.productCode ?? null,
    filters.page,
    filters.size,
  ] as const,
  case: (loanApplicationId: string) => ['staff-disbursements', 'case', loanApplicationId] as const,
}

export function staffDisbursementWorkQuery(
  manager: AuthSessionManager,
  filters: StaffDisbursementWorkFilters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffDisbursementKeys.queue(filters),
    queryFn: () => getStaffDisbursementWork(manager, filters),
    enabled,
    staleTime: 30_000,
  })
}

export function staffDisbursementCaseQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffDisbursementKeys.case(loanApplicationId),
    queryFn: () => getStaffDisbursementCase(manager, loanApplicationId),
    enabled,
  })
}
