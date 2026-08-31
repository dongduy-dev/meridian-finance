import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { applicationKeys } from '@/features/applications/application-queries'
import { useAuth } from '@/features/auth/auth-context'

import { createCorrectionApi } from './correction-api'

export const correctionKeys = {
  all: ['corrections'] as const,
  tasks: (loanApplicationId: string) => (
    [...correctionKeys.all, 'tasks', loanApplicationId] as const
  ),
}

function useCorrectionApi() {
  const { manager } = useAuth()
  return useMemo(() => createCorrectionApi(manager), [manager])
}

export function useCorrectionTasksQuery(loanApplicationId: string | undefined) {
  const api = useCorrectionApi()
  return useQuery({
    queryKey: correctionKeys.tasks(loanApplicationId ?? ''),
    queryFn: () => api.getOwnTasks(loanApplicationId!),
    enabled: Boolean(loanApplicationId),
  })
}

function invalidateCorrectionState(queryClient: ReturnType<typeof useQueryClient>, loanApplicationId: string) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: correctionKeys.tasks(loanApplicationId) }),
    queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
    queryClient.invalidateQueries({ queryKey: applicationKeys.detail(loanApplicationId) }),
  ])
}

export function useCompleteCorrectionTaskMutation() {
  const api = useCorrectionApi()
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (input: { loanApplicationId: string; taskId: string; completionRequestId: string }) => (
      api.completeOwnTask(input.loanApplicationId, input.taskId, input.completionRequestId)
    ),
    retry: false,
    onSuccess: (_, input) => invalidateCorrectionState(queryClient, input.loanApplicationId),
  })
  return { ...mutation, complete: mutation.mutateAsync }
}

export function useResubmitCorrectionMutation() {
  const api = useCorrectionApi()
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (input: { loanApplicationId: string; resubmissionRequestId: string }) => (
      api.resubmitOwnCorrection(input.loanApplicationId, input.resubmissionRequestId)
    ),
    retry: false,
    onSuccess: (_, input) => invalidateCorrectionState(queryClient, input.loanApplicationId),
  })
  return { ...mutation, resubmit: mutation.mutateAsync }
}
