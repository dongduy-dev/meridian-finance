import { useRouteError } from 'react-router-dom'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

export function RouteErrorPage() {
  const error = useRouteError()
  return <main className="grid min-h-screen place-items-center p-6"><Alert className="max-w-lg" variant="destructive" title="The page could not be displayed"><p>No data was changed. Reload the workspace to try again.</p><Button className="mt-4" variant="outline" onClick={() => window.location.reload()}>Reload</Button>{import.meta.env.DEV && error instanceof Error ? <p className="mt-3 text-xs">{error.message}</p> : null}</Alert></main>
}
