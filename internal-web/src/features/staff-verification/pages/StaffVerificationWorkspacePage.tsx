import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, History, RefreshCw, ShieldCheck } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { StatusBadge } from '@/features/staff-applications/components/StatusBadge'
import { applicationStatusLabel, humanizeKnownValue, productLabel } from '@/features/staff-applications/model/presentation'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import {
  type CompleteVerificationInput,
  type CorrectionTaskInput,
  type ManualVerificationCase,
  type StaffVerificationCase,
  type VerificationOutcome,
} from '../api/contracts'
import { staffVerificationCaseQuery } from '../api/queries'
import { completeVerification, startVerification } from '../api/staff-verification-api'

type OperationState = { status: OperationStatus; error?: Error; detail?: string }
type Confirmation = { kind: 'start' } | { kind: 'complete'; expectedVerificationId?: string }
type PendingReconciliation =
  | { kind: 'start'; commandError?: Error }
  | { kind: 'complete'; outcome: VerificationOutcome; commandError?: Error }

const resultLabels: Record<string, string> = {
  PENDING_MANUAL_REVIEW: 'Pending manual review',
  VERIFIED: 'Verified',
  FAILED: 'Failed',
  REQUIRES_MORE_INFORMATION: 'Requires more information',
}

function resultLabel(value: string): string {
  return resultLabels[value] ?? 'Verification result unavailable'
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-semibold">{children}</dd></div>
}

function Readiness({ label, value }: { label: string; value: boolean }) {
  return <div className="rounded-md border bg-background p-4"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="mt-1 font-semibold">{value ? 'Ready' : 'Not ready'}</dd></div>
}

function CaseHeader({ data, onRefresh, refreshing }: { data: StaffVerificationCase; onRefresh: () => void; refreshing: boolean }) {
  return <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div className="min-w-0"><p className="text-sm font-semibold text-muted-foreground">PRODUCT VERIFICATION</p><h1 data-route-heading tabIndex={-1} className="mt-1 break-words text-2xl font-semibold tracking-tight sm:text-3xl">{data.applicationNumber}</h1><div className="mt-3 flex flex-wrap items-center gap-3"><StatusBadge status={data.applicationStatus} /><span className="text-sm text-muted-foreground">{productLabel(data.productCode)}</span></div></div><Button variant="outline" onClick={onRefresh} disabled={refreshing}><RefreshCw className={refreshing ? 'animate-spin' : undefined} />{refreshing ? 'Refreshing…' : 'Refresh'}</Button></div><dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Application ID"><span className="break-all">{data.loanApplicationId}</span></Fact><Fact label="Requested amount">{formatVnd(data.requestedAmount)}</Fact><Fact label="Requested term">{data.requestedTermMonths} months</Fact><Fact label="Submitted">{formatTimestamp(data.submittedAt)}</Fact></dl></header>
}

