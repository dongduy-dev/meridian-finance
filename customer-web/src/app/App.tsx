import { AppProviders } from '@/app/providers/AppProviders'
import { appRouter } from '@/app/router/router'

export function App() {
  return <AppProviders router={appRouter} />
}
