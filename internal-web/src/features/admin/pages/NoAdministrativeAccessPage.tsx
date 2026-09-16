import { LockKeyhole, LogOut } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { hasStaffWebAccess } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'

export function NoAdministrativeAccessPage() {
  const { manager, state } = useAuth()
  if (state.status !== 'authenticated') return null

  return (
    <main id="main-content" className="grid min-h-screen place-items-center p-4 sm:p-6">
      <section className="w-full max-w-3xl space-y-5">
        <div>
          <p className="text-sm font-semibold text-muted-foreground">ACCESS STATUS</p>
          <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight">No administrative access</h1>
        </div>
        <Alert variant="warning">
          <LockKeyhole aria-hidden="true" />
          <AlertTitle>Access is not available</AlertTitle>
          <AlertDescription>Your account is authenticated as {state.actor.email}, but it does not hold an exact capability for Back-Office Administration.</AlertDescription>
        </Alert>
        <Card>
          <CardHeader>
            <CardTitle>Session remains protected</CardTitle>
            <CardDescription>Authentication does not imply administrative authorization.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="flex gap-3 text-sm text-muted-foreground">
              <LockKeyhole className="size-5 shrink-0 text-warning" />
              No Partner, Product, User, configuration, or audit data has been loaded.
            </div>
            <div className="flex flex-wrap gap-3">
              {hasStaffWebAccess(state.actor) ? <Button asChild><Link to="/staff">Return to Staff Operations</Link></Button> : null}
              <Button variant={hasStaffWebAccess(state.actor) ? 'outline' : 'default'} onClick={() => void manager.logout()}>
                <LogOut aria-hidden="true" /> Sign out
              </Button>
            </div>
          </CardContent>
        </Card>
      </section>
    </main>
  )
}
