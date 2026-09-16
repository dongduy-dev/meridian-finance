import { Link } from 'react-router-dom'
import { preferredInternalDestination } from '@/app/router/internal-destination'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/model/auth-context'

export function NotFoundPage() {
  const { state } = useAuth()
  const destination = state.status === 'authenticated' ? preferredInternalDestination(state.actor) : '/login'
  const label = destination === '/admin' ? 'Return to administration' : destination === '/staff' ? 'Return to Staff Operations' : 'Return to sign in'
  return <main className="grid min-h-screen place-items-center p-6 text-center"><div className="space-y-4"><p className="text-sm font-semibold text-muted-foreground">404</p><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Page not found</h1><p className="text-muted-foreground">This route is not available in Meridian Internal Web.</p><Button asChild><Link to={destination}>{label}</Link></Button></div></main>
}
