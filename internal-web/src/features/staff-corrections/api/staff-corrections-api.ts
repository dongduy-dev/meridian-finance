import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { staffCorrectionCaseSchema, staffCorrectionTaskSchema } from './contracts'

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

export async function resubmitStaffCorrection(
  manager: AuthSessionManager,
  loanApplicationId: string,
  resubmissionRequestId: string,
) {
  return manager.protectedRequest<unknown>(`/staff-corrections/loan-applications/${loanApplicationId}/resubmit`, {
    method: 'POST', body: { resubmissionRequestId },
  })
}
