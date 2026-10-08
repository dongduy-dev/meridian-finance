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
import { wholeVndAvailability } from '../whole-vnd-availability'
import {
  blockerPresentation,
  employeeStatusPresentation,
  limitStatusPresentation,
  partnerStatusPresentation,
} from '../salary-advance-presentation'
import { EmployeeVerificationPanel } from './EmployeeVerificationPanel'

function LimitFact({ label, value, primary = false }: { label: string; value: number; primary?: boolean }) {
  return (
    <div className="min-w-0 space-y-2 border-t border-border py-4 [overflow-wrap:anywhere]">
      <dt className="text-sm leading-5 text-muted-foreground">{label}</dt>
      <dd className="mt-2 min-w-0 text-lg"><MoneyDisplay value={value} emphasis={primary ? 'primary' : 'inline'} /></dd>
    </div>
  )
}

export function SalaryAdvanceLimitSummary({ readiness }: { readiness: SalaryAdvanceReadinessData }) {
  const wholeVndAvailableAmount = wholeVndAvailability(readiness.availableAmount)
  const unavailable = readiness.limitStatus === 'UNAVAILABLE' || wholeVndAvailableAmount === null
  return (
    <Card className="border-0 bg-transparent [overflow-wrap:anywhere]">
      <CardHeader className="p-0 pb-6 sm:p-0 sm:pb-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Current Salary Advance limit</CardTitle>
            <CardDescription className="mt-1">See how much may be available for a new application.</CardDescription>
          </div>
          <StatusBadge presentation={limitStatusPresentation(readiness.limitStatus)} />
        </div>
      </CardHeader>
      <CardContent className="space-y-4 p-0 sm:p-0">
        {unavailable ? (
          <Alert>
            <Info aria-hidden="true" />
            <AlertTitle>Limit values are unavailable</AlertTitle>
            <AlertDescription>We can't show a usable limit right now. Refresh the page or try again later.</AlertDescription>
          </Alert>
        ) : (
          <dl className="grid gap-x-6 sm:grid-cols-2">
            <LimitFact label="Total limit" value={readiness.totalAmount} />
            <LimitFact label="Used" value={readiness.usedAmount} />
            <LimitFact label="Reserved" value={readiness.reservedAmount} />
            <LimitFact label="Available" value={wholeVndAvailableAmount} primary />
          </dl>
        )}
        {!unavailable && wholeVndAvailableAmount !== readiness.availableAmount ? (
          <p className="text-sm leading-6 text-muted-foreground">Available is shown in whole VND. Product amount limits also apply.</p>
        ) : null}
        {readiness.limitStatus === 'NOT_INITIALIZED' ? (
          <p className="text-sm leading-6 text-muted-foreground">This amount is an estimate. We'll confirm the available limit when you submit.</p>
        ) : null}
        <p className="text-sm leading-6 text-muted-foreground">
          Employment information last updated: {readiness.lastRefreshAt ? formatTimestamp(readiness.lastRefreshAt) : 'Not available'}
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
    <Card className="border-0 bg-transparent [overflow-wrap:anywhere]">
      <CardHeader className="p-0 pb-6 sm:p-0 sm:pb-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Can you apply?</CardTitle>
            <CardDescription className="mt-1">Check whether anything needs your attention before you apply.</CardDescription>
          </div>
          <StatusBadge presentation={readiness.applicationAllowed
            ? { label: 'Ready to apply', tone: 'success', icon: ShieldCheck }
            : { label: 'Not ready to apply', tone: 'warning', icon: Info }} />
        </div>
      </CardHeader>
      <CardContent className="space-y-6 p-0 sm:p-0">
        <div className="flex flex-wrap gap-2" aria-label="Employment verification statuses">
          <StatusBadge presentation={employeeStatusPresentation(readiness.employeeVerificationStatus)} />
          <StatusBadge presentation={partnerStatusPresentation(readiness.partnerEligibilityStatus)} />
        </div>
        {readiness.applicationAllowed ? (
          <Alert variant="success" aria-live="polite">
            <ShieldCheck aria-hidden="true" />
            <AlertTitle>Ready to begin</AlertTitle>
            <AlertDescription>You're ready to start an application. We'll check your information again when you submit.</AlertDescription>
          </Alert>
        ) : (
          <div className="space-y-3" aria-live="polite">
            {readiness.blockerCodes.length ? readiness.blockerCodes.map((code, index) => {
              const presentation = blockerPresentation(code)
              return (
                <div key={`${code}-${index}`} className="border-t border-border py-4">
                  <p className="font-semibold">{presentation.title}</p>
                  <p className="mt-1 text-sm leading-6 text-muted-foreground">{presentation.description}</p>
                </div>
              )
            }) : (
              <div className="border-t border-border py-4">
                <p className="font-semibold">Application status unavailable</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">We can't confirm whether you can apply right now. Refresh the page or try again later.</p>
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
        <p className="text-sm leading-5 text-muted-foreground">We'll check your information again when you submit. Being ready to apply does not guarantee that the application will be accepted.</p>
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
  const [employmentUpdateOpen, setEmploymentUpdateOpen] = useState(false)
  const needsVerification = readiness.blockerCodes.includes('EMPLOYEE_NOT_VERIFIED')
  const needsReverification = readiness.blockerCodes.includes('SALARY_ADVANCE_ELIGIBILITY_DATA_STALE')
  const applyAvailable = readiness.applicationAllowed && Boolean(readiness.customerPartnerEmployeeLinkId)
  const inconsistentApplyState = readiness.applicationAllowed && !readiness.customerPartnerEmployeeLinkId
  const hasCurrentEmployment = Boolean(readiness.customerPartnerEmployeeLinkId)

  return (
    <div className="space-y-[var(--section-transactional)]">
      <div className="grid gap-[var(--section-transactional)] xl:grid-cols-2">
        <ReadinessSummary readiness={readiness} />
        <SalaryAdvanceLimitSummary readiness={readiness} />
      </div>
      {inconsistentApplyState ? (
        <Alert variant="destructive">
          <Info aria-hidden="true" />
          <AlertTitle>Application unavailable</AlertTitle>
          <AlertDescription>We can't start the application with the current employment information. Refresh the page or contact support if this continues.</AlertDescription>
        </Alert>
      ) : null}
      {showVerification && hasCurrentEmployment && !needsVerification && !needsReverification
        && !employmentUpdateOpen ? (
        <div className="flex flex-wrap gap-3 border-t border-border pt-6 sm:justify-end [&>a]:w-full [&>button]:w-full sm:[&>a]:w-auto sm:[&>button]:w-auto">
          <Button variant="secondary" onClick={() => setEmploymentUpdateOpen(true)}>
            Update employment
          </Button>
        </div>
      ) : null}
      {showVerification && (
        needsVerification || needsReverification || keepVerificationResult || employmentUpdateOpen
      ) ? (
        <EmployeeVerificationPanel
          reverify={needsReverification}
          employmentUpdate={employmentUpdateOpen}
          onCompleted={() => setKeepVerificationResult(true)}
        />
      ) : null}
      {showApplyAction && applyAvailable ? (
        <div className="flex flex-wrap gap-3 border-t border-border pt-6 sm:justify-end [&>a]:w-full [&>button]:w-full sm:[&>a]:w-auto sm:[&>button]:w-auto">
          <Button size="lg" asChild>
            <Link to="/products/salary-advance/apply">Apply for Salary Advance<ArrowRight aria-hidden="true" /></Link>
          </Button>
        </div>
      ) : null}
    </div>
  )
}
