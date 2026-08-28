import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, type RouterProviderProps } from 'react-router-dom'

import { TooltipProvider } from '@/components/ui/tooltip'

import { createQueryClient } from './query-client'

const queryClient = createQueryClient()

export interface AppProvidersProps {
  router: RouterProviderProps['router']
}

export function AppProviders({ router }: AppProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider delayDuration={250}>
        <RouterProvider router={router} />
      </TooltipProvider>
    </QueryClientProvider>
  )
}
