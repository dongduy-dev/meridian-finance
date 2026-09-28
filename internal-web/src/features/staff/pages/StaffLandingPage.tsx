import { ShieldCheck } from 'lucide-react'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function StaffLandingPage() {
  return <section className="mx-auto max-w-4xl space-y-6"><div><p className="text-sm font-semibold text-muted-foreground">MERIDIAN STAFF</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Internal operations</h1><p className="mt-2 max-w-2xl text-muted-foreground">Choose a work area to continue with lending operations available to your Staff account.</p></div><Card><CardHeader><div className="mb-2 grid size-11 place-items-center rounded-md bg-success-subtle text-success"><ShieldCheck aria-hidden="true" /></div><CardTitle>Signed in as Staff</CardTitle><CardDescription>Use the navigation to open applications, reviews, approvals, contracts, disbursements, or servicing work available to you.</CardDescription></CardHeader></Card></section>
}
