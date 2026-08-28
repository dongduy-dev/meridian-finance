import { QueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/ApiError'

function shouldRetryQuery(failureCount: number, error: Error) {
  const isRetryable = !(error instanceof ApiError) || error.status >= 500
  return failureCount < 1 && isRetryable
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetryQuery,
        staleTime: 30_000,
        gcTime: 5 * 60_000,
      },
      mutations: {
        retry: false,
      },
    },
  })
}
