import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { applicationKeys } from '@/features/applications/application-queries'
import { useAuth } from '@/features/auth/auth-context'

import { createDocumentApi, type UploadDocumentInput } from './document-api'

export const documentKeys = {
  all: ['documents'] as const,
  checklist: (loanApplicationId: string) => (
    [...documentKeys.all, 'checklist', loanApplicationId] as const
  ),
}

function useDocumentApi() {
  const { manager } = useAuth()
  return useMemo(() => createDocumentApi(manager), [manager])
}

export function useDocumentChecklistQuery(loanApplicationId: string | undefined) {
  const api = useDocumentApi()
  return useQuery({
    queryKey: documentKeys.checklist(loanApplicationId ?? ''),
    queryFn: () => api.getChecklist(loanApplicationId!),
    enabled: Boolean(loanApplicationId),
  })
}

export function useUploadDocumentMutation() {
  const api = useDocumentApi()
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (input: UploadDocumentInput) => api.uploadDocument(input),
    retry: false,
    onSuccess: (_, input) => Promise.all([
      queryClient.invalidateQueries({ queryKey: documentKeys.checklist(input.loanApplicationId) }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.detail(input.loanApplicationId) }),
    ]),
  })
  return { ...mutation, upload: mutation.mutateAsync }
}
