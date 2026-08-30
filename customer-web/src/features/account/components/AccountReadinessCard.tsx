import { CheckCircle2, CircleAlert, Info, Landmark, UserRound } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { Customer } from '@/features/account/account-api'
import { cn } from '@/lib/cn'

const customerStatusLabels: Record<string, string> = {
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
  DISABLED: 'Disabled',
}

const verificationStatusLabels: Record<string, string> = {
  UNVERIFIED: 'Not verified',
  VERIFIED: 'Verified',
  REJECTED: 'Not approved',
}

function safeLabel(labels: Record<string, string>, value: string) {
  return labels[value] ?? 'Status unavailable'
}

function ReadinessItem({
  complete,
  icon: Icon,
  title,
  description,
  href,
  action,
}: {
  complete: boolean
  icon: typeof UserRound
  title: string
  description: string
  href: string
  action: string
}) {
  const StateIcon = complete ? CheckCircle2 : CircleAlert
  return (
    <div className="flex flex-col gap-4 rounded-md border border-border bg-background p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 gap-3">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-card">
          <Icon aria-hidden="true" className="size-5" />
        </div>
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold">{title}</p>
            <span
              className={cn(
                'inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold',
                complete ? 'bg-success-subtle text-success' : 'bg-warning-subtle text-warning',
              )}
            >
              <StateIcon aria-hidden="true" className="size-3.5" />
              {complete ? 'Complete' : 'Action needed'}
            </span>
          </div>
          <p className="mt-1 text-sm leading-5 text-muted-foreground">{description}</p>
        </div>
      </div>
      {!complete ? (
        <Button variant="secondary" size="sm" asChild>
          <Link to={href}>{action}</Link>
        </Button>
      ) : null}
    </div>
  )
}

export function AccountReadinessCard({ customer }: { customer: Customer }) {
  const profileComplete = customer.profileCompletionStatus === 'COMPLETE'
  return (
    <Card>
      <CardHeader>
        <CardTitle>Account readiness</CardTitle>
        <CardDescription>
          These account setup facts come from Meridian. They do not indicate loan eligibility.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 lg:grid-cols-2">
          <ReadinessItem
            complete={profileComplete}
            icon={UserRound}
            title="Customer profile"
            description={profileComplete ? 'Your required profile details are on file.' : 'Complete the required profile and consent details.'}
            href="/account/profile"
            action="Complete profile"
          />
          <ReadinessItem
            complete={customer.primaryActiveBankAccountPresent}
            icon={Landmark}
            title="Primary bank account"
            description={customer.primaryActiveBankAccountPresent ? 'A primary active bank account is available.' : 'Add or select a primary active bank account.'}
            href="/account/bank-accounts"
            action="Manage accounts"
          />
        </div>
        <div className="flex flex-wrap gap-x-6 gap-y-2 rounded-md bg-information-subtle px-4 py-3 text-sm text-information">
          <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span><strong>Customer status:</strong> {safeLabel(customerStatusLabels, customer.status)}</span>
          <span><strong>Profile verification:</strong> {safeLabel(verificationStatusLabels, customer.verificationStatus)}</span>
        </div>
      </CardContent>
    </Card>
  )
}
