import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getCustomer, getIntake, getIntakeOcrReview, getIntakeOcrStatus, listBankAccounts, listEvidence, listOpenIntakes } from './staff-origination-api'

export const originationKeys = {
  all: ['staff-origination'] as const,
  list: () => ['staff-origination', 'list'] as const,
  case: (id: string) => ['staff-origination', 'case', id] as const,
  customer: (id: string) => ['staff-origination', 'customer', id] as const,
  banks: (id: string) => ['staff-origination', 'banks', id] as const,
  evidence: (id: string) => ['staff-origination', 'evidence', id] as const,
  ocrStatus: (caseId: string, evidenceType: string, versionId: string) =>
    ['staff-origination', 'ocr-status', caseId, evidenceType, versionId] as const,
  ocrReview: (caseId: string, evidenceType: string, versionId: string) =>
    ['staff-origination', 'ocr-review', caseId, evidenceType, versionId] as const,
}
export const intakeListQuery = (manager: AuthSessionManager, enabled: boolean) => queryOptions({ queryKey: originationKeys.list(), queryFn: () => listOpenIntakes(manager), enabled })
export const intakeCaseQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.case(id), queryFn: () => getIntake(manager, id), enabled })
export const customerQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.customer(id), queryFn: () => getCustomer(manager, id), enabled })
export const banksQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.banks(id), queryFn: () => listBankAccounts(manager, id), enabled })
export const evidenceQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.evidence(id), queryFn: () => listEvidence(manager, id), enabled })
export const intakeOcrStatusQuery = (manager: AuthSessionManager, caseId: string, evidenceType: string, versionId: string, enabled: boolean) => queryOptions({
  queryKey: originationKeys.ocrStatus(caseId, evidenceType, versionId),
  queryFn: () => getIntakeOcrStatus(manager, caseId, evidenceType, versionId),
  enabled,
  retry: false,
  refetchInterval: (query) => query.state.data?.state === 'PENDING' || query.state.data?.state === 'PROCESSING' ? 1_500 : false,
})
export const intakeOcrReviewQuery = (manager: AuthSessionManager, caseId: string, evidenceType: string, versionId: string, enabled: boolean) => queryOptions({
  queryKey: originationKeys.ocrReview(caseId, evidenceType, versionId),
  queryFn: () => getIntakeOcrReview(manager, caseId, evidenceType, versionId),
  enabled,
  staleTime: 0,
  gcTime: 0,
  retry: false,
})
