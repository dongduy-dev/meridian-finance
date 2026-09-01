import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

import { useAuth } from '@/features/auth/auth-context'

import { createLoanApi } from './loan-api'

export const loanKeys = {
  all: ['loans'] as const,
  index: () => [...loanKeys.all, 'index'] as const,
  detail: (loanApplicationId: string) => (
    [...loanKeys.all, 'detail', loanApplicationId] as const
  ),
  repayments: (loanApplicationId: string, page: number, size: number) => (
    [...loanKeys.all, 'repayments', loanApplicationId, page, size] as const
  ),
}

function useLoanApi() {
  const { manager } = useAuth()
  return useMemo(() => createLoanApi(manager), [manager])
}

export function useOwnLoanAccountsQuery() {
  const api = useLoanApi()
  return useQuery({
    queryKey: loanKeys.index(),
    queryFn: () => api.getOwnLoanAccounts(),
  })
}

export function useLoanAccountQuery(loanApplicationId: string | undefined) {
  const api = useLoanApi()
  return useQuery({
    queryKey: loanKeys.detail(loanApplicationId ?? ''),
    queryFn: () => api.getLoanAccount(loanApplicationId!),
    enabled: Boolean(loanApplicationId),
  })
}

export function useRepaymentHistoryQuery(
  loanApplicationId: string | undefined,
  page: number,
  size: number,
  enabled: boolean,
) {
  const api = useLoanApi()
  return useQuery({
    queryKey: loanKeys.repayments(loanApplicationId ?? '', page, size),
    queryFn: () => api.getRepaymentHistory(loanApplicationId!, page, size),
    enabled: Boolean(loanApplicationId) && enabled,
  })
}
