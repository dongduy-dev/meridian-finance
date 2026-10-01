import { Check, Clock3, Copy, RefreshCw } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  canAccessStaffRoute,
  STAFF_APPLICATION_CASE_ROUTE,
  STAFF_APPLICATIONS_ROUTE,
  STAFF_CORRECTION_CASE_ROUTE,
  STAFF_DOCUMENT_CASE_ROUTE,
  STAFF_REVIEW_CASE_ROUTE,
  STAFF_VERIFICATION_CASE_ROUTE,
} from '@/app/router/staff-route-metadata'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import type { StaffActor } from '@/features/auth/model/access-control'
import { StatusBadge } from '@/features/staff-applications/components/StatusBadge'
import { humanizeKnownValue, productLabel } from '@/features/staff-applications/model/presentation'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'

export type ApplicationWorkspaceCaseFacts = {
  loanApplicationId: string
  applicationNumber: string
  applicationStatus: string
  productCode: string
  requestedAmount: number
  requestedTermMonths: number
  submittedAt: string
  originationChannel: string
}

type FeatureFacts = Pick<ApplicationWorkspaceCaseFacts, 'loanApplicationId' | 'applicationStatus'>
  & Partial<Omit<ApplicationWorkspaceCaseFacts, 'loanApplicationId' | 'applicationStatus'>>

export type ApplicationWorkspaceContext =
  | { source: 'case'; facts: ApplicationWorkspaceCaseFacts }
  | { source: 'feature'; facts: FeatureFacts }
  | { source: 'document-evidence'; facts: Pick<ApplicationWorkspaceCaseFacts, 'loanApplicationId' | 'applicationStatus' | 'originationChannel'> }

// Select only safe persistent facts; never pass an entire purpose-limited case projection into the header.
export function applicationWorkspaceCaseFacts(data: Omit<ApplicationWorkspaceCaseFacts, 'applicationStatus'> & { status: string }): ApplicationWorkspaceCaseFacts {
  return {
    loanApplicationId: data.loanApplicationId, applicationNumber: data.applicationNumber,
    applicationStatus: data.status, productCode: data.productCode,
    requestedAmount: data.requestedAmount, requestedTermMonths: data.requestedTermMonths,
    submittedAt: data.submittedAt, originationChannel: data.originationChannel,
  }
}

const sections = [
  { key: 'overview', label: 'Overview', route: STAFF_APPLICATION_CASE_ROUTE },
  { key: 'history', label: 'History', route: STAFF_APPLICATION_CASE_ROUTE },
  { key: 'verification', label: 'Verification', route: STAFF_VERIFICATION_CASE_ROUTE },
  { key: 'review', label: 'Review', route: STAFF_REVIEW_CASE_ROUTE },
  { key: 'documents', label: 'Documents', route: STAFF_DOCUMENT_CASE_ROUTE },
  { key: 'corrections', label: 'Corrections', route: STAFF_CORRECTION_CASE_ROUTE },
] as const

export type ApplicationWorkspaceSection = (typeof sections)[number]['key']

export function ApplicationWorkspaceNavigation({ actor, loanApplicationId, activeSection, children }: {
  actor: StaffActor | undefined
  loanApplicationId: string
  activeSection?: ApplicationWorkspaceSection
  children?: ReactNode
}) {
  if (!children && !sections.some(({ route }) => actor && canAccessStaffRoute(actor, route))) return null

  return <nav aria-label="Application sections" className="flex gap-2 overflow-x-auto rounded-lg border bg-card p-2">
    {sections.filter(({ route }) => actor && canAccessStaffRoute(actor, route)).map(({ key, label, route }) => (
      <Link key={key}
        to={`${route.path.replace(':loanApplicationId', loanApplicationId)}${key === 'history' ? '#history' : key === 'overview' ? '#overview' : ''}`}
        aria-current={key === activeSection ? (key === 'history' ? 'location' : 'page') : undefined}
        className={`inline-flex min-h-11 shrink-0 items-center rounded-md px-4 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-ring ${key === activeSection ? 'bg-selected' : 'hover:bg-muted'}`}>
        {label}
      </Link>
    ))}
    {children}
  </nav>
}

