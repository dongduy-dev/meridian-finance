import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getAssistedActionEvidence, getDocumentReviewQueue, getStaffDocumentChecklist } from './staff-documents-api'

export const staffDocumentKeys = {
  all: ['staff-documents'] as const,
  queue: (page: number, size: number) => ['staff-documents', 'queue', 'AWAITING_REVIEW', page, size] as const,
  case: (loanApplicationId: string) => ['staff-documents', 'case', loanApplicationId] as const,
  assistedEvidence: (loanApplicationId: string) => ['staff-documents', 'assisted-evidence', loanApplicationId] as const,
}
export function documentReviewQueueQuery(manager: AuthSessionManager, page: number, size: number, enabled: boolean) {
  return queryOptions({
    queryKey: staffDocumentKeys.queue(page, size),
    queryFn: () => getDocumentReviewQueue(manager, page, size),
    enabled,
  })
}

export function staffDocumentCaseQuery(manager: AuthSessionManager, loanApplicationId: string, enabled: boolean) {
  return queryOptions({
    queryKey: staffDocumentKeys.case(loanApplicationId),
    queryFn: () => getStaffDocumentChecklist(manager, loanApplicationId),
    gcTime: 0,
    enabled,
  })
}

export function assistedActionEvidenceQuery(manager: AuthSessionManager, loanApplicationId: string, enabled: boolean) {
  return queryOptions({
    queryKey: staffDocumentKeys.assistedEvidence(loanApplicationId),
    queryFn: () => getAssistedActionEvidence(manager, loanApplicationId),
    enabled,
    gcTime: 0,
  })
}
