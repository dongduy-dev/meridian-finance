import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  cancelledLoanApplicationSchema,
  staffCorrectionCaseSchema,
  staffCorrectionTaskSchema,
  uploadedCancellationEvidenceVersionSchema,
} from './contracts'

export async function getStaffCorrectionQueue(manager: AuthSessionManager, page: number, size: number) {
  const payload = await manager.protectedRequest<unknown>(
    `/staff-corrections/tasks?status=OPEN&page=${page}&size=${size}`,
  )
  return staffCorrectionTaskSchema.array().parse(payload)
}
export async function getStaffCorrectionCase(manager: AuthSessionManager, loanApplicationId: string) {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/corrections`,
  )
  return staffCorrectionCaseSchema.parse(payload)
}

export async function completeStaffCorrectionTask(
  manager: AuthSessionManager,
  taskId: string,
  completionRequestId: string,
) {
  const payload = await manager.protectedRequest<unknown>(`/staff-corrections/tasks/${taskId}/complete`, {
    method: 'POST', body: { completionRequestId },
  })
  return staffCorrectionTaskSchema.parse(payload)
}

export async function completeAssistedCustomerCorrectionTask(
  manager: AuthSessionManager,
  loanApplicationId: string,
  taskId: string,
  completionRequestId: string,
) {
  return manager.protectedRequest<unknown>(
    `/staff-corrections/loan-applications/${loanApplicationId}/customer-tasks/${taskId}/complete`,
    { method: 'POST', body: { completionRequestId } },
  )
}

export async function resubmitStaffCorrection(
  manager: AuthSessionManager,
  loanApplicationId: string,
  resubmissionRequestId: string,
) {
  return manager.protectedRequest<unknown>(`/staff-corrections/loan-applications/${loanApplicationId}/resubmit`, {
    method: 'POST', body: { resubmissionRequestId },
  })
}

export async function uploadAssistedCancellationEvidence(
  manager: AuthSessionManager,
  loanApplicationId: string,
  correctionRequestId: string,
  uploadRequestId: string,
  expectedCurrentVersionId: string | undefined,
  file: File,
) {
  const data = new FormData()
  data.set('file', file)
  data.set('correctionRequestId', correctionRequestId)
  data.set('uploadRequestId', uploadRequestId)
  if (expectedCurrentVersionId) data.set('expectedCurrentVersionId', expectedCurrentVersionId)
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}`
      + '/assisted-action-evidence/CUSTOMER_CANCELLATION_REQUEST/versions',
    { method: 'POST', body: data },
  )
  return uploadedCancellationEvidenceVersionSchema.parse(payload)
}

export async function recordAssistedUclCancellation(
  manager: AuthSessionManager,
  loanApplicationId: string,
  requestId: string,
  expectedCorrectionRequestId: string,
  evidenceDocumentVersionId: string,
) {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/cancellation`,
    {
      method: 'POST',
      body: { requestId, expectedCorrectionRequestId, evidenceDocumentVersionId },
    },
  )
  return cancelledLoanApplicationSchema.parse(payload)
}