export function ApplicationWorkspaceHeader({ context }: { context: ApplicationWorkspaceContext }) {
  const identity: FeatureFacts = context.facts
  const showApplicationId = context.source === 'case' || context.source === 'document-evidence'
  const [copiedId, setCopiedId] = useState<string>()
  const copied = copiedId === identity.loanApplicationId
  const copyId = async () => {
    try {
      await navigator.clipboard.writeText(identity.loanApplicationId)
      setCopiedId(identity.loanApplicationId)
    } catch {
      setCopiedId(undefined)
    }
  }
  const hasSummary = identity.requestedAmount !== undefined || identity.requestedTermMonths !== undefined
    || identity.submittedAt !== undefined || identity.originationChannel !== undefined

  return <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6">
    <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
      <div className="min-w-0">
        <p className="text-sm font-semibold text-muted-foreground">APPLICATION CASE</p>
        <h1 data-route-heading tabIndex={-1} className="mt-1 break-words text-2xl font-semibold tracking-tight sm:text-3xl">{identity.applicationNumber ?? 'Application'}</h1>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <StatusBadge status={identity.applicationStatus} />
          {identity.productCode ? <span className="text-sm text-muted-foreground">{productLabel(identity.productCode)}</span> : null}
        </div>
      </div>
      {showApplicationId ? <div className="flex flex-col gap-2 text-sm lg:items-end">
        <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Application ID</span>
        <code className="max-w-full break-all rounded bg-muted px-2 py-1 text-xs">{identity.loanApplicationId}</code>
        <Button variant="outline" size="sm" onClick={() => void copyId()} aria-label="Copy application ID">
          {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />} {copied ? 'ID copied' : 'Copy application ID'}
        </Button>
      </div> : null}
    </div>
    {hasSummary ? <dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-4">
      {identity.requestedAmount !== undefined ? <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Requested amount</dt><dd className="financial-value mt-1 font-semibold">{formatVnd(identity.requestedAmount)}</dd></div> : null}
      {identity.requestedTermMonths !== undefined ? <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Requested term</dt><dd className="mt-1 font-semibold">{identity.requestedTermMonths} months</dd></div> : null}
      {identity.submittedAt !== undefined ? <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Submitted</dt><dd className="mt-1 font-semibold">{formatTimestamp(identity.submittedAt)}</dd></div> : null}
      {identity.originationChannel !== undefined ? <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Origination channel</dt><dd className="mt-1 font-semibold">{humanizeKnownValue(identity.originationChannel)}</dd></div> : null}
    </dl> : null}
  </header>
}

export function ApplicationWorkspaceShell({ actor, context, activeSection,
  updatedAt, refreshing, stale, onRefresh, contextUnavailable, onRetryContext, navigationExtra, children }: {
  actor: StaffActor | undefined
  context: ApplicationWorkspaceContext
  activeSection?: ApplicationWorkspaceSection
  updatedAt: number
  refreshing: boolean
  stale: boolean
  onRefresh: () => void
  contextUnavailable?: boolean
  onRetryContext?: () => void
  navigationExtra?: ReactNode
  children: ReactNode
}) {
  return <section className="mx-auto max-w-6xl space-y-6">
    {actor && canAccessStaffRoute(actor, STAFF_APPLICATIONS_ROUTE) ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={STAFF_APPLICATIONS_ROUTE.path}>← Back to applications</Link> : null}
    <ApplicationWorkspaceHeader context={context} />
    <div className="flex flex-col gap-3 rounded-lg border bg-card p-4 sm:flex-row sm:items-center sm:justify-between" aria-live="polite">
      <div className="flex items-start gap-3 text-sm">
        <Clock3 aria-hidden="true" className="mt-0.5 size-5 text-information" />
        <div><p className="font-semibold">Last updated</p><p className="text-muted-foreground">Last successful refresh: {updatedAt ? formatTimestamp(new Date(updatedAt).toISOString()) : 'Not refreshed'}</p>{stale ? <p className="mt-1 font-medium text-warning">These details may be out of date. Refresh before taking action.</p> : null}</div>
      </div>
      <Button variant="outline" onClick={onRefresh} disabled={refreshing}>
        {refreshing ? <Spinner /> : <RefreshCw aria-hidden="true" />} {refreshing ? 'Refreshing…' : 'Refresh'}
      </Button>
    </div>
    <ApplicationWorkspaceNavigation actor={actor} loanApplicationId={context.facts.loanApplicationId} activeSection={activeSection}>{navigationExtra}</ApplicationWorkspaceNavigation>
    {contextUnavailable ? <div role="status" className="rounded-lg border border-warning/40 bg-card p-4 text-sm">
      <h2 className="font-semibold">Application context unavailable</h2>
      <p className="mt-1 text-muted-foreground">Some application details could not be refreshed. This section remains available.</p>
      {onRetryContext ? <Button className="mt-3" variant="outline" onClick={onRetryContext}>Retry application context</Button> : null}
    </div> : null}
    {children}
  </section>
}
