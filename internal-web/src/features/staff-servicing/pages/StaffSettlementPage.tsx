import { operatorErrorMessage } from '@/lib/api/operator-error-message'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { useOperationResultFocus } from '@/components/operations/useOperationResultFocus'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
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
import type { ApprovedSettlementResult, SettlementSemanticPayload } from '../api/contracts'
import { approvedSettlementEvidenceQuery, loanAccountQuery, repaymentHistoryQuery, staffServicingKeys } from '../api/queries'
import { approveSettlement } from '../api/staff-servicing-api'
import { LoanAccountEvidence } from '../components/LoanAccountEvidence'
import { RepaymentHistoryPanel } from '../components/RepaymentHistoryPanel'
import { accountStatusLabel, hasCoherentLoanAccount, serviceableAccountStatuses } from '../model/presentation'

const operationType: UnresolvedOperationType = 'ADMINISTRATIVE_FULL_BALANCE_SETTLEMENT'
type OperationState = { status: OperationStatus; detail?: string; error?: Error }
type FormState = { externalPaymentReference: string; paymentValueDate: string }

function canonicalPayload(
  loanApplicationId: string,
  form: FormState,
  amount: number,
): SettlementSemanticPayload | undefined {
  const reference = form.externalPaymentReference.trim().toUpperCase()
  if (!/^[A-Z0-9][A-Z0-9._:/-]{0,63}$/.test(reference)
    || !form.paymentValueDate
    || !Number.isSafeInteger(amount)
    || amount <= 0) return undefined
  return {
    loanApplicationId,
    externalPaymentReference: reference,
    expectedSettlementAmount: amount,
    paymentValueDate: form.paymentValueDate,
  }
}

function isUnknownOutcome(error: Error) {
  return !(error instanceof ApiError) || error.status >= 500
}

