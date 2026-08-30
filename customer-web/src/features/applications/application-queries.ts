import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

import { useAuth } from '@/features/auth/auth-context'

import { createApplicationApi } from './application-api'

export const applicationKeys = {
  all: ['applications'] as const,
  index: () => [...applicationKeys.all, 'index'] as const,
}

export function useOwnApplicationsQuery() {
  const { manager } = useAuth()
  const api = useMemo(() => createApplicationApi(manager), [manager])
  return useQuery({
    queryKey: applicationKeys.index(),
    queryFn: () => api.getOwnApplications(),
  })
}