function VerificationHistory({ data }: { data: ManualVerificationCase }) {
  return <Card><CardHeader><CardTitle>Verification cycle history</CardTitle><p className="text-sm text-muted-foreground">Immutable cycles are ordered from earliest to latest. Restricted assessment notes and reviewer identities are not exposed here.</p></CardHeader><CardContent><ol className="space-y-3">{data.productVerification.history.map((cycle) => <li key={cycle.verificationId} className="rounded-md border p-4"><div className="flex flex-wrap items-center justify-between gap-2"><span className="font-semibold">Cycle {cycle.verificationSequence}</span><span className="rounded-full border px-3 py-1 text-xs font-semibold">{resultLabel(cycle.productVerificationResult)}</span></div><dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2"><Fact label="Created">{formatTimestamp(cycle.createdAt)}</Fact><Fact label="Reviewed">{cycle.reviewedAt ? formatTimestamp(cycle.reviewedAt) : 'Not completed'}</Fact><Fact label="Verification ID"><span className="break-all">{cycle.verificationId}</span></Fact></dl></li>)}</ol></CardContent></Card>
}

function SalaryAdvancePanel({ data }: { data: Extract<StaffVerificationCase, { productCode: 'SALARY_ADVANCE' }> }) {
  const verification = data.productVerification
  return <Card><CardHeader><CardTitle>Immutable Salary Advance verification</CardTitle><p className="text-sm text-muted-foreground">These are Loan-owned snapshots captured during submission or correction revalidation. They are not live Partner values.</p></CardHeader><CardContent className="space-y-5"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Sequence">{verification.verificationSequence}</Fact><Fact label="Employee outcome">{humanizeKnownValue(verification.employeeVerificationOutcome)}</Fact><Fact label="Product result">{resultLabel(verification.productVerificationResult)}</Fact><Fact label="Total limit snapshot">{formatVnd(verification.totalLimitSnapshot)}</Fact><Fact label="Used amount snapshot">{formatVnd(verification.usedAmountSnapshot)}</Fact><Fact label="Reserved amount snapshot">{formatVnd(verification.reservedAmountSnapshot)}</Fact><Fact label="Available limit snapshot">{formatVnd(verification.availableLimitSnapshot)}</Fact><Fact label="Verified">{formatTimestamp(verification.verifiedAt)}</Fact></dl><Alert variant="information"><ShieldCheck /><AlertTitle>Read-only evidence</AlertTitle><AlertDescription>No manual Salary Advance verification or client-side limit calculation is available.</AlertDescription></Alert></CardContent></Card>
}

function CollateralPanel({ data }: { data: Extract<StaffVerificationCase, { productCode: 'COLLATERAL_LOAN' }> }) {
  const collateral = data.productVerification.collateral
  return <Card><CardHeader><CardTitle>Collateral assessment snapshot</CardTitle><p className="text-sm text-muted-foreground">The submitted asset facts are authoritative assessment evidence. No loan-to-value ratio is calculated in this workspace.</p></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2"><Fact label="Collateral type">{humanizeKnownValue(collateral.collateralType)}</Fact><Fact label="Estimated value">{formatVnd(collateral.estimatedValue)}</Fact><Fact label="Description">{collateral.description}</Fact><Fact label="Ownership status">{collateral.ownershipStatus}</Fact><Fact label="Condition note">{collateral.conditionNote}</Fact></dl></CardContent></Card>
}

export function StaffVerificationWorkspacePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canReview = state.status === 'authenticated' && hasPermission(state.actor, 'loan:review')
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const query = useQuery(staffVerificationCaseQuery(manager, loanApplicationId, canReview && validId))
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
  const startAvailable = Boolean(manual && data?.actions.startAvailable && knownPending && !staleExpectedId && !pendingReconciliation)
  const completeAvailable = Boolean(manual && data?.actions.completeAvailable && knownPending && !staleExpectedId && !pendingReconciliation)

  const invalidateRelated = async () => {
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
        setOperation({ status: 'BLOCKED', error: pending.commandError, detail: 'The Collateral verification cycle changed. Your unsent form remains in memory; review the new cycle before confirming again.' })
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
        ? 'The authoritative read confirms that verification started.'
        : 'The authoritative read confirms the completed verification outcome.'
      const blockedDetail = pending.commandError instanceof ApiError
        ? 'The command was rejected. The refreshed authoritative state permits the operator to review whether a new explicit attempt is appropriate.'
        : pending.kind === 'start'
          ? 'The authoritative read does not confirm the command. Review the current state before explicitly trying again.'
          : 'The authoritative read does not confirm completion. No POST was retried.'
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
          ? 'The command was rejected, but authoritative state could not be refreshed. Mutation actions remain unavailable; use Refresh to reconcile before another attempt.'
          : 'The operation result is still unknown because authoritative state could not be refreshed. Mutation actions remain unavailable; use Refresh to reconcile it.',
      })
    }
  }

  const refreshWorkspace = async () => {
    if (pendingReconciliation) {
      await reconcile(pendingReconciliation)
      return
    }
    await query.refetch()
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
      setValidationError('Select a controlled reason and provide between 1 and 10 correction tasks.')
      return false
    }
    const invalid = tasks.some((task) => !data?.correctionTargets.some((target) => target.checklistItemId === task.targetId)
      || task.instruction.trim().length < 1 || task.instruction.trim().length > 500)
    const duplicate = new Set(tasks.map((task) => `${task.targetId}:${task.scope}`)).size !== tasks.length
    if (invalid || duplicate) {
      setValidationError(invalid ? 'Every correction task needs authoritative evidence and an instruction of 1 to 500 characters.' : 'Duplicate correction tasks are not allowed.')
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
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading product verification</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative verification evidence…</div></section>
  if (query.isError && !data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Product verification</h1><QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} /></section>
  if (!data) return null

  const openCompleteConfirmation = () => {
    if (!validateCompletion()) return
    setConfirmation({ kind: 'complete', expectedVerificationId: data.productCode === 'COLLATERAL_LOAN' ? currentVerificationId : undefined })
  }

  return <section className="mx-auto max-w-6xl space-y-6"><div className="flex flex-wrap gap-4">{canReadCase ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}`}>← Application overview</Link> : null}<Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/review`}>Review workspace</Link></div><CaseHeader data={data} onRefresh={() => void refreshWorkspace()} refreshing={query.isFetching} />{query.isError ? <Alert variant="warning"><RefreshCw /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>The last validated verification evidence remains visible but cannot authorize a mutation until Refresh succeeds.</AlertDescription></Alert> : null}<Card><CardHeader><CardTitle>Document readiness</CardTitle></CardHeader><CardContent><dl className="grid gap-3 sm:grid-cols-2"><Readiness label="Upload completeness" value={data.documentReadiness.uploadComplete} /><Readiness label="Processing readiness" value={data.documentReadiness.processingReady} /></dl></CardContent></Card>{data.productCode === 'SALARY_ADVANCE' ? <SalaryAdvancePanel data={data} /> : <><Card><CardHeader><CardTitle>Current verification cycle</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Sequence">{data.productVerification.currentCycle.verificationSequence}</Fact><Fact label="Result">{resultLabel(data.productVerification.currentCycle.productVerificationResult)}</Fact><Fact label="Verification ID"><span className="break-all">{data.productVerification.currentCycle.verificationId}</span></Fact></dl></CardContent></Card>{data.productCode === 'COLLATERAL_LOAN' ? <CollateralPanel data={data} /> : null}<VerificationHistory data={data} /><Card><CardHeader><CardTitle>Verification action</CardTitle><p className="text-sm text-muted-foreground">Availability comes from the backend and every command revalidates authoritative state. A network failure is reconciled with GET and never automatically retries the POST.</p></CardHeader><CardContent className="space-y-5">{startAvailable ? <Button id="verification-action-trigger" onClick={() => setConfirmation({ kind: 'start' })}>Start manual verification</Button> : null}{completeAvailable ? <div className="space-y-5"><fieldset className="space-y-3"><legend className="font-semibold">Verification outcome</legend>{(['VERIFIED', 'FAILED', 'REQUIRES_MORE_INFORMATION'] as const).map((value) => <label key={value} className="flex min-h-11 items-center gap-3 rounded-md border px-4"><input type="radio" name="verification-outcome" value={value} checked={outcome === value} onChange={() => setOutcome(value)} />{resultLabel(value)}</label>)}</fieldset><label className="grid gap-2 text-sm font-semibold">Assessment note <textarea className="min-h-32 rounded-md border bg-card p-3 font-normal" value={assessmentNote} maxLength={2000} onChange={(event) => setAssessmentNote(event.target.value)} aria-describedby="assessment-help" /></label><p id="assessment-help" className="text-sm text-muted-foreground">Restricted Staff input. It remains in memory only and is cleared after confirmed completion.</p>{outcome === 'REQUIRES_MORE_INFORMATION' ? <div className="space-y-4 rounded-md border p-4"><label className="grid gap-2 text-sm font-semibold">Controlled reason<select className="h-11 rounded-md border bg-card px-3 font-normal" value={reasonCode} onChange={(event) => setReasonCode(event.target.value as CompleteVerificationInput['reasonCode'])}>{reasonOptions.map((reason) => <option key={reason} value={reason}>{humanizeKnownValue(reason)}</option>)}</select></label><div className="space-y-3"><div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold">Correction tasks</h3><Button type="button" variant="outline" onClick={() => data.correctionTargets[0] && setTasks((current) => [...current, { targetId: data.correctionTargets[0]!.checklistItemId, scope: 'DOCUMENT_REPLACEMENT', instruction: '' }])} disabled={tasks.length >= 10 || data.correctionTargets.length === 0}>Add task</Button></div>{data.correctionTargets.length === 0 ? <Alert variant="warning"><AlertTriangle /><AlertTitle>No correction target available</AlertTitle><AlertDescription>The backend did not return an authoritative current document version. More-information completion is disabled.</AlertDescription></Alert> : null}{tasks.map((task, index) => <div key={`${index}-${task.targetId}`} className="grid gap-3 rounded-md bg-muted/25 p-4"><label className="grid gap-2 text-sm font-semibold">Evidence<select className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={task.targetId} onChange={(event) => setTasks((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, targetId: event.target.value } : item))}>{data.correctionTargets.map((target) => <option key={target.checklistItemId} value={target.checklistItemId}>{humanizeKnownValue(target.documentType)}</option>)}</select></label><label className="grid gap-2 text-sm font-semibold">Task type<select className="h-11 rounded-md border bg-card px-3 font-normal" value={task.scope} onChange={(event) => setTasks((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, scope: event.target.value as CorrectionTaskInput['scope'] } : item))}><option value="DOCUMENT_REPLACEMENT">Customer document replacement</option><option value="DOCUMENT_REVIEW">Staff document review</option></select></label><label className="grid gap-2 text-sm font-semibold">{task.scope === 'DOCUMENT_REPLACEMENT' ? 'Customer instruction' : 'Staff instruction'}<textarea className="min-h-24 rounded-md border bg-card p-3 font-normal" value={task.instruction} maxLength={500} onChange={(event) => setTasks((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, instruction: event.target.value } : item))} /></label><Button type="button" variant="outline" onClick={() => setTasks((current) => current.filter((_, itemIndex) => itemIndex !== index))}>Remove task</Button></div>)}</div></div> : null}{validationError ? <p role="alert" className="font-semibold text-danger">{validationError}</p> : null}<Button id="verification-action-trigger" variant={outcome === 'FAILED' ? 'destructive' : 'default'} onClick={openCompleteConfirmation}>Review verification completion</Button></div> : null}{!startAvailable && !completeAvailable ? <p className="text-sm text-muted-foreground">No verification command is available for the authoritative current state.</p> : null}{staleExpectedId ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Verification cycle changed</AlertTitle><AlertDescription><p>The prior confirmation targeted <span className="break-all font-semibold">{staleExpectedId}</span>. Review the newly loaded cycle before enabling a new confirmation.</p><Button className="mt-3" variant="outline" onClick={() => setStaleExpectedId(undefined)}>Review updated cycle</Button></AlertDescription></Alert> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p className="text-sm font-medium" aria-live="polite">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent></Card></>}{confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="verification-confirm-title"><div className="max-h-[90vh] w-full max-w-lg space-y-4 overflow-y-auto rounded-lg bg-card p-6 shadow-xl"><div><h2 id="verification-confirm-title" className="text-xl font-semibold">{confirmation.kind === 'start' ? 'Confirm verification start' : 'Confirm verification completion'}</h2><p className="mt-1 text-sm text-muted-foreground">This command has no idempotency key and will not be retried automatically.</p></div><dl className="grid gap-3 text-sm"><Fact label="Application">{data.applicationNumber}</Fact><Fact label="Product">{productLabel(data.productCode)}</Fact><Fact label="Current status">{applicationStatusLabel(data.applicationStatus)}</Fact><Fact label="Current cycle">{manual?.productVerification.currentCycle.verificationSequence} · {resultLabel(currentResult ?? '')}</Fact><Fact label="Document processing">{data.documentReadiness.processingReady ? 'Ready' : 'Not ready'}</Fact>{confirmation.kind === 'complete' ? <><Fact label="Outcome">{resultLabel(outcome)}</Fact>{outcome === 'REQUIRES_MORE_INFORMATION' ? <Fact label="Correction tasks">{tasks.length} structured task{tasks.length === 1 ? '' : 's'}</Fact> : null}</> : null}</dl><div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={() => { setConfirmation(null); setTimeout(() => document.getElementById('verification-action-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus variant={confirmation.kind === 'complete' && outcome === 'FAILED' ? 'destructive' : 'default'} onClick={() => confirmation.kind === 'start' ? void runStart() : void runComplete(confirmation.expectedVerificationId)}><CheckCircle2 /> Confirm</Button></div></div></div> : null}</section>
}
