import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

export function RouteErrorPage() {
  return <main className="grid min-h-screen place-items-center p-6"><Alert className="max-w-lg" variant="destructive" title="The page could not be displayed"><p>Reload to reopen the workspace. If you were recording an action, review its result and recovery guidance before repeating it.</p><Button className="mt-4" variant="outline" onClick={() => window.location.reload()}>Reload workspace</Button></Alert></main>
}
