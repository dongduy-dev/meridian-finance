import { ArrowRight, Info, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatTimestamp } from '@/lib/format/presentation'

import type { SalaryAdvanceReadiness as SalaryAdvanceReadinessData } from '../salary-advance-api'
import {
  blockerPresentation,
  employeeStatusPresentation,
  limitStatusPresentation,
  partnerStatusPresentation,
} from '../salary-advance-presentation'
import { EmployeeVerificationPanel } from './EmployeeVerificationPanel'

function LimitFact({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-0 rounded-md border border-border bg-background p-4">
      <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-2 min-w-0 text-lg"><MoneyDisplay value={value} /></dd>
    </div>
  )
}

export function SalaryAdvanceLimitSummary({ readiness }: { readiness: SalaryAdvanceReadinessData }) {
  const unavailable = readiness.limitStatus === 'UNAVAILABLE'
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Current Salary Advance limit</CardTitle>
            <CardDescription className="mt-1">Amounts are returned directly by Meridian and are not recalculated in Customer Web.</CardDescription>
          </div>
          <StatusBadge presentation={limitStatusPresentation(readiness.limitStatus)} />
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {unavailable ? (
          <Alert>
            <Info aria-hidden="true" />
            <AlertTitle>Limit values are unavailable</AlertTitle>
            <AlertDescription>Meridian has not returned a usable authoritative limit. Placeholder zero values are not shown as a granted limit.</AlertDescription>
          </Alert>
        ) : (
          <dl className="grid gap-3 sm:grid-cols-2">
            <LimitFact label="Total limit" value={readiness.totalAmount} />
            <LimitFact label="Used" value={readiness.usedAmount} />
            <LimitFact label="Reserved" value={readiness.reservedAmount} />
            <LimitFact label="Available" value={readiness.availableAmount} />
          </dl>
        )}
        {readiness.limitStatus === 'NOT_INITIALIZED' ? (
          <p className="text-sm leading-6 text-muted-foreground">This is an advisory current limit. A durable Salary Advance limit is initialized only through authoritative submission processing.</p>
        ) : null}
        <p className="text-sm leading-6 text-muted-foreground">
          Last Partner evidence refresh: {readiness.lastRefreshAt ? formatTimestamp(readiness.lastRefreshAt) : 'Not available'}
        </p>
      </CardContent>
    </Card>
  )
}

export function ReadinessSummary({ readiness }: { readiness: SalaryAdvanceReadinessData }) {
  const actions = Array.from(
    new Map(
      readiness.blockerCodes
        .map((code) => blockerPresentation(code).action)
        .filter((action): action is { label: string; to: string } => Boolean(action))
        .map((action) => [action.to, action]),
    ).values(),
  )

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Application readiness</CardTitle>
            <CardDescription className="mt-1">Meridian uses current backend facts to decide whether an application can be submitted.</CardDescription>
          </div>
          <StatusBadge presentation={readiness.applicationAllowed
            ? { label: 'Ready to apply', tone: 'success', icon: ShieldCheck }
            : { label: 'Action or waiting required', tone: 'warning', icon: Info }} />
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="flex flex-wrap gap-2" aria-label="Employment and Partner readiness statuses">
          <StatusBadge presentation={employeeStatusPresentation(readiness.employeeVerificationStatus)} />
          <StatusBadge presentation={partnerStatusPresentation(readiness.partnerEligibilityStatus)} />
        </div>
        {readiness.applicationAllowed ? (
          <Alert variant="success" aria-live="polite">
            <ShieldCheck aria-hidden="true" />
            <AlertTitle>Ready to begin</AlertTitle>
            <AlertDescription>Current readiness permits an application. Meridian will re-check all authoritative facts when you submit.</AlertDescription>
          </Alert>
        ) : (
          <div className="space-y-3" aria-live="polite">
            {readiness.blockerCodes.length ? readiness.blockerCodes.map((code, index) => {
              const presentation = blockerPresentation(code)
              return (
                <div key={`${code}-${index}`} className="rounded-md border border-border bg-background p-4">
                  <p className="font-semibold">{presentation.title}</p>
                  <p className="mt-1 text-sm leading-6 text-muted-foreground">{presentation.description}</p>
                </div>
              )
            }) : (
              <div className="rounded-md border border-border bg-background p-4">
                <p className="font-semibold">Readiness unavailable</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">Meridian has not confirmed that an application is currently allowed.</p>
              </div>
            )}
          </div>
        )}
        {actions.length ? (
          <div className="flex flex-wrap gap-3">
            {actions.map((action) => (
              <Button key={action.to} variant="secondary" asChild>
                <Link to={action.to}>{action.label}<ArrowRight aria-hidden="true" /></Link>
              </Button>
            ))}
          </div>
        ) : null}
        <p className="text-xs leading-5 text-muted-foreground">Readiness is advisory and non-locking. A ready view does not guarantee that a later submission succeeds after state changes.</p>
      </CardContent>
    </Card>
  )
}

export function SalaryAdvanceReadiness({
  readiness,
  showApplyAction = false,
  showVerification = true,
}: {
  readiness: SalaryAdvanceReadinessData
  showApplyAction?: boolean
  showVerification?: boolean
}) {
  const [keepVerificationResult, setKeepVerificationResult] = useState(false)
  const needsVerification = readiness.blockerCodes.includes('EMPLOYEE_NOT_VERIFIED')
  const needsReverification = readiness.blockerCodes.includes('SALARY_ADVANCE_ELIGIBILITY_DATA_STALE')
  const applyAvailable = readiness.applicationAllowed && Boolean(readiness.customerPartnerEmployeeLinkId)
  const inconsistentApplyState = readiness.applicationAllowed && !readiness.customerPartnerEmployeeLinkId

  return (
    <div className="space-y-6">
      <div className="grid gap-6 xl:grid-cols-2">
        <ReadinessSummary readiness={readiness} />
        <SalaryAdvanceLimitSummary readiness={readiness} />
      </div>
      {inconsistentApplyState ? (
        <Alert variant="destructive">
          <Info aria-hidden="true" />
          <AlertTitle>Application cannot be started safely</AlertTitle>
          <AlertDescription>Meridian reported readiness without the reusable employment link required for submission. Refresh the page or contact support if this continues.</AlertDescription>
        </Alert>
      ) : null}
      {showVerification && (needsVerification || needsReverification || keepVerificationResult) ? (
        <EmployeeVerificationPanel
          reverify={needsReverification}
          onCompleted={() => setKeepVerificationResult(true)}
        />
      ) : null}
      {showApplyAction && applyAvailable ? (
        <div className="flex justify-end">
          <Button size="lg" asChild>
            <Link to="/products/salary-advance/apply">Apply for Salary Advance<ArrowRight aria-hidden="true" /></Link>
          </Button>
        </div>
      ) : null}
    </div>
  )
}
