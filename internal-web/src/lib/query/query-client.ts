import { QueryClient } from '@tanstack/react-query'
import { ApiError, NetworkError } from '@/lib/api'

export function shouldRetryRead(failureCount: number, error: unknown): boolean {
  if (failureCount >= 1) return false
  return error instanceof NetworkError || (error instanceof ApiError && error.status >= 500)
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetryRead, staleTime: 30_000, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  })
}
