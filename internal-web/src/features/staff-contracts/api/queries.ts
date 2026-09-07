import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { StaffContractWorkFilters } from './contracts'
import { getStaffContractCase, getStaffContractWork } from './staff-contracts-api'

export const staffContractKeys = {
  all: ['staff-contracts'] as const,
  queue: (filters: StaffContractWorkFilters) => [
    'staff-contracts',
    'queue',
    filters.productCode ?? null,
    filters.page,
    filters.size,
  ] as const,
  case: (loanApplicationId: string) => ['staff-contracts', 'case', loanApplicationId] as const,
}

export function staffContractWorkQuery(
  manager: AuthSessionManager,
  filters: StaffContractWorkFilters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffContractKeys.queue(filters),
    queryFn: () => getStaffContractWork(manager, filters),
    enabled,
    staleTime: 30_000,
  })
}

export function staffContractCaseQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffContractKeys.case(loanApplicationId),
    queryFn: () => getStaffContractCase(manager, loanApplicationId),
    enabled,
  })
}
