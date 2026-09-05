import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, History, RefreshCw, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { StatusBadge } from '@/features/staff-applications/components/StatusBadge'
import { applicationStatusOptions, humanizeKnownValue as formatKnownToken, productLabel } from '@/features/staff-applications/model/presentation'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { staffReviewCaseQuery } from '../api/queries'
import { startReview } from '../api/staff-review-api'

type OperationState = { status: OperationStatus; detail?: string; error?: Error }
const knownReviewStatuses = new Set(['ACTIVE', 'COMPLETED', 'SUPERSEDED', 'CORRECTION_REQUIRED', 'CORRECTED'])
const knownVerificationResults = new Set(['VERIFIED', 'FAILED', 'REQUIRES_MORE_INFORMATION', 'PENDING_MANUAL_REVIEW'])

function humanizeKnownValue(value: string): string {
  const known = knownReviewStatuses.has(value)
    || knownVerificationResults.has(value)
    || applicationStatusOptions.some((status) => status === value)
  return known ? formatKnownToken(value) : 'State unavailable'
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-semibold">{children}</dd></div>
}

export function StaffReviewWorkspacePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canReview = state.status === 'authenticated' && hasPermission(state.actor, 'loan:review')
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const query = useQuery(staffReviewCaseQuery(manager, loanApplicationId, canReview && validId))
  const [confirming, setConfirming] = useState(false)
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const data = query.data
  const verifiedProductResult = data?.productReadiness.productVerificationResult === 'VERIFIED'
  const knownCycle = !data?.currentReviewCycle || knownReviewStatuses.has(data.currentReviewCycle.status)
  const startAvailable = Boolean(data?.reviewStartAvailable && data.productReadiness.readyForReview && verifiedProductResult && knownCycle)

  const runStart = async () => {
    if (!data) return
    setConfirming(false)
    setOperation({ status: 'IN_FLIGHT' })
    try {
      await startReview(manager, data.loanApplicationId)
      setOperation({ status: 'RECONCILING' })
      const refreshed = (await query.refetch()).data
      if (canReadCase) await queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all })
      const confirmed = refreshed?.applicationStatus === 'UNDER_REVIEW'
        && refreshed.currentReviewCycle?.status === 'ACTIVE'
      setOperation({ status: confirmed ? 'RESOLVED' : 'BLOCKED', detail: confirmed ? 'The authoritative read confirms an active Loan Officer review cycle.' : 'The refreshed state did not prove that review started.' })
    } catch (error) {
      if (error instanceof ApiError) {
        await query.refetch().catch(() => undefined)
        setOperation({ status: 'BLOCKED', error, detail: 'The backend rejected review start. Review the refreshed readiness evidence.' })
        return
      }
      setOperation({ status: 'RESULT_UNKNOWN', error: error instanceof Error ? error : new NetworkError() })
      setOperation({ status: 'RECONCILING' })
      const refreshed = (await query.refetch().catch(() => undefined))?.data
      const confirmed = refreshed?.applicationStatus === 'UNDER_REVIEW'
        && refreshed.currentReviewCycle?.status === 'ACTIVE'
      setOperation({ status: confirmed ? 'RESOLVED' : 'BLOCKED', detail: confirmed ? 'The authoritative read confirms that review started.' : 'The authoritative read does not confirm review start. No POST was retried.' })
    }
  }

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Review unavailable</h1><Alert variant="warning"><History /><AlertTitle>Review unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading Loan Officer review</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative review evidence…</div></section>
  if (query.isError && !data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loan Officer review</h1><QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} /></section>
  if (!data) return null

  return <section className="mx-auto max-w-6xl space-y-6"><div className="flex flex-wrap gap-4">{canReadCase ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}`}>← Application overview</Link> : null}<Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/verification`}>Verification workspace</Link></div><header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">LOAN OFFICER REVIEW</p><h1 data-route-heading tabIndex={-1} className="mt-1 break-words text-2xl font-semibold sm:text-3xl">{data.applicationNumber}</h1><div className="mt-3 flex flex-wrap items-center gap-3"><StatusBadge status={data.applicationStatus} /><span className="text-sm text-muted-foreground">{productLabel(data.productCode)}</span></div></div><Button variant="outline" onClick={() => void query.refetch()} disabled={query.isFetching}><RefreshCw className={query.isFetching ? 'animate-spin' : undefined} />{query.isFetching ? 'Refreshing…' : 'Refresh'}</Button></div><dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Application ID"><span className="break-all">{data.loanApplicationId}</span></Fact><Fact label="Requested amount">{formatVnd(data.requestedAmount)}</Fact><Fact label="Requested term">{data.requestedTermMonths} months</Fact><Fact label="Submitted">{formatTimestamp(data.submittedAt)}</Fact></dl></header><div className="grid gap-5 lg:grid-cols-2"><Card><CardHeader><CardTitle>Review readiness</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2"><Fact label="Upload completeness">{data.documentReadiness.uploadComplete ? 'Ready' : 'Not ready'}</Fact><Fact label="Document processing">{data.documentReadiness.processingReady ? 'Ready' : 'Not ready'}</Fact><Fact label="Product verification">{humanizeKnownValue(data.productReadiness.productVerificationResult)}</Fact><Fact label="Product ready for review">{data.productReadiness.readyForReview ? 'Ready' : 'Not ready'}</Fact></dl></CardContent></Card><Card><CardHeader><CardTitle>Current review cycle</CardTitle></CardHeader><CardContent>{data.currentReviewCycle ? <dl className="grid gap-4 sm:grid-cols-2"><Fact label="Cycle">{data.currentReviewCycle.cycleNumber}</Fact><Fact label="Status">{knownReviewStatuses.has(data.currentReviewCycle.status) ? humanizeKnownValue(data.currentReviewCycle.status) : 'Review status unavailable'}</Fact><Fact label="Review cycle ID"><span className="break-all">{data.currentReviewCycle.reviewCycleId}</span></Fact><Fact label="Started">{formatTimestamp(data.currentReviewCycle.startedAt)}</Fact><Fact label="Ended">{data.currentReviewCycle.endedAt ? formatTimestamp(data.currentReviewCycle.endedAt) : 'Active'}</Fact></dl> : <p className="text-sm text-muted-foreground">No review cycle has been recorded.</p>}</CardContent></Card></div><Card><CardHeader><CardTitle>Start Loan Officer review</CardTitle><p className="text-sm text-muted-foreground">The backend owns lifecycle and readiness rules. This command has no business UUID, so a network failure is reconciled with the current-cycle read and is never automatically retried.</p></CardHeader><CardContent className="space-y-4">{startAvailable ? <Button id="review-start-trigger" onClick={() => setConfirming(true)}>Start review</Button> : <p className="text-sm text-muted-foreground">Review start is unavailable for the authoritative current state.</p>}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p className="text-sm font-medium" aria-live="polite">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{data.currentReviewCycle?.status === 'ACTIVE' ? <Alert variant="information"><ShieldCheck /><AlertTitle>Review cycle active</AlertTitle><AlertDescription>Recommendation and Approver decision are intentionally outside Staff FE-CP4. This workspace exposes no recommendation controls.</AlertDescription></Alert> : null}</CardContent></Card>{confirming ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="review-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><div><h2 id="review-confirm-title" className="text-xl font-semibold">Confirm review start</h2><p className="mt-1 text-sm text-muted-foreground">The command will revalidate every prerequisite under the Loan workflow lock.</p></div><dl className="grid gap-3 text-sm"><Fact label="Application">{data.applicationNumber}</Fact><Fact label="Product">{productLabel(data.productCode)}</Fact><Fact label="Application status">{humanizeKnownValue(data.applicationStatus)}</Fact><Fact label="Product verification">{humanizeKnownValue(data.productReadiness.productVerificationResult)}</Fact><Fact label="Document processing">{data.documentReadiness.processingReady ? 'Ready' : 'Not ready'}</Fact></dl><div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={() => { setConfirming(false); setTimeout(() => document.getElementById('review-start-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus onClick={() => void runStart()}><CheckCircle2 /> Confirm review start</Button></div></div></div> : null}</section>
}
