import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-react'
import { useRef, useState } from 'react'
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
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { ApiError, NetworkError } from '@/lib/api'
import { formatDateOnly, formatVnd } from '@/lib/format/presentation'
import {
  decideOperationIdentity,
  digestOperationPayload,
  findUnresolvedOperation,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
  type UnresolvedOperationType,
} from '@/lib/operation/unresolved-operation'
import type { RecordRepaymentResult, RepaymentSemanticPayload } from '../api/contracts'
import { loanAccountQuery, repaymentHistoryQuery, staffServicingKeys } from '../api/queries'
import { recordRepayment } from '../api/staff-servicing-api'
import { LoanAccountEvidence } from '../components/LoanAccountEvidence'
import { RepaymentHistoryPanel } from '../components/RepaymentHistoryPanel'
import { RepaymentOutcomePanel } from '../components/RepaymentOutcomePanel'
import { hasCoherentLoanAccount, serviceableAccountStatuses } from '../model/presentation'

const operationType: UnresolvedOperationType = 'REPAYMENT_RECORDING'
type OperationState = { status: OperationStatus; detail?: string; error?: Error }
type FormState = { externalPaymentReference: string; amount: string; paymentValueDate: string }
const emptyForm: FormState = { externalPaymentReference: '', amount: '', paymentValueDate: '' }

function canonicalPayload(loanApplicationId: string, form: FormState): RepaymentSemanticPayload | undefined {
  const amount = Number(form.amount)
  if (!form.externalPaymentReference.trim()
    || !/^[1-9]\d*$/.test(form.amount)
    || !Number.isSafeInteger(amount)
    || !form.paymentValueDate) return undefined
  return {
    loanApplicationId,
    externalPaymentReference: form.externalPaymentReference.trim().toUpperCase(),
    amount,
    paymentValueDate: form.paymentValueDate,
  }
}

function isUnknownOutcome(error: Error) {
  return !(error instanceof ApiError) || error.status >= 500
}

