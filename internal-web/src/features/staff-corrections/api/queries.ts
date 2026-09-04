import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getStaffCorrectionCase, getStaffCorrectionQueue } from './staff-corrections-api'

export const staffCorrectionKeys = {
  all: ['staff-corrections'] as const,
  queue: (page: number, size: number) => ['staff-corrections', 'queue', 'OPEN', page, size] as const,
  case: (loanApplicationId: string) => ['staff-corrections', 'case', loanApplicationId] as const,
}

export function staffCorrectionQueueQuery(manager: AuthSessionManager, page: number, size: number, enabled: boolean) {
  return queryOptions({
    queryKey: staffCorrectionKeys.queue(page, size),
    queryFn: () => getStaffCorrectionQueue(manager, page, size),
    enabled,
  })
}

export function staffCorrectionCaseQuery(manager: AuthSessionManager, loanApplicationId: string, enabled: boolean) {
  return queryOptions({
    queryKey: staffCorrectionKeys.case(loanApplicationId),
    queryFn: () => getStaffCorrectionCase(manager, loanApplicationId),
    enabled,
  })
}
