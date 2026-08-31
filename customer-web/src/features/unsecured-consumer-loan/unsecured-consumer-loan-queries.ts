import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { applicationKeys } from '@/features/applications/application-queries'
import { useAuth } from '@/features/auth/auth-context'
import { loanProductKeys } from '@/features/loan-products/loan-product-queries'
import { loanKeys } from '@/features/loans/loan-queries'
import { ApiError } from '@/lib/api'

import {
  createUnsecuredConsumerLoanApi,
  type UnsecuredConsumerLoanApplicationInput,
} from './unsecured-consumer-loan-api'

const productChangingErrors = new Set([
  'PRODUCT_NOT_FOUND', 'PRODUCT_INACTIVE', 'PRODUCT_POLICY_INVALID',
  'INVALID_PRODUCT_AMOUNT', 'INVALID_PRODUCT_TERM',
])

export function useSubmitUnsecuredConsumerLoanMutation() {
  const { manager } = useAuth()
  const api = useMemo(() => createUnsecuredConsumerLoanApi(manager), [manager])
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (input: UnsecuredConsumerLoanApplicationInput) => api.submitApplication(input),
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
    onError: (error) => {
      if (!(error instanceof ApiError)) return undefined
      const refreshes: Promise<unknown>[] = []
      if (productChangingErrors.has(error.errorCode)) {
        refreshes.push(queryClient.invalidateQueries({
          queryKey: loanProductKeys.detail('UNSECURED_CONSUMER_LOAN'),
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
  return { ...mutation, submit: mutation.mutateAsync }
}