export function StaffRepaymentEntryPage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const canRecord = state.status === 'authenticated' && hasPermission(state.actor, 'repayment:update')
  const [historyPage, setHistoryPage] = useState(0)
  const accountQuery = useQuery(loanAccountQuery(manager, loanApplicationId, canRead && validId))
  const historyQuery = useQuery(repaymentHistoryQuery(manager, loanApplicationId, historyPage, 20, canRead && validId))
  const [form, setForm] = useState<FormState>(emptyForm)
  const [formError, setFormError] = useState<string>()
  const [confirmation, setConfirmation] = useState<RepaymentSemanticPayload>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const [inMemoryUnknownPayload, setInMemoryUnknownPayload] = useState<RepaymentSemanticPayload>()
  const [result, setResult] = useState<RecordRepaymentResult>()
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const referenceRef = useRef<HTMLInputElement>(null)
  const amountRef = useRef<HTMLInputElement>(null)
  const dateRef = useRef<HTMLInputElement>(null)
  const account = accountQuery.data
  const unresolved = validId ? findUnresolvedOperation(operationType, loanApplicationId) : undefined
  const safeAccount = Boolean(account && hasCoherentLoanAccount(account))
  const controlsLocked = !account
    || !safeAccount
    || !serviceableAccountStatuses.has(account.status)
    || accountQuery.isFetching
    || accountQuery.isRefetchError
    || operation.status === 'IN_FLIGHT'
    || operation.status === 'RECONCILING'
    || confirmedRefreshFailed

  const refetchAuthoritativeReads = async () => {
    const [accountResult, historyResult] = await Promise.all([
      accountQuery.refetch({ throwOnError: true }),
      historyQuery.refetch({ throwOnError: true }),
    ])
    if (accountResult.isError || historyResult.isError) throw new NetworkError()
  }

  const invalidateQueue = async () => {
    await queryClient.invalidateQueries({ queryKey: staffServicingKeys.all, refetchType: 'none' })
  }

  const refreshAfterSuccess = async (confirmed: RecordRepaymentResult) => {
    setResult(confirmed)
    setOperation({ status: 'RECONCILING' })
    await invalidateQueue().catch(() => undefined)
    try {
      await refetchAuthoritativeReads()
      setConfirmedRefreshFailed(false)
      setOperation({
        status: 'RESOLVED',
        detail: confirmed.idempotentReplay
          ? 'Recovered the previously recorded repayment through exact same-request replay. Current account state was refreshed separately.'
          : 'Repayment was recorded and current account, history, and servicing work were reconciled.',
      })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({
        status: 'BLOCKED',
        error: error instanceof Error ? error : new NetworkError(),
        detail: 'Repayment confirmed; refreshed state unavailable. Only authoritative GET reconciliation may be retried.',
      })
    }
  }

  const reconcileUnknown = async (commandError: Error) => {
    await Promise.allSettled([refetchAuthoritativeReads(), invalidateQueue()])
    setOperation({
      status: 'RESULT_UNKNOWN',
      error: commandError,
      detail: 'Current account or history changes cannot prove this request identity. No POST was retried automatically; exact same-request replay is required.',
    })
  }

  const postCommand = async (
    payload: RepaymentSemanticPayload,
    requestId: string,
    payloadDigest: string,
    retrying: boolean,
  ) => {
    setOperation({ status: 'IN_FLIGHT' })
    try {
      const confirmed = await recordRepayment(manager, loanApplicationId, {
        requestId,
        externalPaymentReference: payload.externalPaymentReference,
        amount: payload.amount,
        paymentValueDate: payload.paymentValueDate,
      })
      removeUnresolvedOperation(operationType, loanApplicationId)
      setInMemoryUnknownPayload(undefined)
      setForm(emptyForm)
      await refreshAfterSuccess(confirmed)
    } catch (error) {
      const commandError = error instanceof Error ? error : new NetworkError()
      if (isUnknownOutcome(commandError)) {
        saveUnresolvedOperation({
          type: operationType,
          resource: loanApplicationId,
          operationId: requestId,
          payloadDigest,
          unresolvedAt: new Date().toISOString(),
        })
        setInMemoryUnknownPayload(payload)
        setOperation({
          status: 'RESULT_UNKNOWN',
          error: commandError,
          detail: retrying
            ? 'The exact retry outcome is also unknown. The same request UUID and in-memory command remain retained.'
            : 'The result is unknown. Only the request UUID and SHA-256 payload digest were persisted.',
        })
        await reconcileUnknown(commandError)
        return
      }
      if (!(commandError instanceof ApiError)) return

      const idempotencyConflict = commandError.errorCode === 'IDEMPOTENCY_KEY_REUSED'
      if (!idempotencyConflict) {
        removeUnresolvedOperation(operationType, loanApplicationId)
        setInMemoryUnknownPayload(undefined)
      }
      if (commandError.errorCode === 'REPAYMENT_EXCEEDS_OUTSTANDING'
        || commandError.errorCode === 'REPAYMENT_NOT_ALLOWED'
        || commandError.errorCode === 'SYSTEM_STATE_CONFLICT') {
        await accountQuery.refetch().catch(() => undefined)
      }
      setOperation({
        status: 'BLOCKED',
        error: commandError,
        detail: idempotencyConflict
          ? 'The retained request identity conflicts with different backend evidence. No replacement UUID was generated; operator resolution is required.'
          : 'The backend rejected the command with a definite response. Entered financial evidence was not changed automatically.',
      })
    }
  }

  const submitPayload = async (payload: RepaymentSemanticPayload) => {
    const payloadDigest = await digestOperationPayload(payload)
    const identity = decideOperationIdentity(operationType, loanApplicationId, payloadDigest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation({ status: 'BLOCKED', detail: 'The entered evidence does not match the unresolved operation. No protected field difference is disclosed and no new UUID was created.' })
      return
    }
    await postCommand(payload, identity.operationId, payloadDigest, identity.kind === 'REUSE_EXISTING')
  }

  const validateForm = () => {
    if (!form.externalPaymentReference.trim()) {
      setFormError('Enter the external payment reference.')
      referenceRef.current?.focus()
      return undefined
    }
    if (!/^[1-9]\d*$/.test(form.amount) || !Number.isSafeInteger(Number(form.amount))) {
      setFormError('Enter a positive whole-VND amount.')
      amountRef.current?.focus()
      return undefined
    }
    if (!form.paymentValueDate) {
      setFormError('Enter the payment value date.')
      dateRef.current?.focus()
      return undefined
    }
    setFormError(undefined)
    return canonicalPayload(loanApplicationId, form)
  }

  const openConfirmation = () => {
    if (controlsLocked || unresolved || !canRecord) return
    const payload = validateForm()
    if (payload) setConfirmation(payload)
  }

  const retryExactOperation = async () => {
    if (!unresolved || controlsLocked) return
    const candidate = inMemoryUnknownPayload ?? validateForm()
    if (!candidate) return
    const digest = await digestOperationPayload(candidate)
    if (digest !== unresolved.payloadDigest) {
      setOperation({ status: 'BLOCKED', detail: 'The entered evidence does not match the unresolved operation. No protected field difference is disclosed.' })
      return
    }
    await postCommand(candidate, unresolved.operationId, digest, true)
  }

  const refreshOnly = async () => {
    try {
      await refetchAuthoritativeReads()
      if (confirmedRefreshFailed) {
        await invalidateQueue()
        setConfirmedRefreshFailed(false)
        setOperation({ status: 'RESOLVED', detail: 'The confirmed repayment is now reconciled with authoritative current state.' })
      }
    } catch {
      // TanStack Query retains safe cached evidence and exposes the refetch error.
    }
  }

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Repayment workspace unavailable</h1><Alert variant="warning"><AlertTriangle /><AlertTitle>Invalid application identifier</AlertTitle><AlertDescription>This route does not contain a valid loanApplicationId.</AlertDescription></Alert></section>
  if (!canRead) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Repayment workspace unavailable</h1><Alert variant="warning"><ShieldAlert /><AlertTitle>LoanAccount read authority required</AlertTitle><AlertDescription>Your session can reach the repayment action route but cannot read authoritative account or history evidence. No unauthorized queries were sent.</AlertDescription></Alert></section>
  if (accountQuery.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading repayment workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative servicing evidence…</div></section>
  if (accountQuery.isError && !account) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Record repayment</h1><QueryErrorPanel error={accountQuery.error} resource="case" onRetry={() => void accountQuery.refetch()} /></section>
  if (!account) return null

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/loan-account`}>← LoanAccount workspace</Link><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/servicing">Servicing queue</Link></div>
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">ORDINARY REPAYMENT</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Record repayment</h1><p className="mt-2 text-muted-foreground">Account {account.accountNumber} · current outstanding {formatVnd(account.servicing.totalOutstanding)}</p></div><Button variant="outline" onClick={() => void refreshOnly()} disabled={accountQuery.isFetching || historyQuery.isFetching}><RefreshCw className={accountQuery.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div></header>
    {accountQuery.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest account refresh unavailable</AlertTitle><AlertDescription>Cached evidence remains visible, but repayment confirmation and exact retry are disabled until Refresh succeeds.</AlertDescription></Alert> : null}
    {!safeAccount ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Servicing evidence unavailable</AlertTitle><AlertDescription>Unknown or contradictory evidence cannot authorize a financial command.</AlertDescription></Alert> : null}
    {!serviceableAccountStatuses.has(account.status) ? <Alert variant="information"><AlertTitle>Ordinary repayment unavailable</AlertTitle><AlertDescription>The backend reports {account.status}. CP8 does not expose settlement or administrative closure actions.</AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Payment evidence</CardTitle><p className="text-sm text-muted-foreground">The backend allocates oldest installment first, then fee → interest → principal. No allocation or payoff preview is calculated here.</p></CardHeader><CardContent className="space-y-5">{unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Previous repayment result unknown</AlertTitle><AlertDescription>Only the original request UUID and SHA-256 payload digest survived reload. Re-enter the exact protected command evidence to retry; current account or history changes cannot prove success.</AlertDescription></Alert> : null}<div className="grid gap-4 lg:grid-cols-3"><label className="grid gap-2 text-sm font-semibold">External payment reference<input ref={referenceRef} autoComplete="off" maxLength={100} className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.externalPaymentReference} onChange={(event) => setForm((value) => ({ ...value, externalPaymentReference: event.target.value }))} /></label><label className="grid gap-2 text-sm font-semibold">Amount (whole VND)<input ref={amountRef} inputMode="numeric" autoComplete="off" className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.amount} onChange={(event) => setForm((value) => ({ ...value, amount: event.target.value }))} /></label><label className="grid gap-2 text-sm font-semibold">Payment value date<input ref={dateRef} type="date" className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.paymentValueDate} onChange={(event) => setForm((value) => ({ ...value, paymentValueDate: event.target.value }))} /></label></div>{formError ? <p role="alert" className="font-semibold text-danger">{formError}</p> : null}{unresolved ? <Button disabled={controlsLocked || !canRecord} onClick={() => void retryExactOperation()}>Retry exact operation</Button> : <Button id="confirm-repayment-trigger" disabled={controlsLocked || !canRecord} onClick={openConfirmation}>Review repayment</Button>}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{confirmedRefreshFailed && result ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Repayment command confirmed</AlertTitle><AlertDescription>{formatVnd(result.receivedAmount)} was confirmed for {formatDateOnly(result.paymentValueDate)}. Current account/history refresh remains unavailable; retry GET only.</AlertDescription></Alert> : null}</CardContent></Card>
    {result ? <RepaymentOutcomePanel result={result} /> : null}
    <LoanAccountEvidence account={account} />
    <RepaymentHistoryPanel data={historyQuery.data} pending={historyQuery.isPending} error={historyQuery.error} fetching={historyQuery.isFetching} onRetry={() => void historyQuery.refetch()} onPage={setHistoryPage} />
    {confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="repayment-confirm-title"><div className="max-h-[90vh] w-full max-w-xl space-y-4 overflow-y-auto rounded-lg bg-card p-6 shadow-xl"><h2 id="repayment-confirm-title" className="text-xl font-semibold">Confirm repayment record</h2><p className="text-sm text-muted-foreground">This records protected payment evidence against the authoritative LoanAccount. Allocation and resulting balances are backend-derived.</p><dl className="grid gap-3 sm:grid-cols-2"><div><dt className="text-xs font-semibold uppercase text-muted-foreground">Account</dt><dd className="font-semibold">{account.accountNumber}</dd></div><div><dt className="text-xs font-semibold uppercase text-muted-foreground">Amount</dt><dd className="font-semibold">{formatVnd(confirmation.amount)}</dd></div><div><dt className="text-xs font-semibold uppercase text-muted-foreground">Payment value date</dt><dd className="font-semibold">{formatDateOnly(confirmation.paymentValueDate)}</dd></div><div><dt className="text-xs font-semibold uppercase text-muted-foreground">Current outstanding</dt><dd className="font-semibold">{formatVnd(account.servicing.totalOutstanding)}</dd></div></dl><p className="text-sm text-muted-foreground">The external payment reference is intentionally omitted from this summary and is never persisted in browser storage.</p><div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={() => { setConfirmation(undefined); setTimeout(() => document.getElementById('confirm-repayment-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus disabled={controlsLocked} onClick={() => { const submitted = confirmation; setConfirmation(undefined); void submitPayload(submitted) }}>Confirm repayment</Button></div></div></div> : null}
  </section>
}
