import { ConfirmationDialog, ConfirmationDialogCancel, ConfirmationDialogTitle, ConfirmationDialogDescription, ConfirmationDialogFooter } from '@/components/ui/confirmation-dialog'
import { ProductAssessmentPanel } from '@/features/staff-verification/components/ProductAssessmentPanel'
import { staffVerificationKeys } from '@/features/staff-verification/api/queries'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ReviewHistoryPanel } from '@/features/staff-review-history/components/ReviewHistoryPanel'
import { staffReviewHistoryKeys } from '@/features/staff-review-history/api/queries'
import { CheckCircle2, History, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { useOperationResultFocus } from '@/components/operations/useOperationResultFocus'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { staffApplicationCaseQuery, staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { ApplicationWorkspaceShell } from '@/components/operations/ApplicationWorkspaceShell'
import { applicationStatusOptions, humanizeKnownValue as formatKnownToken, productLabel } from '@/features/staff-applications/model/presentation'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'
import { staffReviewCaseQuery } from '../api/queries'
import { startReview } from '../api/staff-review-api'
import { RecommendationPanel } from '../components/RecommendationPanel'

type OperationState = { status: OperationStatus; detail?: string; error?: Error }
type PendingReconciliation = { commandError?: Error }
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
  const caseQuery = useQuery(staffApplicationCaseQuery(manager, loanApplicationId, validId && canReview && canReadCase))
  const [confirming, setConfirming] = useState(false)
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const requestResultFocus = useOperationResultFocus(operation.status, 'review-start-result')
  const [pendingReconciliation, setPendingReconciliation] = useState<PendingReconciliation>()
  const data = query.data
  const verifiedProductResult = data?.productReadiness.productVerificationResult === 'VERIFIED'
  const knownCycle = !data?.currentReviewCycle || knownReviewStatuses.has(data.currentReviewCycle.status)
  const startAvailable = Boolean(data?.reviewStartAvailable && data.productReadiness.readyForReview && verifiedProductResult && knownCycle && !pendingReconciliation)
  const assignedToAnotherOfficer = state.status === 'authenticated'
    && Boolean(data?.assignedLoanOfficer && data.assignedLoanOfficer.userId !== state.actor.userId)

  const authoritativeRefresh = async () => {
    const refreshed = await query.refetch({ throwOnError: true })
    await queryClient.invalidateQueries({ queryKey: staffReviewHistoryKeys.case(loanApplicationId) }).catch(() => undefined)
    return refreshed.data
  }

  const reconcile = async (pending: PendingReconciliation) => {
    setPendingReconciliation(pending)
    setOperation({ status: 'RECONCILING' })
    try {
      const refreshed = await authoritativeRefresh()
      setPendingReconciliation(undefined)
      const confirmed = refreshed?.applicationStatus === 'UNDER_REVIEW'
        && refreshed.currentReviewCycle?.status === 'ACTIVE'
      if (confirmed && canReadCase) {
        await queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all }).catch(() => undefined)
      }
      setOperation({
        status: confirmed ? 'RESOLVED' : 'BLOCKED',
        error: confirmed ? undefined : pending.commandError,
        detail: confirmed
          ? 'Review start was confirmed after Meridian refreshed the review.'
          : pending.commandError instanceof ApiError
            ? 'The action was rejected. Review the latest readiness information before deciding whether to try again.'
            : 'Review start was not confirmed. Review the current information before submitting again. Meridian did not submit the action again automatically.',
      })
    } catch (refreshError) {
      const error = pending.commandError ?? (refreshError instanceof Error ? refreshError : new NetworkError())
      setOperation({
        status: pending.commandError instanceof ApiError ? 'BLOCKED' : 'RESULT_UNKNOWN',
        error,
        detail: pending.commandError instanceof ApiError
          ? 'The action was rejected, but the latest review information could not be loaded. Review start remains unavailable; use Refresh before another attempt.'
          : 'Meridian could not confirm the result or load the latest review information. Review start remains unavailable; use Refresh before another attempt.',
      })
    }
  }

  const refreshWorkspace = async () => {
    if (pendingReconciliation) {
      await reconcile(pendingReconciliation)
      return
    }
    await Promise.all([queryClient.invalidateQueries({ queryKey: staffVerificationKeys.case(loanApplicationId) }), queryClient.invalidateQueries({ queryKey: staffReviewHistoryKeys.case(loanApplicationId) }), query.refetch(), ...(canReadCase ? [caseQuery.refetch()] : [])])
  }

  const runStart = async () => {
    if (!data) return
    requestResultFocus()
    setConfirming(false)
    setOperation({ status: 'IN_FLIGHT' })
    let commandError: Error | undefined
    try {
      await startReview(manager, data.loanApplicationId)
    } catch (error) {
      commandError = error instanceof Error ? error : new NetworkError()
      if (!(commandError instanceof ApiError)) setOperation({ status: 'RESULT_UNKNOWN', error: commandError })
    }
    await reconcile({ commandError })
  }

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Review unavailable</h1><Alert variant="warning"><History /><AlertTitle>Review unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 tabIndex={-1} className="text-2xl font-semibold">Loading Loan Officer review</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading review evidence…</div></section>
  if (query.isError && !data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loan Officer review</h1><QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} /></section>
  if (!data) return null

  const featureFacts = {
    loanApplicationId: data.loanApplicationId, applicationNumber: data.applicationNumber,
    applicationStatus: data.applicationStatus, productCode: data.productCode,
    requestedAmount: data.requestedAmount, requestedTermMonths: data.requestedTermMonths,
    submittedAt: data.submittedAt,
  }
  const headerCase = canReadCase && caseQuery.isSuccess ? caseQuery.data : undefined

  return <ApplicationWorkspaceShell
    actor={state.status === 'authenticated' ? state.actor : undefined}
    context={headerCase ? { source: 'case', facts: {
      ...featureFacts, originationChannel: headerCase.originationChannel,
    } } : { source: 'feature', facts: featureFacts }} activeSection="review"
    updatedAt={query.dataUpdatedAt} refreshing={query.isFetching || (canReadCase && caseQuery.isFetching)} stale={query.isStale || (canReadCase && caseQuery.isStale)}
    contextUnavailable={canReadCase && caseQuery.isError}
    onRetryContext={canReadCase ? () => void caseQuery.refetch() : undefined}
    onRefresh={() => void refreshWorkspace()}
  ><h2 className="text-xl font-semibold">Loan Officer review</h2>{query.isError ? <Alert variant="warning"><RefreshCw /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Previously loaded review details are shown. Refresh successfully before starting review.</AlertDescription></Alert> : null}{assignedToAnotherOfficer && data.assignedLoanOfficer ? <Alert variant="information"><History /><AlertTitle>Review assigned to {data.assignedLoanOfficer.displayName}</AlertTitle><AlertDescription>This review is assigned to {data.assignedLoanOfficer.displayName} ({data.assignedLoanOfficer.email}). You can inspect the case, but only the assigned Loan Officer can continue formal review.</AlertDescription></Alert> : null}<Card><CardHeader><CardTitle>Documents</CardTitle></CardHeader><CardContent className="space-y-4"><dl className="grid gap-4 sm:grid-cols-2"><Fact label="Upload completeness">{data.documentReadiness.uploadComplete ? 'Ready' : 'Not ready'}</Fact><Fact label="Document processing">{data.documentReadiness.processingReady ? 'Ready' : 'Not ready'}</Fact></dl>{state.status === 'authenticated' && hasPermission(state.actor, 'document:review') ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/documents`}>Open document workspace</Link> : null}</CardContent></Card><ProductAssessmentPanel loanApplicationId={loanApplicationId} /><h3 className="text-lg font-semibold">Credit review</h3><Card><CardHeader><CardTitle>Review readiness</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2"><Fact label="Product verification">{humanizeKnownValue(data.productReadiness.productVerificationResult)}</Fact><Fact label="Product ready for review">{data.productReadiness.readyForReview ? 'Ready' : 'Not ready'}</Fact></dl></CardContent></Card><Card><CardHeader><CardTitle>Current review cycle</CardTitle></CardHeader><CardContent>{data.currentReviewCycle ? <dl className="grid gap-4 sm:grid-cols-2"><Fact label="Cycle">{data.currentReviewCycle.cycleNumber}</Fact><Fact label="Assigned Loan Officer">{data.currentReviewCycle.assignedLoanOfficer ? <><span className="block">{data.currentReviewCycle.assignedLoanOfficer.displayName}</span><span className="block text-sm font-normal text-muted-foreground">{data.currentReviewCycle.assignedLoanOfficer.email}</span></> : "Assignment unavailable"}</Fact><Fact label="Status">{knownReviewStatuses.has(data.currentReviewCycle.status) ? humanizeKnownValue(data.currentReviewCycle.status) : 'Review status unavailable'}</Fact><Fact label="Started">{formatTimestamp(data.currentReviewCycle.startedAt)}</Fact><Fact label="Ended">{data.currentReviewCycle.endedAt ? formatTimestamp(data.currentReviewCycle.endedAt) : 'Active'}</Fact></dl> : <p className="text-sm text-muted-foreground">No review cycle has been recorded.</p>}</CardContent></Card><Card><CardHeader><CardTitle>Start Loan Officer review</CardTitle><p className="text-sm text-muted-foreground">Starting review requires all application checks to pass. If the result is uncertain, Meridian checks the latest review and does not submit it again automatically.</p></CardHeader><CardContent className="space-y-4">{startAvailable ? <Button id="review-start-trigger" onClick={() => setConfirming(true)}>Start review</Button> : <p className="text-sm text-muted-foreground">Review cannot start in the application's current state.</p>}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} headingId="review-start-result" headingLabel={`Review start result for ${data.applicationNumber}`} /> : null}{operation.detail ? <p className="text-sm font-medium" aria-live="polite">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent></Card><RecommendationPanel loanApplicationId={loanApplicationId} /><ReviewHistoryPanel loanApplicationId={loanApplicationId} />{confirming ? <ConfirmationDialog onDismiss={() => { setConfirming(false); setTimeout(() => document.getElementById('review-start-trigger')?.focus(), 0) }}><div><ConfirmationDialogTitle className="text-xl font-semibold">Confirm review start</ConfirmationDialogTitle><ConfirmationDialogDescription className="mt-1 text-sm text-muted-foreground">Meridian checks the latest application, document, and product requirements again before starting review.</ConfirmationDialogDescription></div><dl className="grid gap-3 text-sm"><Fact label="Application">{data.applicationNumber}</Fact><Fact label="Product">{productLabel(data.productCode)}</Fact><Fact label="Application status">{humanizeKnownValue(data.applicationStatus)}</Fact><Fact label="Product verification">{humanizeKnownValue(data.productReadiness.productVerificationResult)}</Fact><Fact label="Document processing">{data.documentReadiness.processingReady ? 'Ready' : 'Not ready'}</Fact></dl><ConfirmationDialogFooter><ConfirmationDialogCancel asChild><Button variant="outline">Cancel</Button></ConfirmationDialogCancel><Button autoFocus onClick={() => void runStart()}><CheckCircle2 /> Confirm review start</Button></ConfirmationDialogFooter></ConfirmationDialog> : null}</ApplicationWorkspaceShell>
}
