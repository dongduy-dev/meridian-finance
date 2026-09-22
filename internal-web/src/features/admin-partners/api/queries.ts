import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  getPartnerCompanies,
  getPartnerCompany,
  getPartnerEligibilityReview,
  getPartnerEligibilityReviews,
  getPartnerEmployees,
  getPartnerImportBatches,
} from './partner-admin-api'

export const partnerAdminKeys = {
  all: ['admin-partners'] as const,
  companies: () => [...partnerAdminKeys.all, 'companies'] as const,
  company: (id: string) => [...partnerAdminKeys.all, 'company', id] as const,
  employees: (id: string) => [...partnerAdminKeys.all, 'employees', id] as const,
  importBatches: (id: string) => [...partnerAdminKeys.all, 'import-batches', id] as const,
  eligibilityReviews: (page: number, size: number) => [...partnerAdminKeys.all, 'eligibility-reviews', page, size] as const,
  eligibilityReview: (reviewId: string) => [...partnerAdminKeys.all, 'eligibility-review', reviewId] as const,
}

export const partnerCompaniesQuery = (manager: AuthSessionManager, enabled: boolean) => queryOptions({
  queryKey: partnerAdminKeys.companies(), queryFn: () => getPartnerCompanies(manager), enabled,
})

export const partnerCompanyQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({
  queryKey: partnerAdminKeys.company(id), queryFn: () => getPartnerCompany(manager, id), enabled,
})

export const partnerEmployeesQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({
  queryKey: partnerAdminKeys.employees(id), queryFn: () => getPartnerEmployees(manager, id), enabled,
  staleTime: 0, gcTime: 0, refetchOnWindowFocus: true,
})

export const partnerImportBatchesQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({
  queryKey: partnerAdminKeys.importBatches(id), queryFn: () => getPartnerImportBatches(manager, id), enabled,
})

export const partnerEligibilityReviewsQuery = (
  manager: AuthSessionManager,
  page: number,
  size: number,
  enabled: boolean,
) => queryOptions({
  queryKey: partnerAdminKeys.eligibilityReviews(page, size),
  queryFn: () => getPartnerEligibilityReviews(manager, page, size),
  enabled,
  staleTime: 0,
  gcTime: 0,
  refetchOnWindowFocus: true,
})

export const partnerEligibilityReviewQuery = (
  manager: AuthSessionManager,
  reviewId: string,
  enabled: boolean,
) => queryOptions({
  queryKey: partnerAdminKeys.eligibilityReview(reviewId),
  queryFn: () => getPartnerEligibilityReview(manager, reviewId),
  enabled,
  staleTime: 0,
  gcTime: 0,
  refetchOnWindowFocus: true,
})
