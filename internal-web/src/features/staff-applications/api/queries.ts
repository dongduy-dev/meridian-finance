import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  getStaffLoanApplicationCase,
  getStaffLoanApplications,
  type StaffApplicationFilters,
} from './staff-applications-api'

export const staffApplicationKeys = {
  all: ['staff-applications'] as const,
  index: (filters: StaffApplicationFilters) => [
    ...staffApplicationKeys.all,
    'index',
    filters.productCode ?? null,
    filters.status ?? null,
    filters.page,
    filters.size,
  ] as const,
  case: (loanApplicationId: string) => [
    ...staffApplicationKeys.all,
    'case',
    loanApplicationId,
  ] as const,
}

export function staffApplicationIndexQuery(
  manager: AuthSessionManager,
  filters: StaffApplicationFilters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffApplicationKeys.index(filters),
    queryFn: () => getStaffLoanApplications(manager, filters),
    enabled,
    staleTime: 30_000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  })
}

export function staffApplicationCaseQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffApplicationKeys.case(loanApplicationId),
    queryFn: () => getStaffLoanApplicationCase(manager, loanApplicationId),
    enabled,
    staleTime: 30_000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  })
}
