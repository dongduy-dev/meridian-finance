import { CheckCircle2, CircleAlert, Info, Landmark, UserRound } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card'
import type { Customer } from '@/features/account/account-api'
import { useOwnIdentityHistory } from '@/features/account/identity-queries'
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
  statusLabel,
}: {
  complete: boolean
  icon: typeof UserRound
  title: string
  description: string
  href: string
  action: string
  statusLabel?: string
}) {
  const StateIcon = complete ? CheckCircle2 : CircleAlert
  return (
    <div className="flex min-w-0 flex-col items-start gap-4 py-6 [overflow-wrap:anywhere]">
      <div className="flex min-w-0 gap-3">
        <div className="shrink-0 pt-0.5">
          <Icon aria-hidden="true" className="size-5" />
        </div>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold">{title}</p>
            <span
              className={cn(
                'inline-flex max-w-full min-w-0 items-center gap-2 rounded-sm px-3 py-1 text-sm leading-5 font-medium',
                complete ? 'bg-success-subtle text-success' : 'bg-warning-subtle text-warning',
              )}
            >
              <StateIcon aria-hidden="true" className="size-4 shrink-0" />
              <span className="min-w-0">{complete ? 'Complete' : statusLabel ?? 'Action needed'}</span>
            </span>
          </div>
          <p className="mt-2 text-sm leading-5 text-muted-foreground">{description}</p>
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

export function AccountReadinessCard({ customer, presentation = 'card' }: {
  customer: Customer
  presentation?: 'card' | 'section'
}) {
  const identity = useOwnIdentityHistory()
  const pending = identity.data?.[0]?.status === 'PENDING_REVIEW'
  const identityDescription = customer.verificationStatus === 'VERIFIED' ? 'Identity verified.' : identity.isPending ? 'Checking your identity review status…' : identity.isError ? 'Identity review status could not be confirmed. Open identity verification and try again.' : pending ? 'Your identity document is being reviewed. No further document is needed right now.' : customer.verificationStatus === 'REJECTED' ? 'Verification could not be completed. Review the reason and submit a replacement document.' : 'Complete your profile, then submit an identity document for review before applying for a loan.'
  const profileComplete = customer.profileCompletionStatus === 'COMPLETE'
  const Heading = presentation === 'section' ? 'h3' : 'h2'
  return (
    <Card className={presentation === 'section' ? 'border-0 border-t bg-transparent' : undefined}>
      <CardHeader className={presentation === 'section' ? 'px-0 pt-6 sm:px-0' : undefined}>
        <Heading className="type-section">Account setup</Heading>
        <CardDescription>
          Complete your profile, verify your identity, and choose a primary bank account. Salary Advance also requires employment verification. These steps do not guarantee loan approval.
        </CardDescription>
      </CardHeader>
      <CardContent className={cn('space-y-6', presentation === 'section' && 'px-0 pb-0 sm:px-0 sm:pb-0')}>
        <div className={cn('grid divide-y divide-border border-y border-border', presentation === 'section' && 'lg:grid-cols-3 lg:divide-x lg:divide-y-0 lg:[&>div]:px-6 lg:[&>div:first-child]:pl-0 lg:[&>div:last-child]:pr-0')}>
          <ReadinessItem
            complete={profileComplete}
            icon={UserRound}
            title="Your profile"
            description={profileComplete ? 'Your required profile details are on file.' : 'Complete the required profile and consent details.'}
            href="/account/profile"
            action="Complete profile"
          />
          <ReadinessItem complete={customer.verificationStatus === 'VERIFIED'} icon={UserRound}
            title="Identity verification" description={identityDescription}
            statusLabel={identity.isPending ? 'Checking status' : identity.isError ? 'Status unavailable' : pending ? 'Under review' : undefined}
            href="/account/identity-verification" action="Open identity verification" />
          <ReadinessItem
            complete={customer.primaryActiveBankAccountPresent}
            icon={Landmark}
            title="Primary bank account"
            description={customer.primaryActiveBankAccountPresent ? 'You have an active primary bank account.' : 'Add a bank account or make an active saved account your primary account.'}
            href="/account/bank-accounts"
            action="Manage bank accounts"
          />
        </div>
        <div className="flex min-w-0 flex-wrap gap-x-6 gap-y-2 text-sm leading-5 text-muted-foreground [overflow-wrap:anywhere]">
          <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span><strong>Account status:</strong> {safeLabel(customerStatusLabels, customer.status)}</span>
          <span><strong>Identity verification:</strong> {safeLabel(verificationStatusLabels, customer.verificationStatus)}</span>
        </div>
      </CardContent>
    </Card>
  )
}
