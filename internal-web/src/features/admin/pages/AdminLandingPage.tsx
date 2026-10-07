import { ShieldCheck } from 'lucide-react'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function AdminLandingPage() {
  return (
    <section className="mx-auto max-w-4xl space-y-6">
      <div>
        <p className="text-sm font-semibold text-muted-foreground">MERIDIAN ADMINISTRATION</p>
        <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Back-Office Administration</h1>
        <p className="mt-2 max-w-2xl text-muted-foreground">Maintain Partner records and employee eligibility, manage lending products, and administer Staff access.</p>
      </div>
      <Card>
        <CardHeader>
          <div className="mb-2 grid size-11 place-items-center rounded-md bg-success-subtle text-success"><ShieldCheck aria-hidden="true" /></div>
          <CardTitle>Welcome back</CardTitle>
          <CardDescription>Keep Partner information, product availability, and Staff role assignments up to date.</CardDescription>
        </CardHeader>
      </Card>
    </section>
  )
}
