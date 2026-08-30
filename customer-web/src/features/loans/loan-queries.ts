import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

import { useAuth } from '@/features/auth/auth-context'

import { createLoanApi } from './loan-api'

export const loanKeys = {
  all: ['loans'] as const,
  index: () => [...loanKeys.all, 'index'] as const,
}

export function useOwnLoanAccountsQuery() {
  const { manager } = useAuth()
  const api = useMemo(() => createLoanApi(manager), [manager])
  return useQuery({
    queryKey: loanKeys.index(),
    queryFn: () => api.getOwnLoanAccounts(),
  })
}
