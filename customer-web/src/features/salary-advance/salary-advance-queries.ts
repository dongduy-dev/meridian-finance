import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo } from 'react'

import { useAuth } from '@/features/auth/auth-context'
import { applicationKeys } from '@/features/applications/application-queries'
import { loanProductKeys } from '@/features/loan-products/loan-product-queries'
import { loanKeys } from '@/features/loans/loan-queries'
import { ApiError } from '@/lib/api'

import {
  createSalaryAdvanceApi,
  type EmployeeVerificationInput,
  type OwnEmployeeVerification,
  type SalaryAdvanceApplicationInput,
} from './salary-advance-api'

export const salaryAdvanceKeys = {
  all: ['salary-advance'] as const,
  readiness: () => [...salaryAdvanceKeys.all, 'readiness'] as const,
  partnerOptions: () => [...salaryAdvanceKeys.all, 'partner-verification-options'] as const,
  ownEmployeeVerifications: () => [
    ...salaryAdvanceKeys.all,
    'own-employee-verifications',
  ] as const,
}

export const manualReviewPollIntervalMs = 15_000

export function manualReviewRefetchInterval(data: OwnEmployeeVerification[] | undefined) {
  return data?.some((verification) => verification.manualReviewRequired)
    ? manualReviewPollIntervalMs
    : false
}

function useSalaryAdvanceApi() {
  const { manager } = useAuth()
  return useMemo(() => createSalaryAdvanceApi(manager), [manager])
}

export function useSalaryAdvanceReadinessQuery() {
  const api = useSalaryAdvanceApi()
  return useQuery({
    queryKey: salaryAdvanceKeys.readiness(),
    queryFn: () => api.getReadiness(),
    staleTime: 10_000,
    refetchOnMount: 'always',
  })
}

export function usePartnerVerificationOptionsQuery(enabled = true) {
  const api = useSalaryAdvanceApi()
  return useQuery({
    queryKey: salaryAdvanceKeys.partnerOptions(),
    queryFn: () => api.getPartnerVerificationOptions(),
    enabled,
  })
}

export function useVerifyEmployeeMutation() {
  const api = useSalaryAdvanceApi()
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (input: EmployeeVerificationInput) => api.verifyEmployee(input),
    retry: false,
    onSuccess: (result) => {
      const reviewKey = salaryAdvanceKeys.ownEmployeeVerifications()
      queryClient.setQueryData<OwnEmployeeVerification[]>(reviewKey, (current = []) => {
        const otherCompanies = current.filter(
          (verification) => verification.partnerCompanyId !== result.partnerCompanyId,
        )
        return result.manualReviewRequired
          ? [...otherCompanies, {
              partnerCompanyId: result.partnerCompanyId,
              outcome: 'PENDING_MANUAL_REVIEW',
              manualReviewRequired: true,
            }]
          : otherCompanies
      })
      void queryClient.invalidateQueries({ queryKey: reviewKey, exact: true })
      void queryClient.invalidateQueries({ queryKey: salaryAdvanceKeys.readiness() })
    },
  })

  return {
    ...mutation,
    submit(input: EmployeeVerificationInput) {
      return mutation.mutateAsync(input)
    },
  }
}

export function useOwnEmployeeVerificationsQuery() {
  const api = useSalaryAdvanceApi()
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: salaryAdvanceKeys.ownEmployeeVerifications(),
    queryFn: () => api.getOwnEmployeeVerifications(),
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    refetchInterval: (current) => manualReviewRefetchInterval(current.state.data),
  })
  const terminalOutcomes = query.data
    ?.filter((verification) => !verification.manualReviewRequired)
    .map((verification) => `${verification.partnerCompanyId}:${verification.outcome}`)
    .join('|')

  useEffect(() => {
    if (terminalOutcomes) {
      void queryClient.invalidateQueries({ queryKey: salaryAdvanceKeys.readiness() })
    }
  }, [queryClient, terminalOutcomes])

  return query
}

const readinessChangingErrors = new Set([
  'CUSTOMER_NOT_FOUND',
  'PRODUCT_NOT_FOUND',
  'CUSTOMER_NOT_ACTIVE',
  'PROFILE_INCOMPLETE',
  'PRIMARY_BANK_ACCOUNT_REQUIRED',
  'PRODUCT_INACTIVE',
  'PRODUCT_POLICY_INVALID',
  'INVALID_PRODUCT_AMOUNT',
  'INVALID_PRODUCT_TERM',
  'EMPLOYEE_NOT_VERIFIED',
  'SALARY_ADVANCE_ELIGIBILITY_DATA_STALE',
  'SALARY_ADVANCE_LIMIT_UNAVAILABLE',
  'INSUFFICIENT_AVAILABLE_LIMIT',
  'BLOCKING_APPLICATION_EXISTS',
  'OUTSTANDING_LOAN_ACCOUNT_EXISTS',
  'SYSTEM_STATE_CONFLICT',
])

const productChangingErrors = new Set([
  'PRODUCT_NOT_FOUND',
  'PRODUCT_INACTIVE',
  'PRODUCT_POLICY_INVALID',
  'INVALID_PRODUCT_AMOUNT',
  'INVALID_PRODUCT_TERM',
])

export function useSubmitSalaryAdvanceMutation() {
  const api = useSalaryAdvanceApi()
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (input: SalaryAdvanceApplicationInput) => api.submitApplication(input),
    retry: false,
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: salaryAdvanceKeys.readiness() }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
    ]),
    onError: (error) => {
      if (!(error instanceof ApiError) || !readinessChangingErrors.has(error.errorCode)) {
        return undefined
      }

      const refreshes: Promise<unknown>[] = [
        queryClient.invalidateQueries({ queryKey: salaryAdvanceKeys.readiness() }),
      ]
      if (productChangingErrors.has(error.errorCode)) {
        refreshes.push(queryClient.invalidateQueries({
          queryKey: loanProductKeys.detail('SALARY_ADVANCE'),
        }))
      }
      if (error.errorCode === 'BLOCKING_APPLICATION_EXISTS') {
        refreshes.push(queryClient.invalidateQueries({ queryKey: applicationKeys.index() }))
      }
      if (error.errorCode === 'OUTSTANDING_LOAN_ACCOUNT_EXISTS') {
        refreshes.push(queryClient.invalidateQueries({ queryKey: loanKeys.index() }))
      }
      return Promise.all(refreshes)
    },
  })

  return {
    ...mutation,
    submit(input: SalaryAdvanceApplicationInput) {
      return mutation.mutateAsync(input)
    },
  }
}
