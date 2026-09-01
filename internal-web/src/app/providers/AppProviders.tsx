import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type PropsWithChildren } from 'react'
import { createQueryClient } from '@/lib/query/query-client'
import { AuthProvider } from '@/features/auth/model/auth-context'

export function AppProviders({ children }: PropsWithChildren) {
  const [queryClient] = useState(createQueryClient)
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
}
