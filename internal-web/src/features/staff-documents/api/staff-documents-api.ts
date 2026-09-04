import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { ApiBinaryResponse } from '@/lib/api'
import {
  documentReviewQueueItemSchema,
  documentReviewResultSchema,
  staffDocumentChecklistSchema,
} from './contracts'

export type ReviewDocumentInput = {
  reviewRequestId: string
  documentVersionId: string
  outcome: 'ACCEPT_DOCUMENT' | 'WAIVE_DOCUMENT' | 'REQUEST_REPLACEMENT'
  waiverReasonCode?: string
  restrictedStaffNotes?: string
  correctionReasonCode?: string
  customerInstruction?: string
}
export async function getDocumentReviewQueue(manager: AuthSessionManager, page: number, size: number) {
  const payload = await manager.protectedRequest<unknown>(
    `/document-review-items?status=AWAITING_REVIEW&page=${page}&size=${size}`,
  )
  return documentReviewQueueItemSchema.array().parse(payload)
}

export async function getStaffDocumentChecklist(manager: AuthSessionManager, loanApplicationId: string) {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/documents`,
  )
  return staffDocumentChecklistSchema.parse(payload)
}

export async function getDocumentContent(
  manager: AuthSessionManager,
  loanApplicationId: string,
  checklistItemId: string,
  documentVersionId: string,
) {
  return manager.protectedRequest<ApiBinaryResponse>(
    `/staff/loan-applications/${loanApplicationId}/documents/${checklistItemId}/versions/${documentVersionId}/content`,
    { responseType: 'blob' },
  )
}

export async function reviewDocument(
  manager: AuthSessionManager,
  loanApplicationId: string,
  checklistItemId: string,
  input: ReviewDocumentInput,
) {
  const payload = await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/document-review-items/${checklistItemId}/reviews`,
    { method: 'POST', body: input },
  )
  return documentReviewResultSchema.parse(payload)
}

export async function uploadStaffDocument(
  manager: AuthSessionManager,
  loanApplicationId: string,
  checklistItemId: string,
  uploadRequestId: string,
  expectedCurrentVersionId: string | null,
  file: File,
) {
  const body = new FormData()
  body.set('uploadRequestId', uploadRequestId)
  if (expectedCurrentVersionId) body.set('expectedCurrentVersionId', expectedCurrentVersionId)
  body.set('file', file)
  return manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/documents/${checklistItemId}/versions`,
    { method: 'POST', body },
  )
}
