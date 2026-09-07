import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'
import type { CorrectionTaskInput, RecommendationAction, RecommendationRequest } from '../api/contracts'
import { staffRecommendationCaseQuery, staffReviewKeys } from '../api/queries'
import { submitRecommendation } from '../api/staff-review-api'
import { CorrectionPlanFields, toCorrectionTaskRequests, validateCorrectionTasks } from './CorrectionPlanFields'

type OperationState = { status: OperationStatus; detail?: string; error?: Error }
type PendingReconciliation = { action: RecommendationAction; expectedReviewCycleId: string; commandConfirmed: boolean; commandError?: Error }
type RecommendationConfirmation = { action: RecommendationAction; reviewCycleId: string; cycleNumber: number }

const actions: { value: RecommendationAction; label: string }[] = [
  { value: 'RECOMMEND_APPROVAL', label: 'Recommend approval' },
  { value: 'RECOMMEND_REJECTION', label: 'Recommend rejection' },
  { value: 'RETURN_TO_CUSTOMER_REVISION', label: 'Return to Customer revision' },
  { value: 'REQUEST_STAFF_CORRECTION', label: 'Request Staff correction' },
]

export function RecommendationPanel({ loanApplicationId }: { loanApplicationId: string }) {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const canRecommend = state.status === 'authenticated' && hasPermission(state.actor, 'approval:recommend')
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const query = useQuery(staffRecommendationCaseQuery(manager, loanApplicationId, canRecommend))
  const [action, setAction] = useState<RecommendationAction>('RECOMMEND_APPROVAL')
  const [reason, setReason] = useState('')
  const [internalNotes, setInternalNotes] = useState('')
  const [reasonCode, setReasonCode] = useState('DOCUMENT_REPLACEMENT_REQUIRED')
  const [tasks, setTasks] = useState<CorrectionTaskInput[]>([])
  const [validationError, setValidationError] = useState<string>()
  const [confirming, setConfirming] = useState<RecommendationConfirmation>()
  const [staleExpectedCycleId, setStaleExpectedCycleId] = useState<string>()
  const [pending, setPending] = useState<PendingReconciliation>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  useEffect(() => () => setInternalNotes(''), [])

  if (!canRecommend) return null
  if (query.isPending) return <Card><CardHeader><CardTitle>Loan Officer recommendation</CardTitle></CardHeader><CardContent><p role="status" className="text-sm text-muted-foreground">Loading authoritative recommendation evidence…</p></CardContent></Card>
  if (query.isError && !query.data) return <Card><CardHeader><CardTitle>Loan Officer recommendation</CardTitle></CardHeader><CardContent><Alert variant="warning"><RefreshCw /><AlertTitle>Recommendation evidence unavailable</AlertTitle><AlertDescription><Button className="mt-3" variant="outline" onClick={() => void query.refetch()}>Refresh</Button></AlertDescription></Alert></CardContent></Card>
  if (!query.data) return null
  const data = query.data
  const cycle = data.evidence.currentReviewCycle
  const revision = action === 'RETURN_TO_CUSTOMER_REVISION' || action === 'REQUEST_STAFF_CORRECTION'
  const mode = action === 'REQUEST_STAFF_CORRECTION' ? 'STAFF' as const : 'CUSTOMER' as const
  const knownAction = actions.some((item) => item.value === data.recommendation?.action)
  const available = data.recommendationAvailable && !data.recommendation && data.evidence.readyForDecision
    && cycle?.status === 'ACTIVE' && !query.isError && !pending && !staleExpectedCycleId

  const validate = () => {
    if (action === 'RECOMMEND_REJECTION' && (reason.trim().length < 1 || reason.trim().length > 2000)) {
      setValidationError('A rejection reason between 1 and 2,000 characters is required.')
      return false
    }
    if (internalNotes.trim().length > 2000) {
      setValidationError('Internal notes cannot exceed 2,000 characters.')
      return false
    }
    if (revision) {
      if (!data.correctionReasonCodes.includes(reasonCode)) {
        setValidationError('Select a backend-authorized correction reason.')
        return false
      }
      const taskError = validateCorrectionTasks(tasks, data.correctionOptions, mode)
      if (taskError) {
        setValidationError(taskError)
        return false
      }
    }
    setValidationError(undefined)
    return true
  }

  const buildRequest = (confirmation: RecommendationConfirmation): RecommendationRequest => ({
    action: confirmation.action,
    reason: confirmation.action === 'RECOMMEND_REJECTION' ? reason.trim() : null,
    internalNotes: internalNotes.trim() || null,
    expectedReviewCycleId: confirmation.reviewCycleId,
    reasonCode: confirmation.action === 'RETURN_TO_CUSTOMER_REVISION'
      || confirmation.action === 'REQUEST_STAFF_CORRECTION' ? reasonCode : null,
    correctionPlan: confirmation.action === 'RETURN_TO_CUSTOMER_REVISION'
      || confirmation.action === 'REQUEST_STAFF_CORRECTION'
      ? { tasks: toCorrectionTaskRequests(tasks, data.correctionOptions) } : null,
  })

  const reconcile = async (next: PendingReconciliation) => {
    setPending(next)
    setOperation({ status: 'RECONCILING' })
    try {
      const refreshed = (await query.refetch({ throwOnError: true })).data
      setPending(undefined)
      const exact = refreshed?.recommendation?.reviewCycleId === next.expectedReviewCycleId
        && refreshed.recommendation.action === next.action
      const stale = next.commandError instanceof ApiError && next.commandError.errorCode === 'STALE_REVIEW_CYCLE'
      if (stale) setStaleExpectedCycleId(next.expectedReviewCycleId)
      if (exact) {
        if (canReadCase) await queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all }).catch(() => undefined)
        await queryClient.invalidateQueries({ queryKey: staffReviewKeys.case(loanApplicationId) }).catch(() => undefined)
        setReason('')
        setInternalNotes('')
        setTasks([])
      }
      setOperation({
        status: exact ? 'RESOLVED' : 'BLOCKED',
        error: exact ? undefined : next.commandError,
        detail: exact
          ? 'The authoritative read confirms the exact recommendation for the displayed review cycle.'
          : stale
            ? 'The review cycle changed. Your unsent form remains in memory; review the new evidence before confirming again.'
            : 'The refreshed evidence does not prove this recommendation. No POST was retried.',
      })
    } catch (refreshError) {
      const error = next.commandError ?? (refreshError instanceof Error ? refreshError : new NetworkError())
      setOperation({
        status: next.commandConfirmed || next.commandError instanceof ApiError ? 'BLOCKED' : 'RESULT_UNKNOWN',
        error,
        detail: next.commandConfirmed
          ? 'Command confirmed; refreshed state unavailable. Recommendation controls remain locked until Refresh succeeds.'
          : next.commandError instanceof ApiError
            ? 'The command was rejected, but authoritative state is unavailable. Controls remain locked until Refresh succeeds.'
            : 'The recommendation result is unknown. Controls remain locked until Refresh succeeds; no POST will be retried.',
      })
    }
  }

  const submit = async () => {
    const confirmation = confirming
    if (!confirmation || !validate()) return
    setConfirming(undefined)
    setOperation({ status: 'IN_FLIGHT' })
    let commandConfirmed = false
    let commandError: Error | undefined
    try {
      await submitRecommendation(manager, loanApplicationId, buildRequest(confirmation))
      commandConfirmed = true
    } catch (error) {
      commandError = error instanceof Error ? error : new NetworkError()
      if (!(commandError instanceof ApiError)) setOperation({ status: 'RESULT_UNKNOWN', error: commandError })
    }
    await reconcile({
      action: confirmation.action,
      expectedReviewCycleId: confirmation.reviewCycleId,
      commandConfirmed,
      commandError,
    })
  }

  const refresh = async () => pending ? reconcile(pending) : void await query.refetch()

  return <Card><CardHeader><div className="flex flex-wrap items-start justify-between gap-3"><div><CardTitle>Loan Officer recommendation</CardTitle><p className="mt-2 text-sm text-muted-foreground">Recommendation evidence is Approval-owned. The command is never automatically retried.</p></div><Button variant="outline" onClick={() => void refresh()} disabled={query.isFetching}><RefreshCw className={query.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div></CardHeader><CardContent className="space-y-5">{query.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Cached evidence cannot authorize another recommendation.</AlertDescription></Alert> : null}{data.recommendation ? <Alert variant={knownAction ? 'success' : 'warning'}><CheckCircle2 /><AlertTitle>Durable recommendation recorded</AlertTitle><AlertDescription>{knownAction ? humanizeKnownValue(data.recommendation.action) : 'Recommendation action unavailable'} · {formatTimestamp(data.recommendation.submittedAt)}{data.recommendation.reason ? <span className="mt-2 block">Reason: {data.recommendation.reason}</span> : null}{data.recommendation.reasonCode ? <span className="mt-2 block">Controlled reason: {humanizeKnownValue(data.recommendation.reasonCode)}</span> : null}</AlertDescription></Alert> : null}{available ? <div className="space-y-4"><fieldset className="space-y-2"><legend className="font-semibold">Recommendation action</legend>{actions.map((item) => <label key={item.value} className="flex min-h-11 items-center gap-3 rounded-md border px-4"><input type="radio" name="recommendation-action" checked={action === item.value} onChange={() => { setAction(item.value); setTasks([]); setValidationError(undefined) }} />{item.label}</label>)}</fieldset>{action === 'RECOMMEND_REJECTION' ? <label className="grid gap-2 text-sm font-semibold">Recommendation reason<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={reason} maxLength={2000} onChange={(event) => setReason(event.target.value)} /></label> : null}{revision ? <><label className="grid gap-2 text-sm font-semibold">Controlled reason<select className="h-11 rounded-md border bg-card px-3 font-normal" value={reasonCode} onChange={(event) => setReasonCode(event.target.value)}>{data.correctionReasonCodes.map((code) => <option key={code} value={code}>{humanizeKnownValue(code)}</option>)}</select></label><CorrectionPlanFields options={data.correctionOptions} mode={mode} tasks={tasks} onChange={setTasks} /></> : null}<label className="grid gap-2 text-sm font-semibold">Restricted internal notes<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={internalNotes} maxLength={2000} onChange={(event) => setInternalNotes(event.target.value)} /></label><p className="text-sm text-muted-foreground">Internal notes remain memory-only and are never copied into Customer instructions.</p>{validationError ? <p role="alert" className="font-semibold text-danger">{validationError}</p> : null}<Button id="recommendation-trigger" onClick={() => validate() && cycle && setConfirming({ action, reviewCycleId: cycle.reviewCycleId, cycleNumber: cycle.cycleNumber })}>Review recommendation</Button></div> : !data.recommendation ? <p className="text-sm text-muted-foreground">Recommendation is unavailable for the authoritative current state.</p> : null}{staleExpectedCycleId ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Review evidence changed</AlertTitle><AlertDescription><p>The previous confirmation targeted cycle <span className="break-all font-semibold">{staleExpectedCycleId}</span>. Re-review the current evidence before continuing.</p><Button className="mt-3" variant="outline" onClick={() => setStaleExpectedCycleId(undefined)}>I reviewed the updated cycle</Button></AlertDescription></Alert> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent>{confirming ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="recommendation-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><h2 id="recommendation-confirm-title" className="text-xl font-semibold">Confirm {humanizeKnownValue(confirming.action)}</h2><p className="text-sm text-muted-foreground">Cycle {confirming.cycleNumber}. This command has no business UUID and will not be retried automatically.</p><div className="flex justify-end gap-2"><Button variant="outline" onClick={() => { setConfirming(undefined); setTimeout(() => document.getElementById('recommendation-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus variant={confirming.action === 'RECOMMEND_REJECTION' ? 'destructive' : 'default'} onClick={() => void submit()}>Confirm</Button></div></div></div> : null}</Card>
}
