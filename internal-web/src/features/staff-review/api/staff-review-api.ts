import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { staffReviewCaseSchema, type StaffReviewCase } from './contracts'

export async function getStaffReviewCase(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<StaffReviewCase> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/review`,
  )
  return staffReviewCaseSchema.parse(payload)
}

export async function startReview(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<void> {
  await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/review/start`,
    { method: 'POST' },
  )
}
