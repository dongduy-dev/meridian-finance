import { AppProviders } from '@/app/providers/AppProviders'
import { AppRouter } from '@/app/router/router'

export function App() {
  return <AppProviders><AppRouter /></AppProviders>
}
