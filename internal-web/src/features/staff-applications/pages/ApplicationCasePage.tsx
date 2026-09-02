import { useQuery } from '@tanstack/react-query'
import { Check, CircleUserRound, Clock3, Copy, History, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { uuidSchema } from '../api/contracts'
import { staffApplicationCaseQuery } from '../api/queries'
import { QueryErrorPanel } from '../components/QueryErrorPanel'
import { StatusBadge } from '../components/StatusBadge'
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
  const [copied, setCopied] = useState(false)
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const query = useQuery(staffApplicationCaseQuery(manager, loanApplicationId, canRead && validId))
  const data = query.data

  const copyId = async () => {
    try {
      await navigator.clipboard.writeText(loanApplicationId)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  if (!validId) {
    return (
      <section className="mx-auto max-w-5xl space-y-5">
        <h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application unavailable</h1>
        <Alert variant="warning"><History aria-hidden="true" /><AlertTitle>Application unavailable</AlertTitle><AlertDescription>This case route does not contain a valid application identifier.</AlertDescription></Alert>
        <Button asChild variant="outline"><Link to="/staff/applications">Back to applications</Link></Button>
      </section>
    )
  }

  if (query.isPending) {
    return (
      <section className="mx-auto max-w-6xl space-y-5">
        <h1 tabIndex={-1} className="text-2xl font-semibold">Loading application case</h1>
        <div role="status" aria-live="polite" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card text-sm text-muted-foreground">
          <Spinner className="size-5" /> Loading verified case evidence…
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

  const refreshedAt = query.dataUpdatedAt
    ? formatTimestamp(new Date(query.dataUpdatedAt).toISOString())
    : 'Not refreshed'
  const readiness = data.customerReadiness

  return (
    <section className="mx-auto max-w-6xl space-y-6">
      <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/applications">← Back to applications</Link>

      <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-muted-foreground">APPLICATION CASE</p>
            <h1 data-route-heading tabIndex={-1} className="mt-1 break-words text-2xl font-semibold tracking-tight sm:text-3xl">{data.applicationNumber}</h1>
            <div className="mt-3 flex flex-wrap items-center gap-3">
              <StatusBadge status={data.status} />
              <span className="text-sm text-muted-foreground">{productLabel(data.productCode)}</span>
            </div>
          </div>
          <div className="flex flex-col gap-2 text-sm lg:items-end">
            <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Application ID</span>
            <code className="max-w-full break-all rounded bg-muted px-2 py-1 text-xs">{data.loanApplicationId}</code>
            <Button variant="outline" size="sm" onClick={() => void copyId()} aria-label="Copy application ID">
              {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />} {copied ? 'ID copied' : 'Copy application ID'}
            </Button>
          </div>
        </div>
        <dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-4">
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Requested amount</dt><dd className="financial-value mt-1 font-semibold">{formatVnd(data.requestedAmount)}</dd></div>
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Requested term</dt><dd className="mt-1 font-semibold">{data.requestedTermMonths} months</dd></div>
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Product type</dt><dd className="mt-1 font-semibold">{humanizeKnownValue(data.productType)}</dd></div>
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Submitted</dt><dd className="mt-1 font-semibold">{formatTimestamp(data.submittedAt)}</dd></div>
        </dl>
      </header>

      <div className="flex flex-col gap-3 rounded-lg border bg-card p-4 sm:flex-row sm:items-center sm:justify-between" aria-live="polite">
        <div className="flex items-start gap-3 text-sm">
          <Clock3 aria-hidden="true" className="mt-0.5 size-5 text-information" />
          <div><p className="font-semibold">Case evidence freshness</p><p className="text-muted-foreground">Last successful refresh: {refreshedAt}</p>{query.isStale ? <p className="mt-1 font-medium text-warning">This fetch is stale. Refresh before relying on it.</p> : null}</div>
        </div>
        <Button variant="outline" onClick={() => void query.refetch()} disabled={query.isFetching}>
          {query.isFetching ? <Spinner /> : <RefreshCw aria-hidden="true" />} {query.isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      {query.isError ? (
        <Alert variant="warning"><RefreshCw aria-hidden="true" /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>The last successfully validated case remains visible.</AlertDescription></Alert>
      ) : null}

      <nav aria-label="Case workspace" className="flex gap-2 overflow-x-auto rounded-lg border bg-card p-2">
        <a href="#overview" className="inline-flex min-h-11 items-center rounded-md bg-selected px-4 text-sm font-semibold">Overview</a>
        <a href="#history" className="inline-flex min-h-11 items-center rounded-md px-4 text-sm font-semibold hover:bg-muted">History</a>
      </nav>

      <section id="overview" className="scroll-mt-4 space-y-4" aria-labelledby="overview-heading">
        <div><p className="text-sm font-semibold text-muted-foreground">CASE OVERVIEW</p><h2 id="overview-heading" className="mt-1 text-xl font-semibold">Application and readiness</h2></div>
        <div className="grid gap-5 lg:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>Application facts</CardTitle></CardHeader>
            <CardContent>
              <dl className="grid gap-4 sm:grid-cols-2">
                <div><dt className="text-sm text-muted-foreground">Application number</dt><dd className="mt-1 font-semibold">{data.applicationNumber}</dd></div>
                <div><dt className="text-sm text-muted-foreground">Durable status</dt><dd className="mt-1 font-semibold">{applicationStatusLabel(data.status)}</dd></div>
                <div><dt className="text-sm text-muted-foreground">Product</dt><dd className="mt-1 font-semibold">{productLabel(data.productCode)}</dd></div>
                <div><dt className="text-sm text-muted-foreground">Requested terms</dt><dd className="financial-value mt-1 font-semibold">{formatVnd(data.requestedAmount)} · {data.requestedTermMonths} months</dd></div>
              </dl>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Customer readiness</CardTitle><p className="text-sm text-muted-foreground">Purpose-limited lending readiness only. No Customer profile or bank-account values are returned.</p></CardHeader>
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

      <section id="history" className="scroll-mt-4 space-y-4" aria-labelledby="history-heading">
        <div><p className="text-sm font-semibold text-muted-foreground">IMMUTABLE EVIDENCE</p><h2 id="history-heading" className="mt-1 text-xl font-semibold">Lifecycle history</h2><p className="mt-1 text-sm text-muted-foreground">Events appear in the authoritative order returned by Loan.</p></div>
        <Card>
          <CardContent className="pt-6">
            {data.lifecycleHistory.length === 0 ? <p className="text-sm text-muted-foreground">No lifecycle history was returned for this application.</p> : (
              <ol className="relative space-y-0 border-l border-border pl-6">
                {data.lifecycleHistory.map((item, index) => (
                  <li key={`${index}-${item.occurredAt}-${item.action}`} className="relative pb-7 last:pb-0">
                    <span className="absolute -left-[1.82rem] top-1 grid size-3 rounded-full border-2 border-card bg-primary" aria-hidden="true" />
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div><p className="font-semibold">{transitionActionLabel(item.action)}</p><p className="mt-1 text-sm text-muted-foreground">{item.fromStatus ? `${applicationStatusLabel(item.fromStatus)} → ` : ''}{applicationStatusLabel(item.toStatus)} · {humanizeKnownValue(item.actorType)}</p></div>
                      <time className="shrink-0 text-sm text-muted-foreground" dateTime={item.occurredAt}>{formatTimestamp(item.occurredAt)}</time>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </CardContent>
        </Card>
      </section>

      <Alert variant="information"><CircleUserRound aria-hidden="true" /><AlertTitle>Read-only checkpoint</AlertTitle><AlertDescription>This workspace contains discovery, readiness, and lifecycle evidence only. No review, approval, correction, contract, disbursement, or servicing command is available here.</AlertDescription></Alert>
    </section>
  )
}
