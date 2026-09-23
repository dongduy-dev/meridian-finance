import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  partnerCompanySchema,
  partnerEmployeeSchema,
  partnerImportBatchSchema,
  partnerImportResultSchema,
  partnerEligibilityReviewSchema,
  partnerEligibilityReviewPageSchema,
  type CreatePartnerCompanyInput,
  type ImportPartnerEmployeesInput,
  type PartnerCompany,
  type PartnerEmployee,
  type PartnerImportBatch,
  type PartnerImportResult,
  type UpdatePartnerCompanyInput,
  type PartnerEligibilityReview,
  type PartnerEligibilityReviewPage,
  type PartnerEligibilityReviewDecision,
} from './contracts'

export async function getPartnerCompanies(manager: AuthSessionManager): Promise<PartnerCompany[]> {
  return partnerCompanySchema.array().parse(await manager.protectedRequest<unknown>('/partner-companies'))
}

export async function getPartnerCompany(manager: AuthSessionManager, id: string): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}`))
}

export async function getPartnerEmployees(manager: AuthSessionManager, id: string): Promise<PartnerEmployee[]> {
  return partnerEmployeeSchema.array().parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/employees?activeOnly=false`))
}

export async function getPartnerImportBatches(manager: AuthSessionManager, id: string): Promise<PartnerImportBatch[]> {
  return partnerImportBatchSchema.array().parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/employee-import-batches`))
}

export async function createPartnerCompany(manager: AuthSessionManager, input: CreatePartnerCompanyInput): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>('/partner-companies', { method: 'POST', body: input }))
}

export async function updatePartnerCompany(manager: AuthSessionManager, id: string, input: UpdatePartnerCompanyInput): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}`, { method: 'PUT', body: input }))
}

export async function changePartnerCompanyStatus(manager: AuthSessionManager, id: string, status: string): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/status`, { method: 'POST', body: { status } }))
}

export async function importPartnerEmployees(
  manager: AuthSessionManager,
  id: string,
  requestId: string,
  input: ImportPartnerEmployeesInput,
): Promise<PartnerImportResult> {
  return partnerImportResultSchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/employee-import-batches`, {
    method: 'POST',
    body: { requestId, ...input },
  }))
}

export async function getPartnerEligibilityReviews(
  manager: AuthSessionManager,
  page = 0,
  size = 20,
): Promise<PartnerEligibilityReviewPage> {
  return partnerEligibilityReviewPageSchema.parse(await manager.protectedRequest<unknown>(
    `/admin/partner-eligibility-reviews?status=PENDING&page=${page}&size=${size}`,
  ))
}

export async function getPartnerEligibilityReview(
  manager: AuthSessionManager,
  reviewId: string,
): Promise<PartnerEligibilityReview> {
  return partnerEligibilityReviewSchema.parse(await manager.protectedRequest<unknown>(
    `/admin/partner-eligibility-reviews/${reviewId}`,
  ))
}

export async function decidePartnerEligibilityReview(
  manager: AuthSessionManager,
  reviewId: string,
  decision: PartnerEligibilityReviewDecision,
): Promise<PartnerEligibilityReview> {
  return partnerEligibilityReviewSchema.parse(await manager.protectedRequest<unknown>(
    `/admin/partner-eligibility-reviews/${reviewId}/decision`,
    { method: 'POST', body: decision },
  ))
}
