import { CircleCheckBig, Info, PanelsTopLeft, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

const brandTokens = [
  { name: 'Navy leads', className: 'bg-primary text-primary-foreground' },
  { name: 'Gold accents', className: 'bg-accent text-accent-foreground' },
  { name: 'Ivory breathes', className: 'border border-border bg-background text-foreground' },
]

export function FoundationOverviewPage() {
  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="FE-CP1 · Development preview"
        title="Customer Web foundation"
        description="A live review surface for Meridian branding, reusable interface states, and responsive layout behavior. No Customer or lending data is loaded."
        actions={
          <Button asChild>
            <Link to="/login">Review Login</Link>
          </Button>
        }
      />

      <Alert>
        <Info aria-hidden="true" />
        <AlertTitle>Foundation scope only</AlertTitle>
        <AlertDescription>
          Customer authentication is now live. Account data, product integration,
          applications, and loans remain honest placeholders until their checkpoints.
        </AlertDescription>
      </Alert>

      <section aria-labelledby="brand-heading" className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle id="brand-heading">Meridian visual hierarchy</CardTitle>
            <CardDescription>
              Approved anchors flow through semantic tokens rather than page-specific values.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-3">
            {brandTokens.map((token) => (
              <div
                key={token.name}
                className={`flex min-h-28 items-end rounded-md p-4 text-sm font-semibold ${token.className}`}
              >
                {token.name}
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between gap-3">
              <CardTitle>Foundation status</CardTitle>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon" aria-label="About foundation status">
                    <ShieldCheck aria-hidden="true" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Reusable structure, not business capability</TooltipContent>
              </Tooltip>
            </div>
            <CardDescription>Shared delivery pieces established by FE-CP1.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {['Brand system', 'Layout templates', 'Transport boundary'].map((label) => (
              <div key={label} className="flex items-center gap-3 text-sm font-medium">
                <CircleCheckBig aria-hidden="true" className="size-5 text-success" />
                <span>{label}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      </section>

      <section aria-labelledby="states-heading" className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle id="states-heading">Reusable loading treatment</CardTitle>
            <CardDescription>
              Shape-matched skeletons preserve context without pretending data exists.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Skeleton className="h-5 w-2/5" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-4/5" />
            <Separator />
            <div className="flex gap-3">
              <Skeleton className="size-11" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="h-4 w-2/3" />
              </div>
            </div>
          </CardContent>
        </Card>

        <EmptyState
          icon={PanelsTopLeft}
          title="No business data in this checkpoint"
          description="This neutral state demonstrates shared composition while keeping product and Customer facts out of the frontend foundation."
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <Button variant="secondary" asChild>
                <Link to="/foundation/flow">Focused flow</Link>
              </Button>
              <Button variant="ghost" asChild>
                <Link to="/foundation/detail">Detail layout</Link>
              </Button>
            </div>
          }
        />
      </section>
    </div>
  )
}
