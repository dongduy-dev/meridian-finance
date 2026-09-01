import { LockKeyhole, LogOut } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/features/auth/model/auth-context'

export function NoOperationalAccessPage() {
  const { manager, state } = useAuth()
  if (state.status !== 'authenticated') return null

  return (
    <section className="mx-auto max-w-3xl space-y-5">
      <div>
        <p className="text-sm font-semibold text-muted-foreground">ACCESS STATUS</p>
        <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight">No operational access</h1>
      </div>
      <Alert variant="warning">
        <LockKeyhole aria-hidden="true" />
        <AlertTitle>No supported staff capability</AlertTitle>
        <AlertDescription>
          Your account is authenticated as {state.actor.email}, but it does not hold a capability supported by this internal workspace. Contact a Meridian administrator if this is unexpected.
        </AlertDescription>
      </Alert>
      <Card>
        <CardHeader>
          <CardTitle>Session remains protected</CardTitle>
          <CardDescription>Authentication does not imply operational authorization.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="flex gap-3 text-sm text-muted-foreground">
            <LockKeyhole className="size-5 shrink-0 text-warning" />
            No application or customer data has been loaded.
          </div>
          <Button onClick={() => void manager.logout()}>
            <LogOut aria-hidden="true" /> Sign out
          </Button>
        </CardContent>
      </Card>
    </section>
  )
}
