import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { applicationKeys } from '@/features/applications/application-queries'
import { useAuth } from '@/features/auth/auth-context'

import { createOfferApi } from './offer-api'

export const offerKeys = {
  all: ['offers'] as const,
  detail: (loanApplicationId: string) => [...offerKeys.all, 'detail', loanApplicationId] as const,
}

function useOfferApi() {
  const { manager } = useAuth()
  return useMemo(() => createOfferApi(manager), [manager])
}

export function useApprovedOfferQuery(loanApplicationId: string | undefined) {
  const api = useOfferApi()
  return useQuery({
    queryKey: offerKeys.detail(loanApplicationId ?? ''),
    queryFn: () => api.getApprovedOffer(loanApplicationId!),
    enabled: Boolean(loanApplicationId),
  })
}

function useOfferResponseMutation(action: 'accept' | 'decline') {
  const api = useOfferApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (loanApplicationId: string) => action === 'accept'
      ? api.acceptApprovedOffer(loanApplicationId)
      : api.declineApprovedOffer(loanApplicationId),
    retry: false,
    onSuccess: (_, loanApplicationId) => Promise.all([
      queryClient.invalidateQueries({ queryKey: offerKeys.detail(loanApplicationId) }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.detail(loanApplicationId) }),
    ]),
  })
}

export function useAcceptApprovedOfferMutation() {
  const mutation = useOfferResponseMutation('accept')
  return { ...mutation, accept: mutation.mutateAsync }
}

export function useDeclineApprovedOfferMutation() {
  const mutation = useOfferResponseMutation('decline')
  return { ...mutation, decline: mutation.mutateAsync }
}
