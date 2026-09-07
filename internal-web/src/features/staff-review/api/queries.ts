import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getStaffRecommendationCase, getStaffReviewCase } from './staff-review-api'

export const staffReviewKeys = {
  all: ['staff-review'] as const,
  case: (loanApplicationId: string) => ['staff-review', 'case', loanApplicationId] as const,
  recommendation: (loanApplicationId: string) => ['staff-review', 'recommendation', loanApplicationId] as const,
}

export function staffRecommendationCaseQuery(
  manager: AuthSessionManager,
  loanApplicationId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: staffReviewKeys.recommendation(loanApplicationId),
    queryFn: () => getStaffRecommendationCase(manager, loanApplicationId),
    enabled,
  })
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
