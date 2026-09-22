import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  assistedOfferResponseCaseSchema,
  assistedOfferSchema,
  uploadedEvidenceVersionSchema,
  type AssistedOfferCommandPayload,
  type AssistedOfferDecision,
} from './contracts'

export async function getAssistedOfferResponseCase(manager: AuthSessionManager, applicationId: string) {
  return assistedOfferResponseCaseSchema.parse(
    await manager.protectedRequest(`/staff/loan-applications/${applicationId}/offer-response`),
  )
}

export async function uploadOfferResponseEvidence(
  manager: AuthSessionManager,
  applicationId: string,
  offerId: string,
  action: AssistedOfferDecision,
  file: File,
  uploadRequestId: string,
  expectedCurrentVersionId?: string,
) {
  const data = new FormData()
  data.set('file', file)
  data.set('approvedOfferId', offerId)
  data.set('declaredOfferDecision', action)
  data.set('uploadRequestId', uploadRequestId)
  if (expectedCurrentVersionId) data.set('expectedCurrentVersionId', expectedCurrentVersionId)
  return uploadedEvidenceVersionSchema.parse(await manager.protectedRequest(
    `/staff/loan-applications/${applicationId}/assisted-action-evidence/CUSTOMER_OFFER_RESPONSE/versions`,
    { method: 'POST', body: data },
  ))
}

export async function recordAssistedOfferResponse(
  manager: AuthSessionManager,
  payload: AssistedOfferCommandPayload,
  requestId: string,
) {
  return assistedOfferSchema.parse(await manager.protectedRequest(
    `/staff/loan-applications/${payload.loanApplicationId}/offer-response`,
    { method: 'POST', body: {
      requestId,
      expectedApprovedOfferId: payload.expectedApprovedOfferId,
      action: payload.action,
      evidenceDocumentVersionId: payload.evidenceDocumentVersionId,
    } },
  ))
}
