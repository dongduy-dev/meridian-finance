import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return <main className="grid min-h-screen place-items-center p-6 text-center"><div className="space-y-4"><p className="text-sm font-semibold text-muted-foreground">404</p><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Page not found</h1><p className="text-muted-foreground">This route is not available in the Meridian staff foundation.</p><Button asChild><Link to="/staff">Return to staff workspace</Link></Button></div></main>
}
