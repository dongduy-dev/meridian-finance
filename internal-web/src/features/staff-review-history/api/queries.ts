import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { staffReviewHistorySchema } from './contracts'

export const staffReviewHistoryKeys = {
  all: ['staff-review-history'] as const,
  case: (id: string) => ['staff-review-history', id] as const,
}

export function staffReviewHistoryQuery(manager: AuthSessionManager, id: string, enabled: boolean) {
  return queryOptions({
    queryKey: staffReviewHistoryKeys.case(id),
    queryFn: async () => staffReviewHistorySchema.parse(
      await manager.protectedRequest<unknown>(`/staff/loan-applications/${id}/review-history`),
    ),
    enabled,
    gcTime: 0,
  })
}
