import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  staffRecommendationCaseSchema,
  staffReviewCaseSchema,
  type RecommendationRequest,
  type StaffRecommendationCase,
  type StaffReviewCase,
} from './contracts'

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

export async function getStaffRecommendationCase(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<StaffRecommendationCase> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/recommendation`,
  )
  return staffRecommendationCaseSchema.parse(payload)
}

export async function submitRecommendation(
  manager: AuthSessionManager,
  loanApplicationId: string,
  request: RecommendationRequest,
): Promise<void> {
  await manager.protectedRequest<unknown>(
    `/loan-applications/${loanApplicationId}/review-recommendations`,
    { method: 'POST', body: request },
  )
}