export function StaffSettlementPage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const canSettle = state.status === 'authenticated'
    && hasPermission(state.actor, 'loan:settlement:approve')
    && hasRole(state.actor, 'APPROVER')
  const [historyPage, setHistoryPage] = useState(0)
  const [form, setForm] = useState<FormState>({ externalPaymentReference: '', paymentValueDate: '' })
  const [formError, setFormError] = useState<string>()
  const [confirmation, setConfirmation] = useState<SettlementSemanticPayload>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const requestResultFocus = useOperationResultFocus(operation.status, 'settlement-command-result')
  const [inMemoryUnknownPayload, setInMemoryUnknownPayload] = useState<SettlementSemanticPayload>()
  const [result, setResult] = useState<ApprovedSettlementResult>()
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const referenceRef = useRef<HTMLInputElement>(null)
  const dateRef = useRef<HTMLInputElement>(null)
  const accountQuery = useQuery(loanAccountQuery(manager, loanApplicationId, canRead && validId))
  const historyQuery = useQuery(repaymentHistoryQuery(manager, loanApplicationId, historyPage, 20, canRead && validId))
  const account = accountQuery.data
  const unresolved = validId ? findUnresolvedOperation(operationType, loanApplicationId) : undefined
  const terminalRecovery = account?.status === 'SETTLED' || account?.status === 'CLOSED'
  const evidenceQuery = useQuery(approvedSettlementEvidenceQuery(
    manager,
    loanApplicationId,
    Boolean(canSettle && unresolved && terminalRecovery && !inMemoryUnknownPayload),
  ))
  const safeAccount = Boolean(account && hasCoherentLoanAccount(account))
  const readsLocked = !account || !safeAccount || accountQuery.isFetching || accountQuery.isRefetchError
    || historyQuery.isFetching || historyQuery.isRefetchError
  const newSettlementLocked = readsLocked || !account
    || !serviceableAccountStatuses.has(account.status)
    || account.servicing.totalOutstanding <= 0
    || Boolean(unresolved)
    || operation.status === 'IN_FLIGHT'
    || operation.status === 'RECONCILING'
    || confirmedRefreshFailed

  const refetchReads = async () => {
    const [accountResult, historyResult] = await Promise.all([
      accountQuery.refetch({ throwOnError: true }),
      historyQuery.refetch({ throwOnError: true }),
    ])
    if (accountResult.isError || historyResult.isError) throw new NetworkError()
  }

  const invalidateAffected = async () => {
    await queryClient.invalidateQueries({ queryKey: staffServicingKeys.all, refetchType: 'none' })
  }

  const refreshAfterSuccess = async (confirmed: ApprovedSettlementResult) => {
    setResult(confirmed)
    setOperation({ status: 'RECONCILING' })
    await invalidateAffected().catch(() => undefined)
    try {
      await refetchReads()
      setConfirmedRefreshFailed(false)
      setOperation({
        status: 'RESOLVED',
        detail: confirmed.idempotentReplay
          ? 'The retry confirmed the previously recorded settlement. The account and repayment details show the latest position.'
          : 'Settlement was confirmed and the latest account, history, settlement, and closure information is loaded.',
      })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({ status: 'BLOCKED', error: error instanceof Error ? error : new NetworkError(), detail: 'Settlement is confirmed, but the latest account information is unavailable. Use Refresh, and do not record the settlement again.' })
    }
  }

  const postCommand = async (payload: SettlementSemanticPayload, requestId: string, digest: string, retrying: boolean) => {
    setOperation({ status: 'IN_FLIGHT' })
    try {
      const confirmed = await approveSettlement(manager, loanApplicationId, {
        requestId,
        externalPaymentReference: payload.externalPaymentReference,
        expectedSettlementAmount: payload.expectedSettlementAmount,
        paymentValueDate: payload.paymentValueDate,
      })
      removeUnresolvedOperation(operationType, loanApplicationId)
      setInMemoryUnknownPayload(undefined)
      setForm({ externalPaymentReference: '', paymentValueDate: '' })
      await refreshAfterSuccess(confirmed)
    } catch (error) {
      const commandError = error instanceof Error ? error : new NetworkError()
      if (isUnknownOutcome(commandError)) {
        saveUnresolvedOperation({ type: operationType, resource: loanApplicationId, operationId: requestId, payloadDigest: digest, unresolvedAt: new Date().toISOString() })
        setInMemoryUnknownPayload(payload)
        await Promise.allSettled([refetchReads(), invalidateAffected()])
        setOperation({ status: 'RESULT_UNKNOWN', error: commandError, detail: retrying ? 'The settlement is still unconfirmed. Retry it with the same payment details.' : 'The settlement result is not confirmed. Meridian did not submit it again automatically; retry this settlement with the same payment details.' })
        return
      }
      if (!(commandError instanceof ApiError)) return
      const keepIdentity = commandError.errorCode === 'IDEMPOTENCY_KEY_REUSED'
      if (!keepIdentity) {
        removeUnresolvedOperation(operationType, loanApplicationId)
        setInMemoryUnknownPayload(undefined)
      }
      if (['SETTLEMENT_AMOUNT_INVALID', 'SETTLEMENT_NOT_ALLOWED', 'SYSTEM_STATE_CONFLICT'].includes(commandError.errorCode)) {
        await Promise.allSettled([refetchReads(), invalidateAffected()])
      }
      setOperation({ status: 'BLOCKED', error: commandError, detail: keepIdentity ? 'The saved recovery action conflicts with recorded settlement evidence. Do not start a replacement action; operator resolution is required.' : operatorErrorMessage(commandError, 'The settlement was rejected. Entered evidence remains unchanged; review the reason before trying again.') })
    }
  }

  const submit = async (payload: SettlementSemanticPayload) => {
    const digest = await digestOperationPayload(payload)
    const identity = decideOperationIdentity(operationType, loanApplicationId, digest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation({ status: 'BLOCKED', detail: 'The entered evidence does not match the unresolved settlement. Re-enter the same payment evidence; do not start a replacement action.' })
      return
    }
    await postCommand(payload, identity.operationId, digest, identity.kind === 'REUSE_EXISTING')
  }

  const candidateAmount = inMemoryUnknownPayload?.expectedSettlementAmount
    ?? (unresolved && terminalRecovery
      ? evidenceQuery.data?.settlementAmount
      : account?.servicing.totalOutstanding)
  const candidateDate = inMemoryUnknownPayload?.paymentValueDate
    ?? (unresolved && terminalRecovery
      ? evidenceQuery.data?.paymentValueDate ?? form.paymentValueDate
      : form.paymentValueDate)

  const buildCandidate = () => {
    if (!candidateAmount) return undefined
    const candidate = canonicalPayload(loanApplicationId, { ...form, paymentValueDate: candidateDate }, candidateAmount)
    if (!candidate) {
      setFormError(!form.externalPaymentReference.trim() ? 'Enter the external payment reference.' : 'Enter valid payment evidence and a payment value date.')
      if (!form.externalPaymentReference.trim()) referenceRef.current?.focus()
      else dateRef.current?.focus()
      return undefined
    }
    setFormError(undefined)
    return candidate
  }

  const retryExact = async () => {
    if (!unresolved || readsLocked) return
    const candidate = inMemoryUnknownPayload ?? buildCandidate()
    if (!candidate) return
    const digest = await digestOperationPayload(candidate)
    if (digest !== unresolved.payloadDigest) {
      setOperation({ status: 'BLOCKED', detail: 'The re-entered evidence does not match the unresolved settlement. Retry remains blocked until the details match.' })
      return
    }
    await postCommand(candidate, unresolved.operationId, digest, true)
  }

  const refreshOnly = async () => {
    try {
      await refetchReads()
      if (unresolved && terminalRecovery && !inMemoryUnknownPayload) await evidenceQuery.refetch()
      if (confirmedRefreshFailed) {
        await invalidateAffected()
        setConfirmedRefreshFailed(false)
        setOperation({ status: 'RESOLVED', detail: 'The settlement remains confirmed and the latest account information is now loaded.' })
      }
    } catch { /* cached evidence remains inspection-only */ }
  }

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Settlement workspace unavailable</h1><Alert variant="warning"><AlertTriangle /><AlertTitle>Invalid application identifier</AlertTitle></Alert></section>
  if (!canRead) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Settlement workspace unavailable</h1><Alert variant="warning"><ShieldAlert /><AlertTitle>Loan account access required</AlertTitle><AlertDescription>You need access to the loan account and repayment history before recording a settlement.</AlertDescription></Alert></section>
  if (accountQuery.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading settlement workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading servicing information…</div></section>
  if (accountQuery.isError && !account) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Administrative Full-Balance Settlement</h1><QueryErrorPanel error={accountQuery.error} resource="case" onRetry={() => void accountQuery.refetch()} /></section>
  if (!account) return null

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/settlements">← Back to settlement queue</Link><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/loan-account`}>Loan account workspace</Link></div>
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">APPROVER OPERATION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Administrative Full-Balance Settlement</h1><p className="mt-2 text-muted-foreground">Account {account.accountNumber} · full outstanding {formatVnd(account.servicing.totalOutstanding)}</p></div><Button variant="outline" onClick={() => void refreshOnly()} disabled={accountQuery.isFetching || historyQuery.isFetching}><RefreshCw className={accountQuery.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div></header>
    <Alert variant="information"><AlertTitle>Full payment, not debt adjustment</AlertTitle><AlertDescription>Administrative Full-Balance Settlement records an actual payment for exactly the full outstanding balance. It is not a discount, concession, waiver, forgiveness, or write-off.</AlertDescription></Alert>
    {!canSettle ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Approver access required</AlertTitle><AlertDescription>Approver access is required to record a settlement.</AlertDescription></Alert> : null}
    {accountQuery.isError || historyQuery.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Previously loaded details are shown. Refresh successfully before recording a settlement.</AlertDescription></Alert> : null}
    {!safeAccount ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Loan account details need review</AlertTitle><AlertDescription>Account details are incomplete or inconsistent. Refresh before taking action.</AlertDescription></Alert> : null}
    {unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Previous settlement result unknown</AlertTitle><AlertDescription>Current account information alone does not confirm this settlement. Do not record another settlement. Re-enter the same protected reference and retry this settlement with the displayed amount and date.</AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Settlement evidence</CardTitle><p className="text-sm text-muted-foreground">The result shows the payment allocation, payoff, and any released lending exposure.</p></CardHeader><CardContent className="space-y-5"><div className="grid gap-4 lg:grid-cols-3"><div><p className="text-sm font-semibold">Settlement amount</p><p className="mt-2 flex min-h-11 items-center rounded-md border bg-muted px-3 font-semibold">{candidateAmount ? formatVnd(candidateAmount) : 'Settlement amount unavailable'}</p></div><label className="grid gap-2 text-sm font-semibold">Payment value date<input ref={dateRef} type="date" disabled={Boolean(inMemoryUnknownPayload) || Boolean(unresolved && terminalRecovery && evidenceQuery.data)} className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={candidateDate} onChange={(event) => setForm((value) => ({ ...value, paymentValueDate: event.target.value }))} /></label><label className="grid gap-2 text-sm font-semibold">External payment reference<input ref={referenceRef} autoComplete="off" maxLength={64} disabled={Boolean(inMemoryUnknownPayload)} className="h-11 min-w-0 rounded-md border bg-card px-3 font-normal" value={form.externalPaymentReference} onChange={(event) => setForm((value) => ({ ...value, externalPaymentReference: event.target.value }))} /></label></div>{formError ? <p role="alert" className="font-semibold text-danger">{formError}</p> : null}{unresolved ? <Button disabled={!canSettle || readsLocked || evidenceQuery.isFetching || (!inMemoryUnknownPayload && !evidenceQuery.data)} onClick={() => void retryExact()}>Retry this settlement</Button> : <Button id="confirm-settlement-trigger" disabled={!canSettle || newSettlementLocked} onClick={() => { const candidate = buildCandidate(); if (candidate) setConfirmation(candidate) }}>Review full-balance settlement</Button>}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} headingId="settlement-command-result" headingLabel={`Settlement result for account ${account.accountNumber}`} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{confirmedRefreshFailed && result ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Settlement recorded; refresh needed</AlertTitle><AlertDescription>{formatVnd(result.settlementAmount)} was confirmed. The latest account information is still unavailable; use Refresh and do not record the settlement again.</AlertDescription></Alert> : null}</CardContent></Card>
    {result ? <Card><CardHeader><CardTitle>{result.idempotentReplay ? 'Previously recorded settlement confirmed' : 'Settlement confirmed'}</CardTitle></CardHeader><CardContent><dl className="grid gap-3 sm:grid-cols-3"><div><dt className="text-xs font-semibold uppercase text-muted-foreground">Amount</dt><dd className="font-semibold">{formatVnd(result.settlementAmount)}</dd></div><div><dt className="text-xs font-semibold uppercase text-muted-foreground">Value date</dt><dd>{formatDateOnly(result.paymentValueDate)}</dd></div><div><dt className="text-xs font-semibold uppercase text-muted-foreground">Approved</dt><dd>{formatTimestamp(result.approvedAt)}</dd></div></dl></CardContent></Card> : null}
    <LoanAccountEvidence account={account} />
    <RepaymentHistoryPanel data={historyQuery.data} pending={historyQuery.isPending} error={historyQuery.error} fetching={historyQuery.isFetching} onRetry={() => void historyQuery.refetch()} onPage={setHistoryPage} />
    {confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="settlement-confirm-title"><div className="max-h-[90vh] w-full max-w-xl space-y-4 overflow-y-auto rounded-lg bg-card p-6 shadow-xl"><h2 id="settlement-confirm-title" className="text-xl font-semibold">Confirm full-balance settlement</h2><p>Record an actual payment of <strong>{formatVnd(confirmation.expectedSettlementAmount)}</strong> for account <strong>{account.accountNumber}</strong> with status {accountStatusLabel(account.status)}.</p><p className="text-sm text-muted-foreground">This is not a discount, concession, waiver, forgiveness, or write-off. The protected reference is omitted from this summary.</p><div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={() => { setConfirmation(undefined); setTimeout(() => document.getElementById('confirm-settlement-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus disabled={newSettlementLocked} onClick={() => { const payload = confirmation; requestResultFocus(); setConfirmation(undefined); void submit(payload) }}>Record settlement</Button></div></div></div> : null}
  </section>
}
