import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { applicationKeys } from '@/features/applications/application-queries'
import { useAuth } from '@/features/auth/auth-context'

import { createContractApi, type AcknowledgeLoanContractRequest } from './contract-api'

export const contractKeys = {
  all: ['contracts'] as const,
  current: (loanApplicationId: string) => [...contractKeys.all, 'current', loanApplicationId] as const,
}

function useContractApi() {
  const { manager } = useAuth()
  return useMemo(() => createContractApi(manager), [manager])
}

export function useCurrentContractQuery(loanApplicationId: string | undefined) {
  const api = useContractApi()
  return useQuery({
    queryKey: contractKeys.current(loanApplicationId ?? ''),
    queryFn: () => api.getCurrentContract(loanApplicationId!),
    enabled: Boolean(loanApplicationId),
  })
}

export function useAcknowledgeCurrentContractMutation() {
  const api = useContractApi()
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (input: { loanApplicationId: string; request: AcknowledgeLoanContractRequest }) => (
      api.acknowledgeCurrentContract(input.loanApplicationId, input.request)
    ),
    retry: false,
    onSuccess: (_, input) => Promise.all([
      queryClient.invalidateQueries({ queryKey: contractKeys.current(input.loanApplicationId) }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.detail(input.loanApplicationId) }),
    ]),
  })
  return { ...mutation, acknowledge: mutation.mutateAsync }
}
