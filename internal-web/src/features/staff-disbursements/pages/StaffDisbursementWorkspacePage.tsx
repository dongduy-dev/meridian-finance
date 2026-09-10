import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Clock3, Eye, EyeOff, RefreshCw, ShieldAlert } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { StatusBadge } from '@/features/staff-applications/components/StatusBadge'
import { humanizeKnownValue, productLabel } from '@/features/staff-applications/model/presentation'
import { staffContractKeys } from '@/features/staff-contracts/api/queries'
import { ApiError, NetworkError } from '@/lib/api'
import { formatDateOnly, formatTimestamp, formatVnd } from '@/lib/format/presentation'
import {
  decideOperationIdentity,
  digestOperationPayload,
  findUnresolvedOperation,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
  type UnresolvedOperationType,
} from '@/lib/operation/unresolved-operation'
import type {
  DisbursementDestinationReveal,
  DisbursementSemanticPayload,
  ManualDisbursementConfirmation,
  StaffDisbursementCase,
} from '../api/contracts'
import { staffDisbursementCaseQuery, staffDisbursementKeys } from '../api/queries'
import { confirmManualDisbursement, revealDisbursementDestination } from '../api/staff-disbursements-api'
import {
  disbursementStageLabel,
  hasCoherentDisbursementCase,
  knownDisbursementApplicationStatuses,
  knownDisbursementContractStatuses,
  knownDisbursementWorkStages,
} from '../model/presentation'

export const REVEALED_DESTINATION_BACKGROUND_TIMEOUT_MS = 60_000
const operationType: UnresolvedOperationType = 'DISBURSEMENT_CONFIRMATION'

type OperationState = { status: OperationStatus; detail?: string; error?: Error }
type FormState = {
  externalTransferReference: string
  disbursementValueDate: string
  firstRepaymentDate: string
}

const emptyForm: FormState = {
  externalTransferReference: '',
  disbursementValueDate: '',
  firstRepaymentDate: '',
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-semibold">{children}</dd></div>
}

function exactPayload(
  loanApplicationId: string,
  expectedContractVersion: number,
  form: FormState,
): DisbursementSemanticPayload {
  return {
    loanApplicationId,
    expectedContractVersion,
    externalTransferReference: form.externalTransferReference.trim().toUpperCase(),
    disbursementValueDate: form.disbursementValueDate,
    firstRepaymentDate: form.firstRepaymentDate,
  }
}

function isUnknownOutcome(error: Error) {
  return error instanceof NetworkError || (error instanceof ApiError && error.status >= 500)
}

function isPendingCase(value: StaffDisbursementCase | undefined) {
  return Boolean(value
    && value.applicationStatus === 'DISBURSEMENT_PENDING'
    && value.workStage === 'READY_TO_DISBURSE'
    && value.currentContract.status === 'READY_FOR_DISBURSEMENT'
    && value.currentContract.readinessConfirmedAt)
}

export function StaffDisbursementWorkspacePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canDisburse = state.status === 'authenticated' && hasPermission(state.actor, 'loan:disburse')
  const canReadApplication = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const isAccounting = state.status === 'authenticated' && hasRole(state.actor, 'ACCOUNTING_OFFICER')
  const query = useQuery(staffDisbursementCaseQuery(manager, loanApplicationId, canDisburse && validId))
  const [revealedDestination, setRevealedDestination] = useState<DisbursementDestinationReveal>()
  const [revealError, setRevealError] = useState<Error>()
  const [revealInFlight, setRevealInFlight] = useState(false)
  const [revealNeedsRevalidation, setRevealNeedsRevalidation] = useState(false)
  const [form, setForm] = useState<FormState>(emptyForm)
  const [formError, setFormError] = useState<string>()
  const [confirmation, setConfirmation] = useState<DisbursementSemanticPayload>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const [inMemoryUnknownPayload, setInMemoryUnknownPayload] = useState<DisbursementSemanticPayload>()
  const [temporaryResult, setTemporaryResult] = useState<ManualDisbursementConfirmation>()
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const [staleEvidence, setStaleEvidence] = useState(false)
  const referenceInputRef = useRef<HTMLInputElement>(null)
  const valueDateInputRef = useRef<HTMLInputElement>(null)
  const repaymentDateInputRef = useRef<HTMLInputElement>(null)
  const resource = loanApplicationId
  const unresolved = validId ? findUnresolvedOperation(operationType, resource) : undefined
  const data = query.data
  const contractIdentity = data
    ? `${data.currentContract.contractId}:${data.currentContract.contractVersion}:${data.applicationStatus}`
    : ''

  const clearRevealedDestination = useCallback(() => {
    setRevealedDestination(undefined)
  }, [])

  useEffect(
    () => () => clearRevealedDestination(),
    [clearRevealedDestination, contractIdentity, loanApplicationId, state.epoch],
  )
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        timer = setTimeout(clearRevealedDestination, REVEALED_DESTINATION_BACKGROUND_TIMEOUT_MS)
      } else if (timer) {
        clearTimeout(timer)
        timer = undefined
      }
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      if (timer) clearTimeout(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [clearRevealedDestination])

  const safeEvidence = useMemo(() => Boolean(data
    && knownDisbursementApplicationStatuses.has(data.applicationStatus)
    && knownDisbursementContractStatuses.has(data.currentContract.status)
    && knownDisbursementWorkStages.has(data.workStage)
    && hasCoherentDisbursementCase(data)), [data])

  const invalidateRelatedReads = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: staffDisbursementKeys.all, refetchType: 'none' }),
      queryClient.invalidateQueries({ queryKey: staffContractKeys.all, refetchType: 'none' }),
      canReadApplication
        ? queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all, refetchType: 'none' })
        : Promise.resolve(),
    ])
  }

  const refetchAuthoritativeCase = async () => {
    const result = await query.refetch({ throwOnError: true })
    if (result.isError) throw result.error instanceof Error ? result.error : new NetworkError()
    return result.data
  }

  const refreshAfterConfirmedCommand = async (result: ManualDisbursementConfirmation) => {
    setTemporaryResult(result)
    setOperation({ status: 'RECONCILING' })
    await invalidateRelatedReads().catch(() => undefined)
    try {
      await refetchAuthoritativeCase()
      setConfirmedRefreshFailed(false)
      setTemporaryResult(undefined)
      setOperation({
        status: 'RESOLVED',
        detail: result.idempotentReplay
          ? 'Recovered the previously recorded disbursement through exact same-request replay.'
          : 'External transfer confirmation is recorded and the durable activation evidence is reconciled.',
      })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({
        status: 'BLOCKED',
        error: error instanceof Error ? error : new NetworkError(),
        detail: 'Disbursement confirmed; refreshed state unavailable. Only the authoritative GET will be retried.',
      })
    }
  }

  const reconcileUnknownResult = async (commandError: Error) => {
    setOperation({ status: 'RECONCILING' })
    try {
      await refetchAuthoritativeCase()
      await invalidateRelatedReads().catch(() => undefined)
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: commandError,
        detail: 'Authoritative state was refreshed, but it cannot prove this exact request identity. Retry the exact operation with the retained UUID.',
      })
    } catch (error) {
      const correlationError = commandError instanceof ApiError && commandError.requestId
        ? commandError
        : error instanceof Error ? error : commandError
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: correlationError,
        detail: 'The command result remains unknown. No POST was retried automatically.',
      })
    }
  }

  const postCommand = async (
    payload: DisbursementSemanticPayload,
    requestId: string,
    payloadDigest: string,
    retrying: boolean,
  ) => {
    setOperation({ status: 'IN_FLIGHT' })
    try {
      const result = await confirmManualDisbursement(manager, payload.loanApplicationId, {
        requestId,
        expectedContractVersion: payload.expectedContractVersion,
        externalTransferReference: payload.externalTransferReference,
        disbursementValueDate: payload.disbursementValueDate,
        firstRepaymentDate: payload.firstRepaymentDate,
      })
      removeUnresolvedOperation(operationType, resource)
      setInMemoryUnknownPayload(undefined)
      setForm(emptyForm)
      clearRevealedDestination()
      setRevealError(undefined)
      await refreshAfterConfirmedCommand(result)
    } catch (error) {
      const commandError = error instanceof Error ? error : new NetworkError()
      if (!isUnknownOutcome(commandError)) {
        const idempotencyConflict = commandError instanceof ApiError
          && commandError.errorCode === 'IDEMPOTENCY_KEY_REUSED'
        if (!idempotencyConflict) {
          removeUnresolvedOperation(operationType, resource)
          setInMemoryUnknownPayload(undefined)
        }
        const stale = commandError instanceof ApiError
          && commandError.errorCode === 'CONTRACT_VERSION_STALE'
        const completed = commandError instanceof ApiError
          && commandError.errorCode === 'DISBURSEMENT_ALREADY_COMPLETED'
        if (stale) {
          clearRevealedDestination()
          setStaleEvidence(true)
        }
        if (stale || completed) await query.refetch().catch(() => undefined)
        setOperation({
          status: 'BLOCKED',
          error: commandError,
          detail: idempotencyConflict
            ? 'The retained request identity conflicts with different backend evidence. Do not generate a replacement UUID; operator resolution is required.'
            : stale
              ? 'The exact displayed contract version is stale. Review refreshed evidence before another operation.'
              : completed
                ? 'Disbursement was already completed. The durable case has been refreshed without sending another command.'
                : 'The backend rejected the command with a definite response.',
        })
        return
      }
      saveUnresolvedOperation({
        type: operationType,
        resource,
        operationId: requestId,
        payloadDigest,
        unresolvedAt: new Date().toISOString(),
      })
      setInMemoryUnknownPayload(payload)
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: commandError,
        detail: retrying
          ? 'The exact retry outcome is also unknown. The same UUID and in-memory payload remain retained.'
          : 'The command outcome is unknown. Only the request UUID and payload digest were persisted.',
      })
      await reconcileUnknownResult(commandError)
    }
  }

  const submitPayload = async (payload: DisbursementSemanticPayload) => {
    const payloadDigest = await digestOperationPayload(payload)
    const identity = decideOperationIdentity(operationType, resource, payloadDigest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation({
        status: 'BLOCKED',
        detail: 'The entered evidence does not match the unresolved operation. No sensitive field comparison is disclosed and no new UUID was created.',
      })
      return
    }
    await postCommand(payload, identity.operationId, payloadDigest, identity.kind === 'REUSE_EXISTING')
  }

  const openConfirmation = () => {
    if (!data || !isPendingCase(data) || !safeEvidence || unresolved) return
    const payload = exactPayload(loanApplicationId, data.currentContract.contractVersion, form)
    if (!payload.externalTransferReference || !payload.disbursementValueDate || !payload.firstRepaymentDate) {
      setFormError('Enter the external transfer reference, disbursement value date, and first repayment date.')
      if (!payload.externalTransferReference) referenceInputRef.current?.focus()
      else if (!payload.disbursementValueDate) valueDateInputRef.current?.focus()
      else repaymentDateInputRef.current?.focus()
      return
    }
    setFormError(undefined)
    setConfirmation(payload)
  }

  const retryExactOperation = async () => {
    if (!unresolved || !data) return
    const candidate = inMemoryUnknownPayload
      ?? exactPayload(loanApplicationId, data.currentContract.contractVersion, form)
    if (!candidate.externalTransferReference || !candidate.disbursementValueDate || !candidate.firstRepaymentDate) {
      setFormError('Re-enter all exact command inputs before retrying the unresolved operation.')
      if (!candidate.externalTransferReference) referenceInputRef.current?.focus()
      else if (!candidate.disbursementValueDate) valueDateInputRef.current?.focus()
      else repaymentDateInputRef.current?.focus()
      return
    }
    const candidateDigest = await digestOperationPayload(candidate)
    if (candidateDigest !== unresolved.payloadDigest) {
      setOperation({
        status: 'BLOCKED',
        detail: 'The entered evidence does not match the unresolved operation. No sensitive field comparison is disclosed.',
      })
      return
    }
    setFormError(undefined)
    await postCommand(candidate, unresolved.operationId, candidateDigest, true)
  }

  const handleReveal = async () => {
    if (!data || !isPendingCase(data) || !safeEvidence || revealInFlight) return
    setRevealInFlight(true)
    setRevealError(undefined)
    let authoritative = data
    try {
      if (revealNeedsRevalidation) {
        const refreshed = await refetchAuthoritativeCase()
        if (!refreshed
            || !isPendingCase(refreshed)
            || refreshed.currentContract.contractId !== data.currentContract.contractId
            || refreshed.currentContract.contractVersion !== data.currentContract.contractVersion) {
          throw new Error('The exact contract is no longer eligible for destination reveal.')
        }
        authoritative = refreshed
      }
      const revealed = await revealDisbursementDestination(
        manager,
        loanApplicationId,
        authoritative.currentContract.contractVersion,
      )
      if (revealed.contractId !== authoritative.currentContract.contractId
          || revealed.contractVersion !== authoritative.currentContract.contractVersion) {
        throw new Error('Revealed destination does not match the exact current contract.')
      }
      setRevealedDestination(revealed)
      setRevealNeedsRevalidation(false)
    } catch (error) {
      clearRevealedDestination()
      const revealFailure = error instanceof Error ? error : new NetworkError()
      setRevealError(revealFailure)
      if (isUnknownOutcome(revealFailure)) setRevealNeedsRevalidation(true)
      if (revealFailure instanceof ApiError && revealFailure.errorCode === 'CONTRACT_VERSION_STALE') {
        await query.refetch().catch(() => undefined)
      }
    } finally {
      setRevealInFlight(false)
    }
  }

  const refreshOnly = async () => {
    try {
      await refetchAuthoritativeCase()
      if (confirmedRefreshFailed) {
        await invalidateRelatedReads()
        setConfirmedRefreshFailed(false)
        setTemporaryResult(undefined)
        setOperation({ status: 'RESOLVED', detail: 'The confirmed disbursement is now reconciled with authoritative state.' })
      }
    } catch {
      // React Query retains the query error and last validated data for safe presentation.
    }
  }

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Disbursement workspace unavailable</h1><Alert variant="warning"><Clock3 /><AlertTitle>Disbursement workspace unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading disbursement workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative contract and activation evidence…</div></section>
  const accountingRejected = query.error instanceof ApiError && query.error.errorCode === 'ACCOUNTING_ROLE_REQUIRED'
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Disbursement and activation workspace</h1>{accountingRejected ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>This workspace additionally requires the ACCOUNTING_OFFICER role.</AlertDescription></Alert> : <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} />}</section>
  if (!data) return null
  const contract = data.currentContract
  const controlsLocked = query.isFetching || operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING' || confirmedRefreshFailed || staleEvidence || !safeEvidence
  const commandVisible = data.applicationStatus === 'DISBURSEMENT_PENDING' || Boolean(unresolved)
  const outcomeVisible = !commandVisible && (operation.status !== 'DRAFT' || Boolean(temporaryResult))

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/disbursements">← Disbursement queue</Link>{canReadApplication ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}`}>Application case</Link> : null}</div>
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">DISBURSEMENT AND ACTIVATION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">{data.applicationNumber}</h1><div className="mt-3 flex flex-wrap items-center gap-3"><StatusBadge status={data.applicationStatus} /><span className="text-sm text-muted-foreground">{productLabel(data.productCode)}</span><span className="text-sm font-semibold">{disbursementStageLabel(data.workStage)}</span></div></div><Button variant="outline" onClick={() => void refreshOnly()} disabled={query.isFetching}><RefreshCw className={query.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div><dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Application ID"><span className="break-all">{data.loanApplicationId}</span></Fact><Fact label="Contract">{contract.contractReference}</Fact><Fact label="Exact version">{contract.contractVersion}</Fact><Fact label="Contract status">{humanizeKnownValue(contract.status)}</Fact></dl></header>
    {!isAccounting ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>The route permission is present, but disbursement operations additionally require the ACCOUNTING_OFFICER role.</AlertDescription></Alert> : null}
    {query.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Cached evidence remains visible but cannot authorize a consequential action.</AlertDescription></Alert> : null}
    {!safeEvidence ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Operational evidence unavailable</AlertTitle><AlertDescription>Unknown or contradictory lifecycle evidence requires an authoritative refresh. Consequential actions are disabled.</AlertDescription></Alert> : null}
    {staleEvidence ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Exact contract version changed</AlertTitle><AlertDescription><p>The prior confirmation remains bound to its old version. Review the current contract before continuing.</p><Button className="mt-3" variant="outline" onClick={() => setStaleEvidence(false)}>I reviewed the current contract</Button></AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Exact ready contract</CardTitle></CardHeader><CardContent className="space-y-6"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Approved principal">{formatVnd(contract.approvedPrincipal)}</Fact><Fact label="Approved term">{contract.approvedTermMonths} months</Fact><Fact label="Interest method">{humanizeKnownValue(contract.interestCalculationMethod)}</Fact><Fact label="Flat monthly rate">{contract.flatMonthlyInterestRate}</Fact><Fact label="Total interest">{formatVnd(contract.totalInterest)}</Fact><Fact label="Fee">{formatVnd(contract.feeAmount)}</Fact><Fact label="Total repayment">{formatVnd(contract.totalRepaymentAmount)}</Fact><Fact label="Repayment method">{humanizeKnownValue(contract.repaymentMethod)}</Fact><Fact label="Readiness confirmed">{formatTimestamp(contract.readinessConfirmedAt)}</Fact></dl><div><h3 className="font-semibold">Provisional contract repayment items</h3><div className="mt-3 overflow-x-auto rounded-md border"><table className="min-w-[42rem] w-full text-left text-sm"><thead className="bg-muted text-xs uppercase text-muted-foreground"><tr><th className="p-3">Item</th><th className="p-3">Principal</th><th className="p-3">Interest</th><th className="p-3">Fee</th><th className="p-3">Total</th></tr></thead><tbody>{contract.repaymentPreview.map((item) => <tr key={item.installmentNumber} className="border-t"><td className="p-3 font-semibold">{item.installmentNumber}</td><td className="p-3">{formatVnd(item.principalDue)}</td><td className="p-3">{formatVnd(item.interestDue)}</td><td className="p-3">{formatVnd(item.feeDue)}</td><td className="p-3 font-semibold">{formatVnd(item.totalDue)}</td></tr>)}</tbody></table></div></div></CardContent></Card>
    {data.applicationStatus === 'DISBURSEMENT_PENDING' ? <Card><CardHeader><CardTitle>Contract-bound destination</CardTitle><p className="text-sm text-muted-foreground">The masked destination is ordinary case evidence. The full account number appears only after an explicit sensitive reveal and remains only in this component's memory.</p></CardHeader><CardContent className="space-y-5"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Bank">{contract.disbursementBankAccount.bankName} ({contract.disbursementBankAccount.bankCode})</Fact><Fact label="Account holder">{contract.disbursementBankAccount.accountHolderName}</Fact><Fact label="Masked account">{contract.disbursementBankAccount.maskedAccountNumber}</Fact><Fact label="Captured">{formatTimestamp(contract.disbursementBankAccount.capturedAt)}</Fact></dl>{revealedDestination ? <div className="rounded-md border-2 border-warning bg-warning/5 p-4" aria-live="polite"><p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Sensitive destination — temporary view</p><p className="mt-2 break-all font-mono text-lg font-semibold">{revealedDestination.accountNumber}</p><Button className="mt-4" variant="outline" onClick={clearRevealedDestination}><EyeOff />Hide destination</Button></div> : <Button variant="outline" disabled={controlsLocked || revealInFlight} onClick={() => void handleReveal()}><Eye />{revealInFlight ? 'Revealing…' : 'Reveal destination'}</Button>}{revealError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Reveal result unavailable</AlertTitle><AlertDescription>{revealNeedsRevalidation ? 'No automatic retry occurred. The case will be revalidated before a later explicit reveal.' : 'The destination was not retained. Review the authoritative case before trying again.'}</AlertDescription></Alert> : null}</CardContent></Card> : null}
    {commandVisible ? <Card><CardHeader><CardTitle>Record external transfer</CardTitle><p className="text-sm text-muted-foreground">Meridian records confirmation of an external transfer. Meridian does not initiate the transfer.</p></CardHeader><CardContent className="space-y-5">{unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Previous confirmation result unknown</AlertTitle><AlertDescription>The request UUID and SHA-256 payload digest were retained without the transfer reference. Refreshed DISBURSED state alone cannot prove this request identity. Re-enter the exact evidence after reload, then explicitly retry.</AlertDescription></Alert> : null}<div className="grid gap-4 lg:grid-cols-3"><label className="grid gap-2 text-sm font-semibold">External transfer reference<input ref={referenceInputRef} className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" autoComplete="off" maxLength={64} value={form.externalTransferReference} onChange={(event) => setForm((value) => ({ ...value, externalTransferReference: event.target.value }))} /></label><label className="grid gap-2 text-sm font-semibold">Disbursement value date<input ref={valueDateInputRef} type="date" className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.disbursementValueDate} onChange={(event) => setForm((value) => ({ ...value, disbursementValueDate: event.target.value }))} /></label><label className="grid gap-2 text-sm font-semibold">First repayment date<input ref={repaymentDateInputRef} type="date" className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.firstRepaymentDate} onChange={(event) => setForm((value) => ({ ...value, firstRepaymentDate: event.target.value }))} /></label></div>{formError ? <p role="alert" className="font-semibold text-danger">{formError}</p> : null}<Alert variant="information"><Clock3 /><AlertTitle>External transfer occurs outside Meridian</AlertTitle><AlertDescription>Inspect the exact contract, perform the transfer outside Meridian, then record its evidence here. The backend validates lifecycle, dates, financial evidence, activation, and the final schedule.</AlertDescription></Alert>{unresolved ? <Button disabled={controlsLocked && operation.status !== 'RESULT_UNKNOWN' && operation.status !== 'BLOCKED'} onClick={() => void retryExactOperation()}>Retry exact operation</Button> : <Button id="confirm-disbursement-trigger" disabled={controlsLocked || !isPendingCase(data) || !isAccounting} onClick={openConfirmation}>Review disbursement confirmation</Button>}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{temporaryResult && confirmedRefreshFailed ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Disbursement command confirmed</AlertTitle><AlertDescription>LoanAccount {temporaryResult.loanAccountNumber} was returned by the successful command. Canonical case refresh is still unavailable; retry GET only.</AlertDescription></Alert> : null}</CardContent></Card> : null}
    {outcomeVisible ? <Card><CardHeader><CardTitle>Disbursement outcome</CardTitle></CardHeader><CardContent className="space-y-4"><OperationStatusPanel status={operation.status} />{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{temporaryResult && confirmedRefreshFailed ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Disbursement command confirmed</AlertTitle><AlertDescription>LoanAccount {temporaryResult.loanAccountNumber} was returned by the successful command. Canonical case refresh is still unavailable; retry GET only.</AlertDescription></Alert> : null}</CardContent></Card> : null}
    {data.activation ? <Card><CardHeader><CardTitle>Activation result</CardTitle><p className="text-sm text-muted-foreground">Durable Loan-owned evidence from the disbursement case read. No repayment or servicing action is available here.</p></CardHeader><CardContent className="space-y-6"><Alert variant="success"><CheckCircle2 /><AlertTitle>Application disbursed and account activated</AlertTitle><AlertDescription>The external transfer confirmation, LoanAccount, and final repayment schedule are reconciled.</AlertDescription></Alert><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Fact label="LoanAccount number">{data.activation.loanAccountNumber}</Fact><Fact label="LoanAccount status">{humanizeKnownValue(data.activation.loanAccountStatus)}</Fact><Fact label="Activated">{formatTimestamp(data.activation.activatedAt)}</Fact><Fact label="Disbursed amount">{formatVnd(data.activation.disbursedAmount)}</Fact><Fact label="Value date">{formatDateOnly(data.activation.disbursementValueDate)}</Fact><Fact label="First repayment">{formatDateOnly(data.activation.firstRepaymentDate)}</Fact><Fact label="Schedule type">{humanizeKnownValue(data.activation.scheduleType)}</Fact><Fact label="Schedule version">{data.activation.scheduleVersion}</Fact></dl><div><h3 className="font-semibold">Final repayment schedule</h3><div className="mt-3 overflow-x-auto rounded-md border"><table className="min-w-[48rem] w-full text-left text-sm"><thead className="bg-muted text-xs uppercase text-muted-foreground"><tr><th className="p-3">Installment</th><th className="p-3">Due date</th><th className="p-3">Principal</th><th className="p-3">Interest</th><th className="p-3">Fee</th><th className="p-3">Total</th></tr></thead><tbody>{data.activation.scheduleItems.map((item) => <tr key={item.installmentNumber} className="border-t"><td className="p-3 font-semibold">{item.installmentNumber}</td><td className="p-3">{formatDateOnly(item.dueDate)}</td><td className="p-3">{formatVnd(item.principalDue)}</td><td className="p-3">{formatVnd(item.interestDue)}</td><td className="p-3">{formatVnd(item.feeDue)}</td><td className="p-3 font-semibold">{formatVnd(item.totalDue)}</td></tr>)}</tbody></table></div></div></CardContent></Card> : null}
    {confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="disbursement-confirm-title"><div className="max-h-[90vh] w-full max-w-xl space-y-4 overflow-y-auto rounded-lg bg-card p-6 shadow-xl"><h2 id="disbursement-confirm-title" className="text-xl font-semibold">Confirm disbursement record</h2><p className="text-sm text-muted-foreground">This records an external transfer against exact contract version {confirmation.expectedContractVersion}. Meridian does not initiate the transfer.</p><dl className="grid gap-3 sm:grid-cols-2"><Fact label="Application">{data.applicationNumber}</Fact><Fact label="Exact contract version">{confirmation.expectedContractVersion}</Fact><Fact label="Authoritative principal">{formatVnd(contract.approvedPrincipal)}</Fact><Fact label="Disbursement value date">{formatDateOnly(confirmation.disbursementValueDate)}</Fact><Fact label="First repayment date">{formatDateOnly(confirmation.firstRepaymentDate)}</Fact></dl><p className="text-sm text-muted-foreground">The backend validates all financial and lifecycle evidence and creates the authoritative final LoanAccount schedule. Sensitive destination and transfer-reference values are intentionally omitted from this summary.</p><div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={() => { setConfirmation(undefined); setTimeout(() => document.getElementById('confirm-disbursement-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus onClick={() => { const submitted = confirmation; setConfirmation(undefined); void submitPayload(submitted) }}>Confirm record</Button></div></div></div> : null}
  </section>
}
