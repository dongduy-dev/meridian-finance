import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, type RouterProviderProps } from 'react-router-dom'

import { TooltipProvider } from '@/components/ui/tooltip'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { AuthSessionManager, authSessionManager } from '@/features/auth/auth-session'

import { queryClient } from './query-client'

export interface AppProvidersProps {
  router: RouterProviderProps['router']
  authManager?: AuthSessionManager
}

export function AppProviders({ router, authManager = authSessionManager }: AppProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider manager={authManager}>
        <TooltipProvider delayDuration={250}>
          <RouterProvider router={router} />
        </TooltipProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
