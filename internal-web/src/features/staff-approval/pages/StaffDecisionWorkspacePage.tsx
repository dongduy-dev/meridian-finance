import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, History, RefreshCw, ShieldAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
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
import { humanizeKnownValue, productLabel } from '@/features/staff-applications/model/presentation'
import type { CorrectionTaskInput } from '@/features/staff-review/api/contracts'
import { staffReviewKeys } from '@/features/staff-review/api/queries'
import { CorrectionPlanFields, toCorrectionTaskRequests, validateCorrectionTasks } from '@/features/staff-review/components/CorrectionPlanFields'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { decisionActions, type ApprovalDecisionRequest, type DecisionAction } from '../api/contracts'
import { staffApprovalKeys, staffDecisionCaseQuery } from '../api/queries'
import { submitApprovalDecision } from '../api/staff-approval-api'

type OperationState = { status: OperationStatus; detail?: string; error?: Error }
type PendingReconciliation = { action: DecisionAction; recommendationId: string; commandConfirmed: boolean; commandError?: Error }
const actionLabels: Record<DecisionAction, string> = {
  APPROVE: 'Approve',
  REJECT: 'Reject',
  RETURN_TO_LOAN_OFFICER_REVIEW: 'Return to Loan Officer review',
  REQUEST_CUSTOMER_OR_STAFF_CORRECTION: 'Request Customer and Staff correction',
}
const knownRecommendationActions = new Set(['RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION'])

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
  const [confirming, setConfirming] = useState(false)
  const [staleRecommendationId, setStaleRecommendationId] = useState<string>()
  const [pending, setPending] = useState<PendingReconciliation>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  useEffect(() => () => setInternalNotes(''), [])

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Decision unavailable</h1><Alert variant="warning"><History /><AlertTitle>Decision unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading independent decision</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative decision evidence…</div></section>
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Independent decision</h1><QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} /></section>
  if (!query.data) return null
  const data = query.data
  const recommendation = data.recommendation
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
        setValidationError('Select a backend-authorized correction reason.')
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

  const buildRequest = (): ApprovalDecisionRequest => ({
    action,
    reason: action === 'REJECT' || action === 'RETURN_TO_LOAN_OFFICER_REVIEW' ? reason.trim() : null,
    internalNotes: internalNotes.trim() || null,
    expectedReviewCycleId: correction ? data.evidence.currentReviewCycle!.reviewCycleId : null,
    reasonCode: correction ? reasonCode : null,
    correctionPlan: correction ? { tasks: toCorrectionTaskRequests(tasks, data.correctionOptions) } : null,
  })

  const reconcile = async (next: PendingReconciliation) => {
    setPending(next)
    setOperation({ status: 'RECONCILING' })
    try {
      const refreshed = (await query.refetch({ throwOnError: true })).data
      setPending(undefined)
      const exact = refreshed?.latestDecision?.reviewRecommendationId === next.recommendationId
        && refreshed.latestDecision.action === next.action
      const stale = next.commandError instanceof ApiError && next.commandError.errorCode === 'STALE_REVIEW_CYCLE'
      if (stale) setStaleRecommendationId(next.recommendationId)
      if (exact) {
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
          ? `The durable decision and resulting Loan state are confirmed: ${refreshed?.applicationStatus}.`
          : stale
            ? 'The review-cycle evidence changed. Your unsent form remains in memory; review the refreshed recommendation before confirming again.'
            : 'The refreshed evidence does not prove this exact decision. No POST was retried.',
      })
    } catch (refreshError) {
      const error = next.commandError ?? (refreshError instanceof Error ? refreshError : new NetworkError())
      setOperation({
        status: next.commandConfirmed || next.commandError instanceof ApiError ? 'BLOCKED' : 'RESULT_UNKNOWN',
        error,
        detail: next.commandConfirmed
          ? 'Command confirmed; refreshed state unavailable. Decision controls remain locked until Refresh succeeds.'
          : next.commandError instanceof ApiError
            ? 'The command was rejected, but authoritative state is unavailable. Controls remain locked until Refresh succeeds.'
            : 'The decision result is unknown. Controls remain locked until Refresh succeeds; no POST will be retried.',
      })
    }
  }

  const submit = async () => {
    if (!recommendation || !validate()) return
    setConfirming(false)
    setOperation({ status: 'IN_FLIGHT' })
    let commandConfirmed = false
    let commandError: Error | undefined
    try {
      await submitApprovalDecision(manager, loanApplicationId, buildRequest())
      commandConfirmed = true
    } catch (error) {
      commandError = error instanceof Error ? error : new NetworkError()
      if (!(commandError instanceof ApiError)) setOperation({ status: 'RESULT_UNKNOWN', error: commandError })
    }
    await reconcile({ action, recommendationId: recommendation.recommendationId, commandConfirmed, commandError })
  }

  const refresh = async () => pending ? reconcile(pending) : void await query.refetch()

  return <section className="mx-auto max-w-6xl space-y-6"><div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/approvals">← Approval queue</Link>{canReview ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/review`}>Review workspace</Link> : null}</div><header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">INDEPENDENT DECISION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">{data.applicationNumber}</h1><div className="mt-3 flex flex-wrap items-center gap-3"><StatusBadge status={data.applicationStatus} /><span className="text-sm text-muted-foreground">{productLabel(data.productCode)}</span></div></div><Button variant="outline" onClick={() => void refresh()} disabled={query.isFetching}><RefreshCw className={query.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div><dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Application ID"><span className="break-all">{data.loanApplicationId}</span></Fact><Fact label="Requested amount">{formatVnd(data.requestedAmount)}</Fact><Fact label="Requested term">{data.requestedTermMonths} months</Fact><Fact label="Submitted">{formatTimestamp(data.submittedAt)}</Fact></dl></header>{query.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Cached evidence remains visible but cannot authorize a decision.</AlertDescription></Alert> : null}<div className="grid gap-5 lg:grid-cols-2"><Card><CardHeader><CardTitle>Recommendation being decided</CardTitle></CardHeader><CardContent>{recommendation ? <dl className="grid gap-4 sm:grid-cols-2"><Fact label="Action">{knownRecommendation ? humanizeKnownValue(recommendation.action) : 'Recommendation action unavailable'}</Fact><Fact label="Submitted">{formatTimestamp(recommendation.submittedAt)}</Fact><Fact label="Review cycle"><span className="break-all">{recommendation.reviewCycleId}</span></Fact><Fact label="Reason">{recommendation.reason ?? (recommendation.reasonCode ? humanizeKnownValue(recommendation.reasonCode) : 'None')}</Fact></dl> : <p className="text-sm text-muted-foreground">No durable recommendation is available.</p>}</CardContent></Card><Card><CardHeader><CardTitle>Authoritative readiness</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2"><Fact label="Product verification">{humanizeKnownValue(data.evidence.productVerificationResult)}</Fact><Fact label="Document processing">{data.evidence.processingReady ? 'Ready' : 'Not ready'}</Fact><Fact label="Review cycle">{data.evidence.currentReviewCycle ? `${data.evidence.currentReviewCycle.cycleNumber} · ${humanizeKnownValue(data.evidence.currentReviewCycle.status)}` : 'Unavailable'}</Fact><Fact label="Maker-checker">{data.makerCheckerEligible ? 'Eligible' : 'Blocked'}</Fact></dl></CardContent></Card></div>{!data.makerCheckerEligible && recommendation ? <Alert variant="destructive"><ShieldAlert /><AlertTitle>Maker-checker block</AlertTitle><AlertDescription>You recorded the applicable recommendation and cannot make its Approver decision.</AlertDescription></Alert> : null}{data.latestDecision ? <Card><CardHeader><CardTitle>Durable decision outcome</CardTitle></CardHeader><CardContent><Alert variant={knownLatestDecision ? 'success' : 'warning'}><CheckCircle2 /><AlertTitle>{knownLatestDecision ? humanizeKnownValue(data.latestDecision.action) : 'Decision action unavailable'}</AlertTitle><AlertDescription>Recorded {formatTimestamp(data.latestDecision.decidedAt)}. Resulting Loan state: {humanizeKnownValue(data.applicationStatus)}.</AlertDescription></Alert><div className="mt-5 space-y-3"><h3 className="font-semibold">Decision history</h3>{data.decisionHistory.map((item) => <p key={item.decisionId} className="text-sm">{decisionActions.some((value) => value === item.action) ? humanizeKnownValue(item.action) : 'Decision action unavailable'} · {formatTimestamp(item.decidedAt)}</p>)}</div></CardContent></Card> : null}<Card><CardHeader><CardTitle>Record independent decision</CardTitle><p className="text-sm text-muted-foreground">The backend owns maker-checker and Loan lifecycle effects. This no-UUID command is never automatically retried.</p></CardHeader><CardContent className="space-y-5">{available ? <><fieldset className="space-y-2"><legend className="font-semibold">Decision action</legend>{decisionActions.map((value) => <label key={value} className="flex min-h-11 items-center gap-3 rounded-md border px-4"><input type="radio" name="decision-action" checked={action === value} onChange={() => { setAction(value); setTasks([]); setValidationError(undefined) }} />{actionLabels[value]}</label>)}</fieldset>{action === 'REJECT' || action === 'RETURN_TO_LOAN_OFFICER_REVIEW' ? <label className="grid gap-2 text-sm font-semibold">Decision reason<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={reason} maxLength={2000} onChange={(event) => setReason(event.target.value)} /></label> : null}{correction ? <><label className="grid gap-2 text-sm font-semibold">Controlled reason<select className="h-11 rounded-md border bg-card px-3 font-normal" value={reasonCode} onChange={(event) => setReasonCode(event.target.value)}>{data.correctionReasonCodes.map((code) => <option key={code} value={code}>{humanizeKnownValue(code)}</option>)}</select></label><CorrectionPlanFields options={data.correctionOptions} mode="MIXED" tasks={tasks} onChange={setTasks} /></> : null}<label className="grid gap-2 text-sm font-semibold">Restricted internal notes<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={internalNotes} maxLength={2000} onChange={(event) => setInternalNotes(event.target.value)} /></label><p className="text-sm text-muted-foreground">Internal notes remain memory-only and are excluded from the Staff read projection.</p>{validationError ? <p role="alert" className="font-semibold text-danger">{validationError}</p> : null}<Button id="decision-trigger" variant={action === 'REJECT' ? 'destructive' : 'default'} onClick={() => validate() && setConfirming(true)}>Review decision</Button></> : !data.latestDecision ? <p className="text-sm text-muted-foreground">No decision command is available for the authoritative current state.</p> : null}{staleRecommendationId ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Decision evidence changed</AlertTitle><AlertDescription><p>The prior confirmation targeted recommendation <span className="break-all font-semibold">{staleRecommendationId}</span>. Re-review the refreshed evidence before continuing.</p><Button className="mt-3" variant="outline" onClick={() => setStaleRecommendationId(undefined)}>I reviewed the updated evidence</Button></AlertDescription></Alert> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent></Card>{confirming ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="decision-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><h2 id="decision-confirm-title" className="text-xl font-semibold">Confirm {actionLabels[action]}</h2><p className="text-sm text-muted-foreground">Recommendation {recommendation?.recommendationId}. The decision and Loan outcome are one atomic server operation.</p><div className="flex justify-end gap-2"><Button variant="outline" onClick={() => { setConfirming(false); setTimeout(() => document.getElementById('decision-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus variant={action === 'REJECT' ? 'destructive' : 'default'} onClick={() => void submit()}>Confirm decision</Button></div></div></div> : null}</section>
}
