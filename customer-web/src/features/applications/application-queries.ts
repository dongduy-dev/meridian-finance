import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { useAuth } from '@/features/auth/auth-context'

import { createApplicationApi } from './application-api'

export const applicationKeys = {
  all: ['applications'] as const,
  index: () => [...applicationKeys.all, 'index'] as const,
  detail: (loanApplicationId: string) => (
    [...applicationKeys.all, 'detail', loanApplicationId] as const
  ),
}

function useApplicationApi() {
  const { manager } = useAuth()
  return useMemo(() => createApplicationApi(manager), [manager])
}

export function useOwnApplicationsQuery() {
  const api = useApplicationApi()
  return useQuery({
    queryKey: applicationKeys.index(),
    queryFn: () => api.getOwnApplications(),
  })
}

export function useOwnApplicationQuery(loanApplicationId: string | undefined) {
  const api = useApplicationApi()
  return useQuery({
    queryKey: applicationKeys.detail(loanApplicationId ?? ''),
    queryFn: () => api.getOwnApplication(loanApplicationId!),
    enabled: Boolean(loanApplicationId),
  })
}

export function useCancelOwnApplicationMutation() {
  const api = useApplicationApi()
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: ({ loanApplicationId, requestId }: { loanApplicationId: string; requestId: string }) => (
      api.cancelOwnApplication(loanApplicationId, requestId)
    ),
    retry: false,
    onSuccess: (_, input) => Promise.all([
      queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.detail(input.loanApplicationId) }),
    ]),
  })
  return { ...mutation, cancel: mutation.mutateAsync }
}
