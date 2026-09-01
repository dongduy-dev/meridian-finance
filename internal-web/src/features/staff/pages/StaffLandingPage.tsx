import { LockKeyhole, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasStaffWebAccess } from '@/features/auth/model/access-control'

export function StaffLandingPage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  if (!hasStaffWebAccess(state.actor)) {
    return <section className="mx-auto max-w-3xl space-y-5"><div><p className="text-sm font-semibold text-muted-foreground">ACCESS STATUS</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight">No operational access</h1></div><Alert variant="warning"><LockKeyhole aria-hidden="true" /><AlertTitle>No supported staff capability</AlertTitle><AlertDescription>Your account is authenticated as {state.actor.email}, but it does not hold a capability supported by this internal workspace. Contact a Meridian administrator if this is unexpected.</AlertDescription></Alert><Card><CardHeader><CardTitle>Session remains protected</CardTitle><CardDescription>Authentication does not imply operational authorization.</CardDescription></CardHeader><CardContent className="flex gap-3 text-sm text-muted-foreground"><LockKeyhole className="size-5 shrink-0 text-warning" /> No application or customer data has been loaded.</CardContent></Card></section>
  }
  return <section className="mx-auto max-w-4xl space-y-6"><div><p className="text-sm font-semibold text-muted-foreground">MERIDIAN STAFF</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Internal operations</h1><p className="mt-2 max-w-2xl text-muted-foreground">The staff workspace foundation is ready. Operational queues and lending workflows are deliberately outside this checkpoint.</p></div><Card><CardHeader><div className="mb-2 grid size-11 place-items-center rounded-md bg-success-subtle text-success"><ShieldCheck aria-hidden="true" /></div><CardTitle>Secure session established</CardTitle><CardDescription>Your staff identity and explicit capabilities were accepted. No customer or loan records are displayed in this foundation release.</CardDescription></CardHeader></Card></section>
}
