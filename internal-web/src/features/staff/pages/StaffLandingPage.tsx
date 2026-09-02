import { ShieldCheck } from 'lucide-react'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function StaffLandingPage() {
  return <section className="mx-auto max-w-4xl space-y-6"><div><p className="text-sm font-semibold text-muted-foreground">MERIDIAN STAFF</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Internal operations</h1><p className="mt-2 max-w-2xl text-muted-foreground">Use the permitted navigation to enter a supported lending work area. This landing remains intentionally free of invented dashboard counts.</p></div><Card><CardHeader><div className="mb-2 grid size-11 place-items-center rounded-md bg-success-subtle text-success"><ShieldCheck aria-hidden="true" /></div><CardTitle>Secure session established</CardTitle><CardDescription>Your Staff identity and explicit capabilities were accepted. Application discovery appears only when your session has the exact loan:read capability.</CardDescription></CardHeader></Card></section>
}
