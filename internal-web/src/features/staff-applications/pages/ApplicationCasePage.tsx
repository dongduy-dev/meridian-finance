import { useQuery } from '@tanstack/react-query'
import { CircleUserRound, History, RefreshCw } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { uuidSchema } from '../api/contracts'
import { staffApplicationCaseQuery } from '../api/queries'
import { QueryErrorPanel } from '../components/QueryErrorPanel'
import { CollateralFactsCard } from '../components/CollateralFactsCard'
import { ApplicationWorkspaceShell, applicationWorkspaceCaseFacts } from '@/components/operations/ApplicationWorkspaceShell'
import {
  applicationStatusLabel,
  humanizeKnownValue,
  productLabel,
  transitionActionLabel,
} from '../model/presentation'

function ReadinessFact({ label, value, positive }: { label: string; value: string; positive: boolean }) {
  return (
    <div className="rounded-md border bg-background p-4">
      <dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-2 flex items-center gap-2 font-semibold">
        <span className={positive ? 'text-success' : 'text-warning'} aria-hidden="true">●</span>
        {value}
      </dd>
    </div>
  )
}

export function ApplicationCasePage() {
  const { manager, state } = useAuth()
  const { loanApplicationId = '' } = useParams()
  const { hash } = useLocation()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const query = useQuery(staffApplicationCaseQuery(manager, loanApplicationId, canRead && validId))
  const data = query.data

  const caseLoaded = Boolean(data)
  useEffect(() => {
    if (!caseLoaded || (hash !== '#history' && hash !== '#overview')) return
    const timer = setTimeout(() => {
      const heading = document.getElementById(hash === '#history' ? 'history-heading' : 'overview-heading')
      heading?.focus()
      heading?.scrollIntoView?.({ block: 'start' })
    }, 0)
    return () => clearTimeout(timer)
  }, [hash, caseLoaded])

  if (!validId) {
    return (
      <section className="mx-auto max-w-5xl space-y-5">
        <h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application unavailable</h1>
        <Alert variant="warning"><History aria-hidden="true" /><AlertTitle>Application unavailable</AlertTitle><AlertDescription>This application link is invalid. Open the application from the application list.</AlertDescription></Alert>
        <Button asChild variant="outline"><Link to="/staff/applications">Back to applications</Link></Button>
      </section>
    )
  }

  if (query.isPending) {
    return (
      <section className="mx-auto max-w-6xl space-y-5">
        <h1 tabIndex={-1} className="text-2xl font-semibold">Loading application case</h1>
        <div role="status" aria-live="polite" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card text-sm text-muted-foreground">
          <Spinner className="size-5" /> Loading application details…
        </div>
      </section>
    )
  }

  if (query.isError && !data) {
    return (
      <section className="mx-auto max-w-6xl space-y-5">
        <h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application case</h1>
        <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} />
        <Button asChild variant="outline"><Link to="/staff/applications">Back to applications</Link></Button>
      </section>
    )
  }

  if (!data) return null

  const readiness = data.customerReadiness

  return (
    <ApplicationWorkspaceShell
      actor={state.status === 'authenticated' ? state.actor : undefined}
      context={{ source: 'case', facts: applicationWorkspaceCaseFacts(data) }}
      activeSection={hash === '#history' ? 'history' : 'overview'}
      updatedAt={query.dataUpdatedAt} refreshing={query.isFetching} stale={query.isStale}
      onRefresh={() => void query.refetch()}
      navigationExtra={
        data.originationChannel === 'STAFF_ASSISTED'
          && data.status === 'CUSTOMER_ACCEPTANCE_PENDING'
          && state.status === 'authenticated'
          && hasPermission(state.actor, 'loan:offer:respond:staff')
          && hasRole(state.actor, 'LOAN_OFFICER')
          ? <Link to={`/staff/applications/${loanApplicationId}/offer-response`} className="inline-flex min-h-11 items-center rounded-md px-4 text-sm font-semibold hover:bg-muted">Customer offer response</Link>
          : null
      }
    >
      {query.isError ? (
        <Alert variant="warning"><RefreshCw aria-hidden="true" /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Previously loaded application details are shown.</AlertDescription></Alert>
      ) : null}

      <section id="overview" className="scroll-mt-4 space-y-4" aria-labelledby="overview-heading">
        <div><p className="text-sm font-semibold text-muted-foreground">CASE OVERVIEW</p><h2 tabIndex={-1} id="overview-heading" className="mt-1 text-xl font-semibold">Application and readiness</h2></div>
        <div className="grid gap-5 lg:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>Application facts</CardTitle></CardHeader>
            <CardContent>
              <dl className="grid gap-4 sm:grid-cols-2">
                <div><dt className="text-sm text-muted-foreground">Application number</dt><dd className="mt-1 font-semibold">{data.applicationNumber}</dd></div>
                <div><dt className="text-sm text-muted-foreground">Application status</dt><dd className="mt-1 font-semibold">{applicationStatusLabel(data.status)}</dd></div>
                <div><dt className="text-sm text-muted-foreground">Product</dt><dd className="mt-1 font-semibold">{productLabel(data.productCode)}</dd></div>
                <div><dt className="text-sm text-muted-foreground">Requested terms</dt><dd className="financial-value mt-1 font-semibold">{formatVnd(data.requestedAmount)} · {data.requestedTermMonths} months</dd></div>
                <div><dt className="text-sm text-muted-foreground">Assigned Loan Officer</dt><dd className="mt-1 font-semibold">{data.assignedLoanOfficer ? <><span className="block">{data.assignedLoanOfficer.displayName}</span><span className="block text-sm font-normal text-muted-foreground">{data.assignedLoanOfficer.email}</span></> : data.formalReviewRecorded ? 'Assignment unavailable' : 'Unassigned'}</dd></div>
              </dl>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Customer readiness</CardTitle><p className="text-sm text-muted-foreground">Check the Customer requirements for this application.</p></CardHeader>
            <CardContent>
              <dl className="grid gap-3 sm:grid-cols-2">
                <ReadinessFact label="Customer state" value={readiness.active ? 'Active' : 'Inactive'} positive={readiness.active} />
                <ReadinessFact label="Profile" value={readiness.profileComplete ? 'Complete' : 'Incomplete'} positive={readiness.profileComplete} />
                <ReadinessFact label="Primary bank account" value={readiness.hasPrimaryActiveBankAccount ? 'Available' : 'Missing'} positive={readiness.hasPrimaryActiveBankAccount} />
                <ReadinessFact label="Verification status" value={humanizeKnownValue(readiness.verificationStatus)} positive={readiness.verificationStatus === 'VERIFIED'} />
              </dl>
            </CardContent>
          </Card>
        </div>
      </section>

      {data.customerContext ? <Card>
        <CardHeader><CardTitle>Customer contact details</CardTitle><p className="text-sm text-muted-foreground">Current contact details are shown here. Application evidence remains unchanged.</p></CardHeader>
        <CardContent><dl className="grid gap-4 sm:grid-cols-3">
          <div><dt className="text-sm text-muted-foreground">Customer number</dt><dd className="mt-1 font-semibold">{data.customerContext.customerNumber}</dd></div>
          <div><dt className="text-sm text-muted-foreground">Name</dt><dd className="mt-1 font-semibold">{data.customerContext.fullName ?? 'Not recorded'}</dd></div>
          <div><dt className="text-sm text-muted-foreground">Phone</dt><dd className="mt-1 font-semibold">{data.customerContext.phoneNumber ?? 'Not recorded'}</dd></div>
        </dl></CardContent>
      </Card> : null}
      {data.productCode === 'COLLATERAL_LOAN' && data.collateralContext ? <CollateralFactsCard collateral={data.collateralContext} /> : null}

      <section id="history" className="scroll-mt-4 space-y-4" aria-labelledby="history-heading">
        <div><p className="text-sm font-semibold text-muted-foreground">APPLICATION HISTORY</p><h2 tabIndex={-1} id="history-heading" className="mt-1 text-xl font-semibold">Application history</h2><p className="mt-1 text-sm text-muted-foreground">Events appear in the order recorded for this application.</p></div>
        <Card>
          <CardContent className="pt-6">
            {data.lifecycleHistory.length === 0 ? <p className="text-sm text-muted-foreground">Application history is unavailable.</p> : (
              <ol className="relative space-y-0 border-l border-border pl-6">
                {data.lifecycleHistory.map((item, index) => (
                  <li key={`${index}-${item.occurredAt}-${item.action}`} className="relative pb-7 last:pb-0">
                    <span className="absolute -left-[1.82rem] top-1 grid size-3 rounded-full border-2 border-card bg-primary" aria-hidden="true" />
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div><p className="font-semibold">{transitionActionLabel(item.action)}</p><p className="mt-1 text-sm text-muted-foreground">{item.fromStatus ? `${applicationStatusLabel(item.fromStatus)} → ` : ''}{applicationStatusLabel(item.toStatus)}</p><p className="mt-1 text-sm text-muted-foreground">{item.actorType === 'SYSTEM' ? 'System' : item.actor ? `${item.actor.displayName} · ${item.actor.email}` : 'User unavailable'}</p></div>
                      <time className="shrink-0 text-sm text-muted-foreground" dateTime={item.occurredAt}>{formatTimestamp(item.occurredAt)}</time>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </CardContent>
        </Card>
      </section>

      <Alert variant="information"><CircleUserRound aria-hidden="true" /><AlertTitle>Application overview</AlertTitle><AlertDescription>This overview remains read-only. Document review and Staff correction actions are available in their dedicated workspaces.</AlertDescription></Alert>
    </ApplicationWorkspaceShell>
  )
}
