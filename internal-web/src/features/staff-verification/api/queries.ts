import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getStaffVerificationCase } from './staff-verification-api'

export const staffVerificationKeys = {
  all: ['staff-verification'] as const,
  case: (loanApplicationId: string) => ['staff-verification', 'case', loanApplicationId] as const,
}

export function staffVerificationCaseQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffVerificationKeys.case(loanApplicationId),
    queryFn: () => getStaffVerificationCase(manager, loanApplicationId),
    enabled,
  })
}
