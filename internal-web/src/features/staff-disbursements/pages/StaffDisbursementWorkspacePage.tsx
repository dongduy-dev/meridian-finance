import { operatorErrorMessage } from '@/lib/api/operator-error-message'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Clock3, Eye, EyeOff, ShieldAlert } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { ApplicationWorkspaceShell } from '@/components/operations/ApplicationWorkspaceShell'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { useOperationResultFocus } from '@/components/operations/useOperationResultFocus'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { AccountingCaseContextPanel } from '@/features/staff-contracts/components/AccountingCaseContextPanel'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
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
  const requestResultFocus = useOperationResultFocus(operation.status, 'disbursement-command-result')
  const [inMemoryUnknownPayload, setInMemoryUnknownPayload] = useState<DisbursementSemanticPayload>()
  const [temporaryResult, setTemporaryResult] = useState<ManualDisbursementConfirmation>()
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const [staleEvidence, setStaleEvidence] = useState(false)
  const referenceInputRef = useRef<HTMLInputElement>(null)
  const valueDateInputRef = useRef<HTMLInputElement>(null)
  const repaymentDateInputRef = useRef<HTMLInputElement>(null)
  const revealGenerationRef = useRef(0)
  const resource = loanApplicationId
  const unresolved = validId ? findUnresolvedOperation(operationType, resource) : undefined
  const data = query.data
  const contractIdentity = data
    ? `${data.currentContract.contractId}:${data.currentContract.contractVersion}:${data.applicationStatus}`
    : ''

  const clearRevealedDestination = useCallback(() => {
    revealGenerationRef.current += 1
    setRevealedDestination(undefined)
    setRevealInFlight(false)
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
  const controlsLocked = query.isFetching
    || query.isRefetchError
    || operation.status === 'IN_FLIGHT'
    || operation.status === 'RECONCILING'
    || confirmedRefreshFailed
    || staleEvidence
    || !safeEvidence

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
          ? 'The retry confirmed the previously recorded disbursement. The latest loan account activation details are loaded.'
          : 'The external transfer confirmation and loan account activation are confirmed in the latest case information.',
      })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({
        status: 'BLOCKED',
        error: error instanceof Error ? error : new NetworkError(),
        detail: 'Disbursement confirmed; the latest case information is unavailable. Use Refresh, and do not record the transfer again.',
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
        detail: 'The latest case information does not confirm this exact disbursement. Re-enter the same transfer evidence and retry this disbursement with unchanged details. Do not record another transfer.',
      })
    } catch (error) {
      const correlationError = commandError instanceof ApiError && commandError.requestId
        ? commandError
        : error instanceof Error ? error : commandError
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: correlationError,
        detail: 'Meridian could not confirm the disbursement or load the latest case information. It did not submit the action again automatically.',
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
            ? 'The saved recovery action conflicts with recorded evidence. Do not start a replacement action; operator resolution is required.'
            : stale
              ? 'The displayed contract version is out of date. Review the updated contract before taking action.'
              : completed
                ? 'Disbursement was already completed. The latest case information was loaded without submitting another action.'
                : operatorErrorMessage(commandError, 'The action was rejected. Review the reason before trying again.'),
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
          ? 'The disbursement is still unconfirmed. Re-enter the same transfer evidence to retry this action again.'
          : 'The disbursement result is not confirmed. Re-enter the same transfer evidence and retry this disbursement with unchanged details.',
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
        detail: 'The entered evidence does not match the unresolved disbursement. Re-enter the same transfer evidence; do not start a replacement action.',
      })
      return
    }
    await postCommand(payload, identity.operationId, payloadDigest, identity.kind === 'REUSE_EXISTING')
  }

  const openConfirmation = () => {
    if (!data || !isPendingCase(data) || controlsLocked || unresolved) return
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
      setFormError('Re-enter the same transfer reference, disbursement date, and first repayment date before retrying this disbursement.')
      if (!candidate.externalTransferReference) referenceInputRef.current?.focus()
      else if (!candidate.disbursementValueDate) valueDateInputRef.current?.focus()
      else repaymentDateInputRef.current?.focus()
      return
    }
    const candidateDigest = await digestOperationPayload(candidate)
    if (candidateDigest !== unresolved.payloadDigest) {
      setOperation({
        status: 'BLOCKED',
        detail: 'The entered evidence does not match the unresolved disbursement. Re-enter the same transfer evidence; retry remains blocked until the details match.',
      })
      return
    }
    setFormError(undefined)
    await postCommand(candidate, unresolved.operationId, candidateDigest, true)
  }

  const handleReveal = async () => {
    if (!data || !isPendingCase(data) || controlsLocked || revealInFlight) return
    const revealGeneration = revealGenerationRef.current
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
      if (revealGeneration !== revealGenerationRef.current) return
      if (revealed.contractId !== authoritative.currentContract.contractId
          || revealed.contractVersion !== authoritative.currentContract.contractVersion) {
        throw new Error('Revealed destination does not match the exact current contract.')
      }
      setRevealedDestination(revealed)
      setRevealNeedsRevalidation(false)
    } catch (error) {
      if (revealGeneration !== revealGenerationRef.current) return
      clearRevealedDestination()
      const revealFailure = error instanceof Error ? error : new NetworkError()
      setRevealError(revealFailure)
      if (isUnknownOutcome(revealFailure)) setRevealNeedsRevalidation(true)
      if (revealFailure instanceof ApiError && revealFailure.errorCode === 'CONTRACT_VERSION_STALE') {
        await query.refetch().catch(() => undefined)
      }
    } finally {
      if (revealGeneration === revealGenerationRef.current) setRevealInFlight(false)
    }
  }

  const refreshOnly = async () => {
    try {
      await refetchAuthoritativeCase()
      if (confirmedRefreshFailed) {
        await invalidateRelatedReads()
        setConfirmedRefreshFailed(false)
        setTemporaryResult(undefined)
        setOperation({ status: 'RESOLVED', detail: 'The disbursement remains confirmed and the latest case information is now loaded.' })
      }
    } catch {
      // React Query retains the query error and last validated data for safe presentation.
    }
  }

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Disbursement workspace unavailable</h1><Alert variant="warning"><Clock3 /><AlertTitle>Disbursement workspace unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading disbursement workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading contract and activation information…</div></section>
  const accountingRejected = query.error instanceof ApiError && query.error.errorCode === 'ACCOUNTING_ROLE_REQUIRED'
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Disbursement and activation workspace</h1>{accountingRejected ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>Accounting Officer access is required for disbursement operations.</AlertDescription></Alert> : <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} />}</section>
  if (!data) return null
  const contract = data.currentContract
  const commandVisible = data.applicationStatus === 'DISBURSEMENT_PENDING' || Boolean(unresolved)
  const outcomeVisible = !commandVisible && (operation.status !== 'DRAFT' || Boolean(temporaryResult))

  return <ApplicationWorkspaceShell
    actor={state.status === 'authenticated' ? state.actor : undefined}
    context={{ source: 'feature', facts: {
      loanApplicationId: data.loanApplicationId, applicationNumber: data.applicationNumber,
      applicationStatus: data.applicationStatus, productCode: data.productCode,
      requestedAmount: data.requestedAmount, requestedTermMonths: data.requestedTermMonths,
      submittedAt: data.submittedAt, originationChannel: data.originationChannel,
    } }}
    updatedAt={query.dataUpdatedAt} refreshing={query.isFetching} stale={query.isStale}
    onRefresh={() => void refreshOnly()}
  >
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/disbursements">← Back to disbursement queue</Link>{canReadApplication ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}`}>Application case</Link> : null}</div>
    <Card><CardHeader><CardTitle>Disbursement and activation</CardTitle><p className="text-sm font-semibold">{disbursementStageLabel(data.workStage)}</p></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Contract">{contract.contractReference}</Fact><Fact label="Exact version">{contract.contractVersion}</Fact><Fact label="Contract status">{humanizeKnownValue(contract.status)}</Fact></dl></CardContent></Card>
    {!isAccounting ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>Accounting Officer access is required for disbursement operations.</AlertDescription></Alert> : null}
    {query.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Previously loaded details are shown. Refresh successfully before taking action.</AlertDescription></Alert> : null}
    {!safeEvidence ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Operational evidence unavailable</AlertTitle><AlertDescription>Application details are incomplete or inconsistent. Refresh before taking action.</AlertDescription></Alert> : null}
    {staleEvidence ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Exact contract version changed</AlertTitle><AlertDescription><p>The prior confirmation remains bound to its old version. Review the current contract before continuing.</p><Button className="mt-3" variant="outline" onClick={() => setStaleEvidence(false)}>I reviewed the current contract</Button></AlertDescription></Alert> : null}
    <AccountingCaseContextPanel loanApplicationId={loanApplicationId} context={data.accountingContext} contractVersion={contract.contractVersion} />
    <Card><CardHeader><CardTitle>Ready contract</CardTitle></CardHeader><CardContent className="space-y-6"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Approved principal">{formatVnd(contract.approvedPrincipal)}</Fact><Fact label="Approved term">{contract.approvedTermMonths} months</Fact><Fact label="Interest method">{humanizeKnownValue(contract.interestCalculationMethod)}</Fact><Fact label="Flat monthly rate">{contract.flatMonthlyInterestRate}</Fact><Fact label="Total interest">{formatVnd(contract.totalInterest)}</Fact><Fact label="Fee">{formatVnd(contract.feeAmount)}</Fact><Fact label="Total repayment">{formatVnd(contract.totalRepaymentAmount)}</Fact><Fact label="Repayment method">{humanizeKnownValue(contract.repaymentMethod)}</Fact><Fact label="Readiness confirmed">{formatTimestamp(contract.readinessConfirmedAt)}</Fact></dl><div><h3 className="font-semibold">Provisional contract repayment items</h3><div className="mt-3 overflow-x-auto rounded-md border"><table className="min-w-[42rem] w-full text-left text-sm"><thead className="bg-muted text-xs uppercase text-muted-foreground"><tr><th className="p-3">Item</th><th className="p-3">Principal</th><th className="p-3">Interest</th><th className="p-3">Fee</th><th className="p-3">Total</th></tr></thead><tbody>{contract.repaymentPreview.map((item) => <tr key={item.installmentNumber} className="border-t"><td className="p-3 font-semibold">{item.installmentNumber}</td><td className="p-3">{formatVnd(item.principalDue)}</td><td className="p-3">{formatVnd(item.interestDue)}</td><td className="p-3">{formatVnd(item.feeDue)}</td><td className="p-3 font-semibold">{formatVnd(item.totalDue)}</td></tr>)}</tbody></table></div></div></CardContent></Card>
    {data.applicationStatus === 'DISBURSEMENT_PENDING' ? <Card><CardHeader><CardTitle>Contract-bound destination</CardTitle><p className="text-sm text-muted-foreground">Reveal the full account number only to verify the transfer destination. Keep the Customer&apos;s bank details confidential.</p></CardHeader><CardContent className="space-y-5"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Bank">{contract.disbursementBankAccount.bankNameSnapshot} ({contract.disbursementBankAccount.bankCode})</Fact><Fact label="Account holder">{contract.disbursementBankAccount.accountHolderName}</Fact><Fact label="Masked account">{contract.disbursementBankAccount.maskedAccountNumber}</Fact><Fact label="Captured">{formatTimestamp(contract.disbursementBankAccount.capturedAt)}</Fact></dl>{revealedDestination ? <div className="rounded-md border-2 border-warning bg-warning/5 p-4" aria-live="polite"><p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Sensitive destination — temporary view</p><p className="mt-2 break-all font-mono text-lg font-semibold">{revealedDestination.accountNumber}</p><Button className="mt-4" variant="outline" onClick={clearRevealedDestination}><EyeOff />Hide destination</Button></div> : <Button variant="outline" disabled={controlsLocked || revealInFlight} onClick={() => void handleReveal()}><Eye />{revealInFlight ? 'Revealing…' : 'Reveal destination'}</Button>}{revealError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Reveal result unavailable</AlertTitle><AlertDescription>{revealNeedsRevalidation ? 'The reveal was not repeated automatically. Meridian will check the latest contract before you reveal again.' : 'The destination was not retained. Review the latest case information before trying again.'}</AlertDescription></Alert> : null}</CardContent></Card> : null}
    {commandVisible ? <Card><CardHeader><CardTitle>Record external transfer</CardTitle><p className="text-sm text-muted-foreground">Meridian records confirmation of an external transfer. Meridian does not initiate the transfer.</p></CardHeader><CardContent className="space-y-5">{unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Previous confirmation result unknown</AlertTitle><AlertDescription>Meridian could not confirm the previous disbursement. Do not record another transfer. Re-enter the same transfer evidence and retry this disbursement with unchanged details.</AlertDescription></Alert> : null}<div className="grid gap-4 lg:grid-cols-3"><label className="grid gap-2 text-sm font-semibold">External transfer reference<input ref={referenceInputRef} className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" autoComplete="off" maxLength={64} value={form.externalTransferReference} onChange={(event) => setForm((value) => ({ ...value, externalTransferReference: event.target.value }))} /></label><label className="grid gap-2 text-sm font-semibold">Disbursement value date<input ref={valueDateInputRef} type="date" className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.disbursementValueDate} onChange={(event) => setForm((value) => ({ ...value, disbursementValueDate: event.target.value }))} /></label><label className="grid gap-2 text-sm font-semibold">First repayment date<input ref={repaymentDateInputRef} type="date" className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.firstRepaymentDate} onChange={(event) => setForm((value) => ({ ...value, firstRepaymentDate: event.target.value }))} /></label></div>{formError ? <p role="alert" className="font-semibold text-danger">{formError}</p> : null}<Alert variant="information"><Clock3 /><AlertTitle>External transfer occurs outside Meridian</AlertTitle><AlertDescription>Check the contract and destination, complete the transfer outside Meridian, then record the transfer here. Meridian validates the transfer details before activating the loan account and final repayment schedule.</AlertDescription></Alert>{unresolved ? <Button disabled={controlsLocked} onClick={() => void retryExactOperation()}>Retry this disbursement</Button> : <Button id="confirm-disbursement-trigger" disabled={controlsLocked || !isPendingCase(data) || !isAccounting} onClick={openConfirmation}>Review disbursement confirmation</Button>}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} headingId="disbursement-command-result" headingLabel={`Disbursement result for ${data.applicationNumber}`} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{temporaryResult && confirmedRefreshFailed ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Disbursement recorded; refresh needed</AlertTitle><AlertDescription>Loan account {temporaryResult.loanAccountNumber} confirms the disbursement. The latest case information is still unavailable; use Refresh and do not record the transfer again.</AlertDescription></Alert> : null}</CardContent></Card> : null}
    {outcomeVisible ? <Card><CardHeader><CardTitle>Disbursement outcome</CardTitle></CardHeader><CardContent className="space-y-4"><OperationStatusPanel status={operation.status} headingId="disbursement-command-result" headingLabel={`Disbursement result for ${data.applicationNumber}`} />{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{temporaryResult && confirmedRefreshFailed ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Disbursement recorded; refresh needed</AlertTitle><AlertDescription>Loan account {temporaryResult.loanAccountNumber} confirms the disbursement. The latest case information is still unavailable; use Refresh and do not record the transfer again.</AlertDescription></Alert> : null}</CardContent></Card> : null}
    {data.activation ? <Card><CardHeader><CardTitle>Activation result</CardTitle><p className="text-sm text-muted-foreground">Review the activated loan account and final schedule. Open the loan account for payments and servicing.</p>{canReadApplication ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}/loan-account`}>Open loan account servicing</Link></Button> : null}</CardHeader><CardContent className="space-y-6"><Alert variant="success"><CheckCircle2 /><AlertTitle>Application disbursed and account activated</AlertTitle><AlertDescription>The latest case information confirms the external transfer, loan account, and final repayment schedule.</AlertDescription></Alert><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Loan account number">{data.activation.loanAccountNumber}</Fact><Fact label="Loan account status">{humanizeKnownValue(data.activation.loanAccountStatus)}</Fact><Fact label="Activated">{formatTimestamp(data.activation.activatedAt)}</Fact><Fact label="Disbursed amount">{formatVnd(data.activation.disbursedAmount)}</Fact><Fact label="Value date">{formatDateOnly(data.activation.disbursementValueDate)}</Fact><Fact label="First repayment">{formatDateOnly(data.activation.firstRepaymentDate)}</Fact><Fact label="Schedule type">{humanizeKnownValue(data.activation.scheduleType)}</Fact><Fact label="Schedule version">{data.activation.scheduleVersion}</Fact></dl><div><h3 className="font-semibold">Final repayment schedule</h3><div className="mt-3 overflow-x-auto rounded-md border"><table className="min-w-[48rem] w-full text-left text-sm"><thead className="bg-muted text-xs uppercase text-muted-foreground"><tr><th className="p-3">Installment</th><th className="p-3">Due date</th><th className="p-3">Principal</th><th className="p-3">Interest</th><th className="p-3">Fee</th><th className="p-3">Total</th></tr></thead><tbody>{data.activation.scheduleItems.map((item) => <tr key={item.installmentNumber} className="border-t"><td className="p-3 font-semibold">{item.installmentNumber}</td><td className="p-3">{formatDateOnly(item.dueDate)}</td><td className="p-3">{formatVnd(item.principalDue)}</td><td className="p-3">{formatVnd(item.interestDue)}</td><td className="p-3">{formatVnd(item.feeDue)}</td><td className="p-3 font-semibold">{formatVnd(item.totalDue)}</td></tr>)}</tbody></table></div></div></CardContent></Card> : null}
    {confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="disbursement-confirm-title"><div className="max-h-[90vh] w-full max-w-xl space-y-4 overflow-y-auto rounded-lg bg-card p-6 shadow-xl"><h2 id="disbursement-confirm-title" className="text-xl font-semibold">Confirm disbursement record</h2><p className="text-sm text-muted-foreground">This records an external transfer against exact contract version {confirmation.expectedContractVersion}. Meridian does not initiate the transfer.</p><dl className="grid gap-3 sm:grid-cols-2"><Fact label="Application">{data.applicationNumber}</Fact><Fact label="Exact contract version">{confirmation.expectedContractVersion}</Fact><Fact label="Approved principal">{formatVnd(contract.approvedPrincipal)}</Fact><Fact label="Disbursement value date">{formatDateOnly(confirmation.disbursementValueDate)}</Fact><Fact label="First repayment date">{formatDateOnly(confirmation.firstRepaymentDate)}</Fact></dl><p className="text-sm text-muted-foreground">Confirm the completed transfer to activate the loan account and final schedule. Keep the destination and transfer reference confidential; they are not shown in this summary.</p><div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={() => { setConfirmation(undefined); setTimeout(() => document.getElementById('confirm-disbursement-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus disabled={controlsLocked} onClick={() => { const submitted = confirmation; requestResultFocus(); setConfirmation(undefined); void submitPayload(submitted) }}>Confirm disbursement</Button></div></div></div> : null}
  </ApplicationWorkspaceShell>
}
