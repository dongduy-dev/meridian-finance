import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getStaffReviewCase } from './staff-review-api'

export const staffReviewKeys = {
  all: ['staff-review'] as const,
  case: (loanApplicationId: string) => ['staff-review', 'case', loanApplicationId] as const,
}

export function staffReviewCaseQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffReviewKeys.case(loanApplicationId),
    queryFn: () => getStaffReviewCase(manager, loanApplicationId),
    enabled,
  })
}
