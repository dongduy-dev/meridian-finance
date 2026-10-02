import { ProductAssessmentPanel } from '@/features/staff-verification/components/ProductAssessmentPanel'
import { staffVerificationKeys } from '@/features/staff-verification/api/queries'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ReviewHistoryPanel } from '@/features/staff-review-history/components/ReviewHistoryPanel'
import { staffReviewHistoryKeys } from '@/features/staff-review-history/api/queries'
import { AlertTriangle, History, ShieldAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
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
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { ApplicationWorkspaceShell } from '@/components/operations/ApplicationWorkspaceShell'
import { RecordedStaffAction } from '@/components/operations/RecordedStaffAction'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import type { CorrectionTaskInput } from '@/features/staff-review/api/contracts'
import { staffReviewKeys } from '@/features/staff-review/api/queries'
import { CorrectionPlanFields, toCorrectionTaskRequests, validateCorrectionTasks } from '@/features/staff-review/components/CorrectionPlanFields'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'
import { decisionActions, type ApprovalDecisionRequest, type DecisionAction, type StaffDecisionCase } from '../api/contracts'
import { staffApprovalKeys, staffDecisionCaseQuery } from '../api/queries'
import { submitApprovalDecision } from '../api/staff-approval-api'

type OperationState = { status: OperationStatus; detail?: string; error?: Error }
type PendingReconciliation = { action: DecisionAction; recommendationId: string; reviewCycleId: string; reviewCycleNumber: number; expectedApplicationStatus: string; commandConfirmed: boolean; commandError?: Error }
type DecisionConfirmation = { action: DecisionAction; recommendationId: string; reviewCycleId: string; reviewCycleNumber: number; applicationNumber: string; recommendation: NonNullable<StaffDecisionCase['recommendation']> }
const actionLabels: Record<DecisionAction, string> = {
  APPROVE: 'Approve',
  REJECT: 'Reject',
  RETURN_TO_LOAN_OFFICER_REVIEW: 'Return to Loan Officer review',
  REQUEST_CUSTOMER_OR_STAFF_CORRECTION: 'Request Customer and Staff correction',
}
const knownRecommendationActions = new Set(['RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION'])
const resultingLoanStatus: Record<DecisionAction, string> = {
  APPROVE: 'CUSTOMER_ACCEPTANCE_PENDING',
  REJECT: 'REJECTED',
  RETURN_TO_LOAN_OFFICER_REVIEW: 'RETURNED_TO_REVIEW',
  REQUEST_CUSTOMER_OR_STAFF_CORRECTION: 'RETURNED_FOR_REVISION',
}

function decisionOutcomeMatches(refreshed: StaffDecisionCase | undefined, submitted: PendingReconciliation) {
  if (refreshed?.latestDecision?.reviewRecommendationId !== submitted.recommendationId
    || refreshed.latestDecision.action !== submitted.action
    || refreshed.recommendation?.recommendationId !== submitted.recommendationId
    || refreshed.recommendation.reviewCycleId !== submitted.reviewCycleId
    || refreshed.applicationStatus !== submitted.expectedApplicationStatus) {
    return false
  }

  const currentCycle = refreshed.evidence.currentReviewCycle
  if (submitted.action === 'RETURN_TO_LOAN_OFFICER_REVIEW') {
    return currentCycle?.status === 'ACTIVE'
      && currentCycle.reviewCycleId !== submitted.reviewCycleId
      && currentCycle.cycleNumber > submitted.reviewCycleNumber
  }
  return currentCycle?.reviewCycleId === submitted.reviewCycleId
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-semibold">{children}</dd></div>
}

export function StaffDecisionWorkspacePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canDecide = state.status === 'authenticated' && hasPermission(state.actor, 'approval:decide')
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const canReview = state.status === 'authenticated' && hasPermission(state.actor, 'loan:review')
  const query = useQuery(staffDecisionCaseQuery(manager, loanApplicationId, canDecide && validId))
  const [action, setAction] = useState<DecisionAction>('APPROVE')
  const [reason, setReason] = useState('')
  const [internalNotes, setInternalNotes] = useState('')
  const [reasonCode, setReasonCode] = useState('DOCUMENT_REPLACEMENT_REQUIRED')
  const [tasks, setTasks] = useState<CorrectionTaskInput[]>([])
  const [validationError, setValidationError] = useState<string>()
  const [confirming, setConfirming] = useState<DecisionConfirmation>()
  const [staleRecommendationId, setStaleRecommendationId] = useState<string>()
  const [pending, setPending] = useState<PendingReconciliation>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const requestResultFocus = useOperationResultFocus(operation.status, 'decision-result')
  useEffect(() => () => setInternalNotes(''), [])

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Decision unavailable</h1><Alert variant="warning"><History /><AlertTitle>Decision unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading independent decision</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading decision evidence…</div></section>
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Independent decision</h1><QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} /></section>
  if (!query.data) return null
  const data = query.data
  const recommendation = data.recommendation
  const recommendationCycle = recommendation && data.evidence.currentReviewCycle?.reviewCycleId === recommendation.reviewCycleId
    ? data.evidence.currentReviewCycle.cycleNumber : undefined
  const knownRecommendation = recommendation && knownRecommendationActions.has(recommendation.action)
  const knownLatestDecision = !data.latestDecision || decisionActions.some((value) => value === data.latestDecision?.action)
  const correction = action === 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
  const available = data.decisionAvailable && data.makerCheckerEligible && data.evidence.readyForDecision
    && recommendation && knownRecommendation && !data.latestDecision && !query.isError && !pending && !staleRecommendationId

  const validate = () => {
    if (action !== 'APPROVE' && action !== 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
      && (reason.trim().length < 1 || reason.trim().length > 2000)) {
      setValidationError('A decision reason between 1 and 2,000 characters is required.')
      return false
    }
    if (internalNotes.trim().length > 2000) {
      setValidationError('Internal notes cannot exceed 2,000 characters.')
      return false
    }
    if (correction) {
      if (!data.correctionReasonCodes.includes(reasonCode)) {
        setValidationError('Select an available correction reason.')
        return false
      }
      const taskError = validateCorrectionTasks(tasks, data.correctionOptions, 'MIXED')
      if (taskError) {
        setValidationError(taskError)
        return false
      }
    }
    setValidationError(undefined)
    return true
  }

  const buildRequest = (confirmation: DecisionConfirmation): ApprovalDecisionRequest => ({
    action: confirmation.action,
    reason: confirmation.action === 'REJECT' || confirmation.action === 'RETURN_TO_LOAN_OFFICER_REVIEW'
      ? reason.trim() : null,
    internalNotes: internalNotes.trim() || null,
    expectedReviewRecommendationId: confirmation.recommendationId,
    expectedReviewCycleId: confirmation.reviewCycleId,
    reasonCode: confirmation.action === 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION' ? reasonCode : null,
    correctionPlan: confirmation.action === 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
      ? { tasks: toCorrectionTaskRequests(tasks, data.correctionOptions) } : null,
  })

  const reconcile = async (next: PendingReconciliation) => {
    setPending(next)
    setOperation({ status: 'RECONCILING' })
    try {
      const refreshed = (await query.refetch({ throwOnError: true })).data
      setPending(undefined)
      const exact = decisionOutcomeMatches(refreshed, next)
      const stale = next.commandError instanceof ApiError
        && (next.commandError.errorCode === 'STALE_REVIEW_CYCLE'
          || next.commandError.errorCode === 'STALE_REVIEW_RECOMMENDATION')
      if (stale) setStaleRecommendationId(next.recommendationId)
      if (exact) {
        await queryClient.invalidateQueries({ queryKey: staffReviewHistoryKeys.case(loanApplicationId) }).catch(() => undefined)
        await queryClient.invalidateQueries({ queryKey: staffApprovalKeys.all }).catch(() => undefined)
        if (canReview) await queryClient.invalidateQueries({ queryKey: staffReviewKeys.all }).catch(() => undefined)
        if (canReadCase) await queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all }).catch(() => undefined)
        setReason('')
        setInternalNotes('')
        setTasks([])
      }
      setOperation({
        status: exact ? 'RESOLVED' : 'BLOCKED',
        error: exact ? undefined : next.commandError,
        detail: exact
          ? `Decision recorded. Application status: ${refreshed ? humanizeKnownValue(refreshed.applicationStatus) : 'Status unavailable'}.`
          : stale
            ? 'The recommendation or review-cycle evidence changed. Your unsent entries are retained on this page. Review the updated evidence before confirming again.'
            : 'The decision was not confirmed. Review the refreshed evidence before submitting again. Meridian did not submit the action again automatically.',
      })
    } catch (refreshError) {
      const error = next.commandError ?? (refreshError instanceof Error ? refreshError : new NetworkError())
      setOperation({
        status: next.commandConfirmed || next.commandError instanceof ApiError ? 'BLOCKED' : 'RESULT_UNKNOWN',
        error,
        detail: next.commandConfirmed
          ? 'Decision recorded; updated details could not be loaded. Refresh successfully before taking another action.'
          : next.commandError instanceof ApiError
            ? 'The action was rejected, but the latest decision information is unavailable. Controls remain locked until Refresh succeeds.'
            : 'The decision result is not confirmed. Controls remain locked until Refresh succeeds; Meridian will not submit it again automatically.',
      })
    }
  }

  const submit = async () => {
    const confirmation = confirming
    if (!recommendation || !confirmation || !validate()) return
    requestResultFocus()
    setConfirming(undefined)
    setOperation({ status: 'IN_FLIGHT' })
    let commandConfirmed = false
    let commandError: Error | undefined
    try {
      await submitApprovalDecision(manager, loanApplicationId, buildRequest(confirmation))
      commandConfirmed = true
    } catch (error) {
      commandError = error instanceof Error ? error : new NetworkError()
      if (!(commandError instanceof ApiError)) setOperation({ status: 'RESULT_UNKNOWN', error: commandError })
    }
    await reconcile({
      action: confirmation.action,
      recommendationId: confirmation.recommendationId,
      reviewCycleId: confirmation.reviewCycleId,
      reviewCycleNumber: confirmation.reviewCycleNumber,
      expectedApplicationStatus: resultingLoanStatus[confirmation.action],
      commandConfirmed,
      commandError,
    })
  }

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: staffVerificationKeys.case(loanApplicationId) }).catch(() => undefined)
    if (pending) await reconcile(pending)
    else await query.refetch()
    await queryClient.invalidateQueries({ queryKey: staffReviewHistoryKeys.case(loanApplicationId) }).catch(() => undefined)
  }

  return <ApplicationWorkspaceShell
    actor={state.status === 'authenticated' ? state.actor : undefined}
    context={{ source: 'feature', facts: {
      loanApplicationId: data.loanApplicationId, applicationNumber: data.applicationNumber,
      applicationStatus: data.applicationStatus, productCode: data.productCode,
      requestedAmount: data.requestedAmount, requestedTermMonths: data.requestedTermMonths,
      submittedAt: data.submittedAt,
    } }}
    updatedAt={query.dataUpdatedAt} refreshing={query.isFetching} stale={query.isStale}
    onRefresh={() => void refresh()}
  ><div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/approvals">← Approval queue</Link>{canReview ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/review`}>Review workspace</Link> : null}</div><h2 className="text-xl font-semibold">Independent decision</h2>{query.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Previously loaded details are shown. Refresh successfully before recording a decision.</AlertDescription></Alert> : null}<Card><CardHeader><CardTitle>Customer</CardTitle></CardHeader><CardContent><p className="break-words font-semibold">{data.customer.fullName}</p><p className="mt-1 break-words text-sm text-muted-foreground">{data.customer.customerNumber}</p></CardContent></Card><ProductAssessmentPanel loanApplicationId={loanApplicationId} /><div className="grid gap-5 lg:grid-cols-2"><Card><CardHeader><CardTitle>Loan Officer recommendation</CardTitle></CardHeader><CardContent>{recommendation ? <RecordedStaffAction outcome={knownRecommendation ? humanizeKnownValue(recommendation.action) : 'Recommendation action unavailable'} recordedAt={recommendation.submittedAt} recordedBy={recommendation.recordedBy} known={Boolean(knownRecommendation)}>
        <dl className="grid gap-4 sm:grid-cols-2"><Fact label="Review cycle">{recommendationCycle ? `Cycle ${recommendationCycle}` : 'Cycle number unavailable'}</Fact><Fact label="Reason">{recommendation.reason ?? (recommendation.reasonCode ? humanizeKnownValue(recommendation.reasonCode) : 'None')}</Fact></dl>
      </RecordedStaffAction> : <p className="text-sm text-muted-foreground">No recommendation has been recorded.</p>}</CardContent></Card><Card><CardHeader><CardTitle>Decision readiness</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2"><Fact label="Product verification">{humanizeKnownValue(data.evidence.productVerificationResult)}</Fact><Fact label="Document processing">{data.evidence.processingReady ? 'Ready' : 'Not ready'}</Fact><Fact label="Review cycle">{data.evidence.currentReviewCycle ? `Cycle ${data.evidence.currentReviewCycle.cycleNumber} · ${humanizeKnownValue(data.evidence.currentReviewCycle.status)}` : 'Unavailable'}</Fact><Fact label="Maker-checker">{data.makerCheckerEligible ? 'Eligible' : 'Blocked'}</Fact></dl></CardContent></Card></div>{!data.makerCheckerEligible && recommendation ? <Alert variant="destructive"><ShieldAlert /><AlertTitle>Maker-checker requirement</AlertTitle><AlertDescription>You recorded the applicable recommendation. A different authorized Approver must make the decision.</AlertDescription></Alert> : null}{data.latestDecision ? <Card><CardHeader><CardTitle>Decision outcome</CardTitle></CardHeader><CardContent><RecordedStaffAction outcome={knownLatestDecision ? humanizeKnownValue(data.latestDecision.action) : 'Decision action unavailable'} recordedAt={data.latestDecision.decidedAt} recordedBy={data.latestDecision.recordedBy} known={knownLatestDecision}>
        <p>Application status: {humanizeKnownValue(data.applicationStatus)}.</p>
        {data.latestDecision.reason ? <p>Reason: {data.latestDecision.reason}</p> : null}
        {data.latestDecision.reasonCode ? <p>Controlled reason: {humanizeKnownValue(data.latestDecision.reasonCode)}</p> : null}
      </RecordedStaffAction></CardContent></Card> : null}<ReviewHistoryPanel loanApplicationId={loanApplicationId} /><Card><CardHeader><CardTitle>Record independent decision</CardTitle><p className="text-sm text-muted-foreground">A different Approver must decide the Loan Officer recommendation. If the result cannot be confirmed, refresh before trying again; Meridian will not submit the decision again automatically.</p></CardHeader><CardContent className="space-y-5">{available ? <><fieldset className="space-y-2"><legend className="font-semibold">Decision action</legend>{decisionActions.map((value) => <label key={value} className="flex min-h-11 items-center gap-3 rounded-md border px-4"><input type="radio" name="decision-action" checked={action === value} onChange={() => { setAction(value); setTasks([]); setValidationError(undefined) }} />{actionLabels[value]}</label>)}</fieldset>{action === 'REJECT' || action === 'RETURN_TO_LOAN_OFFICER_REVIEW' ? <label className="grid gap-2 text-sm font-semibold">Decision reason<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={reason} maxLength={2000} onChange={(event) => setReason(event.target.value)} /></label> : null}{correction ? <><label className="grid gap-2 text-sm font-semibold">Controlled reason<select className="h-11 rounded-md border bg-card px-3 font-normal" value={reasonCode} onChange={(event) => setReasonCode(event.target.value)}>{data.correctionReasonCodes.map((code) => <option key={code} value={code}>{humanizeKnownValue(code)}</option>)}</select></label><CorrectionPlanFields options={data.correctionOptions} mode="MIXED" tasks={tasks} onChange={setTasks} /></> : null}<label className="grid gap-2 text-sm font-semibold">Internal credit note<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={internalNotes} maxLength={2000} onChange={(event) => setInternalNotes(event.target.value)} /></label><p className="text-sm text-muted-foreground">Submitted notes are saved as restricted credit evidence for entitled lending Staff. They are excluded from the application overview.</p>{validationError ? <p role="alert" className="font-semibold text-danger">{validationError}</p> : null}<Button id="decision-trigger" variant={action === 'REJECT' ? 'destructive' : 'default'} onClick={() => validate() && recommendation && data.evidence.currentReviewCycle && setConfirming({ action, recommendationId: recommendation.recommendationId, reviewCycleId: data.evidence.currentReviewCycle.reviewCycleId, reviewCycleNumber: data.evidence.currentReviewCycle.cycleNumber, applicationNumber: data.applicationNumber, recommendation })}>Review decision</Button></> : !data.latestDecision ? <p className="text-sm text-muted-foreground">No decision is available for the application's current state.</p> : null}{staleRecommendationId ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Decision evidence changed</AlertTitle><AlertDescription><p>The recommendation or review cycle used in the prior confirmation has changed. Re-review the refreshed evidence before continuing.</p><Button className="mt-3" variant="outline" onClick={() => setStaleRecommendationId(undefined)}>I reviewed the updated evidence</Button></AlertDescription></Alert> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} headingId="decision-result" headingLabel={`Decision result for ${data.applicationNumber}`} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent></Card>{confirming ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="decision-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><h2 id="decision-confirm-title" className="text-xl font-semibold">Confirm {actionLabels[confirming.action]}</h2><dl className="grid gap-4 text-sm sm:grid-cols-2">
          <Fact label="Application">{confirming.applicationNumber}</Fact>
          <Fact label="Decision">{actionLabels[confirming.action]}</Fact>
          <Fact label="Loan Officer recommendation">{humanizeKnownValue(confirming.recommendation.action)}</Fact>
          <Fact label="Recommended by">{confirming.recommendation.recordedBy ? <><span className="block">{confirming.recommendation.recordedBy.displayName}</span><span className="block text-sm font-normal text-muted-foreground">{confirming.recommendation.recordedBy.email}</span></> : 'Staff member unavailable'}</Fact>
          <Fact label="Review cycle">Cycle {confirming.reviewCycleNumber}</Fact>
          <Fact label="Submitted">{formatTimestamp(confirming.recommendation.submittedAt)}</Fact>
        </dl><div className="flex justify-end gap-2"><Button variant="outline" onClick={() => { setConfirming(undefined); setTimeout(() => document.getElementById('decision-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus variant={confirming.action === 'REJECT' ? 'destructive' : 'default'} onClick={() => void submit()}>Confirm</Button></div></div></div> : null}</ApplicationWorkspaceShell>
}
