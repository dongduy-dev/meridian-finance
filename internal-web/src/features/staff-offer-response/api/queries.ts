import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getAssistedOfferResponseCase } from './staff-offer-response-api'

export const staffOfferResponseKeys = { all: ['staff-offer-response'] as const }

export function assistedOfferResponseCaseQuery(
  manager: AuthSessionManager, applicationId: string, enabled: boolean,
) {
  return queryOptions({
    queryKey: [...staffOfferResponseKeys.all, applicationId],
    queryFn: () => getAssistedOfferResponseCase(manager, applicationId),
    enabled,
    staleTime: 0,
    retry: false,
  })
}
