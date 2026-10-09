import { ConfirmationDialog, ConfirmationDialogCancel, ConfirmationDialogTitle, ConfirmationDialogDescription, ConfirmationDialogFooter } from '@/components/ui/confirmation-dialog'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, History } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { staffApplicationCaseQuery, staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { ApplicationWorkspaceShell } from '@/components/operations/ApplicationWorkspaceShell'
import { applicationStatusLabel, humanizeKnownValue, productLabel } from '@/features/staff-applications/model/presentation'
import { ApiError, NetworkError } from '@/lib/api'
import {
  type CompleteVerificationInput,
  type CorrectionTaskInput,
  type ManualVerificationCase,
  type VerificationOutcome,
} from '../api/contracts'
import { ProductAssessmentEvidence, verificationResultLabel as resultLabel } from '../components/ProductAssessmentPanel'
import { staffReviewKeys } from '@/features/staff-review/api/queries'
import { staffVerificationCaseQuery } from '../api/queries'
import { completeVerification, startVerification } from '../api/staff-verification-api'

type OperationState = { status: OperationStatus; error?: Error; detail?: string }
type Confirmation = { kind: 'start' } | { kind: 'complete'; expectedVerificationId?: string }
type PendingReconciliation =
  | { kind: 'start'; commandError?: Error }
  | { kind: 'complete'; outcome: VerificationOutcome; commandError?: Error }

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-semibold">{children}</dd></div>
}

function Readiness({ label, value }: { label: string; value: boolean }) {
  return <div className="rounded-md border bg-background p-4"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="mt-1 font-semibold">{value ? 'Ready' : 'Not ready'}</dd></div>
}

export function StaffVerificationWorkspacePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canReview = state.status === 'authenticated' && hasPermission(state.actor, 'loan:review')
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const query = useQuery(staffVerificationCaseQuery(manager, loanApplicationId, canReview && validId))
  const caseQuery = useQuery(staffApplicationCaseQuery(manager, loanApplicationId, validId && canReview && canReadCase))
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [outcome, setOutcome] = useState<VerificationOutcome>('VERIFIED')
  const [assessmentNote, setAssessmentNote] = useState('')
  const [reasonCode, setReasonCode] = useState<CompleteVerificationInput['reasonCode']>('DOCUMENT_REPLACEMENT_REQUIRED')
  const [tasks, setTasks] = useState<CorrectionTaskInput[]>([])
  const [validationError, setValidationError] = useState<string>()
  const [staleExpectedId, setStaleExpectedId] = useState<string>()
  const [pendingReconciliation, setPendingReconciliation] = useState<PendingReconciliation>()

  useEffect(() => () => setAssessmentNote(''), [])

  const data = query.data
  const manual = data?.productCode === 'UNSECURED_CONSUMER_LOAN' || data?.productCode === 'COLLATERAL_LOAN'
    ? data as ManualVerificationCase
    : undefined
  const currentResult = manual?.productVerification.currentCycle.productVerificationResult
  const currentVerificationId = manual?.productVerification.currentCycle.verificationId
  const knownPending = currentResult === 'PENDING_MANUAL_REVIEW'
  const startAvailable = Boolean(manual && data?.actions.startAvailable && knownPending && data.applicationStatus === 'SUBMITTED' && !query.isError && !staleExpectedId && !pendingReconciliation)
  const completeAvailable = Boolean(manual && data?.actions.completeAvailable && knownPending && data.applicationStatus === 'VERIFICATION_PENDING' && !query.isError && !staleExpectedId && !pendingReconciliation)

  const invalidateRelated = async () => {
    await queryClient.invalidateQueries({ queryKey: staffReviewKeys.all })
    if (canReadCase) await queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all })
  }

  const authoritativeRefresh = async () => {
    const refreshed = await query.refetch({ throwOnError: true })
    return refreshed.data
  }

  const reconcile = async (pending: PendingReconciliation) => {
    setPendingReconciliation(pending)
    setOperation({ status: 'RECONCILING' })
    try {
      const refreshed = await authoritativeRefresh()
      setPendingReconciliation(undefined)
      const confirmed = pending.kind === 'start'
        ? refreshed?.applicationStatus === 'VERIFICATION_PENDING'
          && refreshed.productCode !== 'SALARY_ADVANCE'
          && refreshed.productVerification.currentCycle.productVerificationResult === 'PENDING_MANUAL_REVIEW'
        : (refreshed?.productCode === 'SALARY_ADVANCE'
          ? refreshed.productVerification.productVerificationResult
          : refreshed?.productVerification.currentCycle.productVerificationResult) === pending.outcome
      const staleCollateral = pending.commandError instanceof ApiError
        && pending.commandError.errorCode === 'STALE_COLLATERAL_VERIFICATION'
      if (staleCollateral) {
        setOperation({ status: 'BLOCKED', error: pending.commandError, detail: 'The Collateral verification cycle changed. Your unsent entries are retained on this page. Review the new cycle before confirming again.' })
        return
      }
      if (confirmed) {
        await invalidateRelated().catch(() => undefined)
        if (pending.kind === 'complete') {
          setAssessmentNote('')
          setTasks([])
        }
      }
      const resolvedDetail = pending.kind === 'start'
        ? 'Verification start was confirmed after Meridian refreshed the review.'
        : 'The verification outcome was confirmed after Meridian refreshed the review.'
      const blockedDetail = pending.commandError instanceof ApiError
        ? 'The action was rejected. Review the latest verification information before deciding whether to try again.'
        : pending.kind === 'start'
          ? 'Verification start was not confirmed. Review the current verification before submitting again.'
          : 'Verification completion was not confirmed. Review the current outcome before submitting again. Meridian did not submit the action again automatically.'
      setOperation({
        status: confirmed ? 'RESOLVED' : 'BLOCKED',
        error: confirmed ? undefined : pending.commandError,
        detail: confirmed ? resolvedDetail : blockedDetail,
      })
    } catch (refreshError) {
      const error = pending.commandError ?? (refreshError instanceof Error ? refreshError : new NetworkError())
      setOperation({
        status: pending.commandError instanceof ApiError ? 'BLOCKED' : 'RESULT_UNKNOWN',
        error,
        detail: pending.commandError instanceof ApiError
          ? 'The action was rejected, but the latest verification information could not be loaded. Verification actions remain unavailable; use Refresh before another attempt.'
          : 'Meridian could not confirm the result or load the latest verification information. Verification actions remain unavailable; use Refresh before another attempt.',
      })
    }
  }

  const refreshWorkspace = async () => {
    if (pendingReconciliation) {
      await reconcile(pendingReconciliation)
      return
    }
    await Promise.all([query.refetch(), ...(canReadCase ? [caseQuery.refetch()] : [])])
  }

  const runStart = async () => {
    if (!data || !manual) return
    setConfirmation(null)
    setOperation({ status: 'IN_FLIGHT' })
    let commandError: Error | undefined
    try {
      await startVerification(manager, data)
    } catch (error) {
      commandError = error instanceof Error ? error : new NetworkError()
      if (!(commandError instanceof ApiError)) setOperation({ status: 'RESULT_UNKNOWN', error: commandError })
    }
    await reconcile({ kind: 'start', commandError })
  }

  const validateCompletion = (): boolean => {
    const note = assessmentNote.trim()
    if (note.length < 1 || note.length > 2000) {
      setValidationError('Assessment note must contain between 1 and 2,000 characters.')
      return false
    }
    if (outcome !== 'REQUIRES_MORE_INFORMATION') {
      setValidationError(undefined)
      return true
    }
    if (!reasonCode || tasks.length < 1 || tasks.length > 10) {
      setValidationError('Select a reason and add between 1 and 10 correction tasks.')
      return false
    }
    const invalid = tasks.some((task) => !data?.correctionTargets.some((target) => target.checklistItemId === task.targetId)
      || task.instruction.trim().length < 1 || task.instruction.trim().length > 500)
    const duplicate = new Set(tasks.map((task) => `${task.targetId}:${task.scope}`)).size !== tasks.length
    if (invalid || duplicate) {
      setValidationError(invalid ? 'Every correction task needs current evidence and an instruction of 1 to 500 characters.' : 'Duplicate correction tasks are not allowed.')
      return false
    }
    setValidationError(undefined)
    return true
  }

  const runComplete = async (expectedVerificationId?: string) => {
    if (!data || !manual || !validateCompletion()) return
    setConfirmation(null)
    const input: CompleteVerificationInput = {
      expectedVerificationId,
      outcome,
      assessmentNote: assessmentNote.trim(),
      ...(outcome === 'REQUIRES_MORE_INFORMATION' ? { reasonCode, tasks } : {}),
    }
    setOperation({ status: 'IN_FLIGHT' })
    let commandError: Error | undefined
    try {
      await completeVerification(manager, data, input)
    } catch (error) {
      commandError = error instanceof Error ? error : new NetworkError()
      if (commandError instanceof ApiError && commandError.errorCode === 'STALE_COLLATERAL_VERIFICATION' && expectedVerificationId) {
        setStaleExpectedId(expectedVerificationId)
      }
      if (!(commandError instanceof ApiError)) setOperation({ status: 'RESULT_UNKNOWN', error: commandError })
    }
    await reconcile({ kind: 'complete', outcome, commandError })
  }

  const reasonOptions = useMemo(() => data?.productCode === 'COLLATERAL_LOAN'
    ? ['DOCUMENT_REPLACEMENT_REQUIRED', 'DOCUMENT_REVIEW_REQUIRED'] as const
    : ['DOCUMENT_REPLACEMENT_REQUIRED', 'DOCUMENT_REVIEW_REQUIRED', 'SUPPORTING_DOCUMENT_REQUIRED'] as const, [data?.productCode])

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Verification unavailable</h1><Alert variant="warning"><History /><AlertTitle>Verification unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 tabIndex={-1} className="text-2xl font-semibold">Loading product verification</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading verification evidence…</div></section>
  if (query.isError && !data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Product verification</h1><QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} /></section>
  if (!data) return null

  const openCompleteConfirmation = () => {
    if (!validateCompletion()) return
    setConfirmation({ kind: 'complete', expectedVerificationId: data.productCode === 'COLLATERAL_LOAN' ? currentVerificationId : undefined })
  }

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
    } } : { source: 'feature', facts: featureFacts }} activeSection="verification"
    updatedAt={query.dataUpdatedAt} refreshing={query.isFetching || (canReadCase && caseQuery.isFetching)} stale={query.isStale || (canReadCase && caseQuery.isStale)}
    contextUnavailable={canReadCase && caseQuery.isError}
    onRetryContext={canReadCase ? () => void caseQuery.refetch() : undefined}
    onRefresh={() => void refreshWorkspace()}
  ><h2 className="text-xl font-semibold">Product assessment</h2><Card><CardHeader><CardTitle>Document readiness</CardTitle></CardHeader><CardContent><dl className="grid gap-3 sm:grid-cols-2"><Readiness label="Upload completeness" value={data.documentReadiness.uploadComplete} /><Readiness label="Processing readiness" value={data.documentReadiness.processingReady} /></dl></CardContent></Card><Card><CardHeader><CardTitle>Product assessment / verification</CardTitle></CardHeader><CardContent><>{query.isError ? <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} /> : <ProductAssessmentEvidence data={data} />}</></CardContent></Card>{data.productCode !== 'SALARY_ADVANCE' ? <><Card><CardHeader><CardTitle>Verification action</CardTitle><p className="text-sm text-muted-foreground">If a verification result cannot be confirmed, refresh before trying again. Meridian will not submit it again automatically.</p></CardHeader><CardContent className="space-y-5">{startAvailable ? <Button id="verification-action-trigger" onClick={() => setConfirmation({ kind: 'start' })}>Start manual verification</Button> : null}{completeAvailable ? <div className="space-y-5"><fieldset className="space-y-3"><legend className="font-semibold">Verification outcome</legend>{(['VERIFIED', 'FAILED', 'REQUIRES_MORE_INFORMATION'] as const).map((value) => <label key={value} className="flex min-h-11 items-center gap-3 rounded-md border px-4"><input type="radio" name="verification-outcome" value={value} checked={outcome === value} onChange={() => setOutcome(value)} />{resultLabel(value)}</label>)}</fieldset><label className="grid gap-2 text-sm font-semibold">Assessment note <textarea className="min-h-32 rounded-md border bg-card p-3 font-normal" value={assessmentNote} maxLength={2000} onChange={(event) => setAssessmentNote(event.target.value)} aria-describedby="assessment-help" /></label><p id="assessment-help" className="text-sm text-muted-foreground">Submitted assessment is saved as restricted lending evidence for entitled lending Staff and is not Customer-facing.</p>{outcome === 'REQUIRES_MORE_INFORMATION' ? <div className="space-y-4 rounded-md border p-4"><label className="grid gap-2 text-sm font-semibold">Controlled reason<select className="h-11 rounded-md border bg-card px-3 font-normal" value={reasonCode} onChange={(event) => setReasonCode(event.target.value as CompleteVerificationInput['reasonCode'])}>{reasonOptions.map((reason) => <option key={reason} value={reason}>{humanizeKnownValue(reason)}</option>)}</select></label><div className="space-y-3"><div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold">Correction tasks</h3><Button type="button" variant="outline" onClick={() => data.correctionTargets[0] && setTasks((current) => [...current, { targetId: data.correctionTargets[0]!.checklistItemId, scope: 'DOCUMENT_REPLACEMENT', instruction: '' }])} disabled={tasks.length >= 10 || data.correctionTargets.length === 0}>Add task</Button></div>{data.correctionTargets.length === 0 ? <Alert variant="warning"><AlertTriangle /><AlertTitle>No correction target available</AlertTitle><AlertDescription>A current document is required before you can request this correction.</AlertDescription></Alert> : null}{tasks.map((task, index) => <div key={`${index}-${task.targetId}`} className="grid gap-3 rounded-md bg-muted/25 p-4"><label className="grid gap-2 text-sm font-semibold">Evidence<select className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={task.targetId} onChange={(event) => setTasks((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, targetId: event.target.value } : item))}>{data.correctionTargets.map((target) => <option key={target.checklistItemId} value={target.checklistItemId}>{humanizeKnownValue(target.documentType)}</option>)}</select></label><label className="grid gap-2 text-sm font-semibold">Task type<select className="h-11 rounded-md border bg-card px-3 font-normal" value={task.scope} onChange={(event) => setTasks((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, scope: event.target.value as CorrectionTaskInput['scope'] } : item))}><option value="DOCUMENT_REPLACEMENT">Customer document replacement</option><option value="DOCUMENT_REVIEW">Staff document review</option></select></label><label className="grid gap-2 text-sm font-semibold">{task.scope === 'DOCUMENT_REPLACEMENT' ? 'Customer instruction' : 'Staff instruction'}<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={task.instruction} maxLength={500} onChange={(event) => setTasks((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, instruction: event.target.value } : item))} /></label><Button type="button" variant="outline" onClick={() => setTasks((current) => current.filter((_, itemIndex) => itemIndex !== index))}>Remove task</Button></div>)}</div></div> : null}{validationError ? <p role="alert" className="font-semibold text-danger">{validationError}</p> : null}<Button id="verification-action-trigger" variant={outcome === 'FAILED' ? 'destructive' : 'default'} onClick={openCompleteConfirmation}>Review verification completion</Button></div> : null}{!startAvailable && !completeAvailable ? <p className="text-sm text-muted-foreground">No verification action is available for the application's current state.</p> : null}{staleExpectedId ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Verification cycle changed</AlertTitle><AlertDescription><p>The prior confirmation targeted <span className="break-all font-semibold">{staleExpectedId}</span>. Review the newly loaded cycle before enabling a new confirmation.</p><Button className="mt-3" variant="outline" onClick={() => setStaleExpectedId(undefined)}>Review updated cycle</Button></AlertDescription></Alert> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p className="text-sm font-medium" aria-live="polite">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent></Card></> : null}{confirmation && !query.isError ? <ConfirmationDialog onDismiss={() => { setConfirmation(null); setTimeout(() => document.getElementById('verification-action-trigger')?.focus(), 0) }}><div><ConfirmationDialogTitle className="text-xl font-semibold">{confirmation.kind === 'start' ? 'Confirm verification start' : 'Confirm verification completion'}</ConfirmationDialogTitle><ConfirmationDialogDescription className="mt-1 text-sm text-muted-foreground">Meridian will not submit this action again automatically if the result cannot be confirmed.</ConfirmationDialogDescription></div><dl className="grid gap-3 text-sm"><Fact label="Application">{data.applicationNumber}</Fact><Fact label="Product">{productLabel(data.productCode)}</Fact><Fact label="Current status">{applicationStatusLabel(data.applicationStatus)}</Fact><Fact label="Current cycle">{manual?.productVerification.currentCycle.verificationSequence} · {resultLabel(currentResult ?? '')}</Fact><Fact label="Document processing">{data.documentReadiness.processingReady ? 'Ready' : 'Not ready'}</Fact>{confirmation.kind === 'complete' ? <><Fact label="Outcome">{resultLabel(outcome)}</Fact>{outcome === 'REQUIRES_MORE_INFORMATION' ? <Fact label="Correction tasks">{tasks.length} correction task{tasks.length === 1 ? '' : 's'}</Fact> : null}</> : null}</dl><ConfirmationDialogFooter><ConfirmationDialogCancel asChild><Button variant="outline">Cancel</Button></ConfirmationDialogCancel><Button autoFocus variant={confirmation.kind === 'complete' && outcome === 'FAILED' ? 'destructive' : 'default'} onClick={() => confirmation.kind === 'start' ? void runStart() : void runComplete(confirmation.expectedVerificationId)}><CheckCircle2 /> {confirmation.kind === 'start' ? 'Start manual verification' : 'Record verification outcome'}</Button></ConfirmationDialogFooter></ConfirmationDialog> : null}</ApplicationWorkspaceShell>
}
