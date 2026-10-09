import { ConfirmationDialog, ConfirmationDialogCancel, ConfirmationDialogTitle, ConfirmationDialogDescription, ConfirmationDialogFooter } from '@/components/ui/confirmation-dialog'
import { operatorErrorMessage } from '@/lib/api/operator-error-message'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
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
import { formatTimestamp } from '@/lib/format/presentation'
import {
  decideOperationIdentity,
  digestOperationPayload,
  findUnresolvedOperation,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
  type UnresolvedOperationType,
} from '@/lib/operation/unresolved-operation'
import type { ClosedLoanAccountResult } from '../api/contracts'
import { loanAccountQuery, staffServicingKeys, staffServicingProvenanceQuery } from '../api/queries'
import { closeLoanAccount } from '../api/staff-servicing-api'
import { LoanAccountEvidence } from '../components/LoanAccountEvidence'
import { RepaymentHistoryPanel } from '../components/RepaymentHistoryPanel'
import { StaffServicingProvenancePanel } from '../components/StaffServicingProvenancePanel'
import { accountStatusLabel, hasCoherentLoanAccount } from '../model/presentation'

const operationType: UnresolvedOperationType = 'LOAN_ACCOUNT_ADMINISTRATIVE_CLOSURE'
type OperationState = { status: OperationStatus; detail?: string; error?: Error }
const semanticPayload = (loanApplicationId: string) => ({
  loanApplicationId,
  operation: 'ADMINISTRATIVE_CLOSURE',
})

function isUnknownOutcome(error: Error) {
  return !(error instanceof ApiError) || error.status >= 500
}

export function StaffClosurePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const canClose = state.status === 'authenticated'
    && hasPermission(state.actor, 'loan:account:close')
    && hasRole(state.actor, 'ACCOUNTING_OFFICER')
  const accountQuery = useQuery(loanAccountQuery(manager, loanApplicationId, canRead && validId))
  const [historyPage, setHistoryPage] = useState(0)
  const provenanceQuery = useQuery(staffServicingProvenanceQuery(
    manager, loanApplicationId, historyPage, 20, canRead && validId,
  ))
  const [confirmation, setConfirmation] = useState(false)
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const requestResultFocus = useOperationResultFocus(operation.status, 'closure-command-result')
  const [result, setResult] = useState<ClosedLoanAccountResult>()
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const account = accountQuery.data
  const provenance = provenanceQuery.data
  const coherentProvenance = provenance?.loanApplicationId === loanApplicationId
    && account?.loanApplicationId === loanApplicationId
    && provenance?.loanAccountId === account?.loanAccountId
    && provenance?.statusHistory.at(-1)?.toStatus === account?.status
  const provenanceError = provenanceQuery.error ?? (provenance && !coherentProvenance
    ? new Error('Account activity does not match this loan account.') : null)
  const visibleProvenance = coherentProvenance && !provenanceError ? provenance : undefined
  const unresolved = validId ? findUnresolvedOperation(operationType, loanApplicationId) : undefined
  const safeAccount = Boolean(account && hasCoherentLoanAccount(account))
  const readLocked = !account || !safeAccount || accountQuery.isFetching || accountQuery.isRefetchError
  const newClosureLocked = readLocked || account?.status !== 'SETTLED' || Boolean(unresolved)
    || operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING'
    || confirmedRefreshFailed

  const invalidateAffected = async () => {
    await queryClient.invalidateQueries({ queryKey: staffServicingKeys.all, refetchType: 'none' })
  }

  const refreshAfterSuccess = async (confirmed: ClosedLoanAccountResult) => {
    setResult(confirmed)
    setOperation({ status: 'RECONCILING' })
    await invalidateAffected().catch(() => undefined)
    // Actor history is informational; its availability cannot change the confirmed result.
    void provenanceQuery.refetch()
    try {
      const refreshed = await accountQuery.refetch({ throwOnError: true })
      if (refreshed.isError) throw new NetworkError()
      setConfirmedRefreshFailed(false)
      setOperation({ status: 'RESOLVED', detail: confirmed.idempotentReplay ? 'The retry confirmed the previously recorded closure. The latest account details are loaded.' : 'Administrative closure was confirmed and the latest account and work-queue information is loaded.' })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({ status: 'BLOCKED', error: error instanceof Error ? error : new NetworkError(), detail: 'Closure is confirmed, but the latest account information is unavailable. Use Refresh, and do not close the account again.' })
    }
  }

  const postCommand = async (requestId: string, digest: string, retrying: boolean) => {
    setOperation({ status: 'IN_FLIGHT' })
    try {
      const confirmed = await closeLoanAccount(manager, loanApplicationId, requestId)
      removeUnresolvedOperation(operationType, loanApplicationId)
      await refreshAfterSuccess(confirmed)
    } catch (error) {
      const commandError = error instanceof Error ? error : new NetworkError()
      if (isUnknownOutcome(commandError)) {
        saveUnresolvedOperation({ type: operationType, resource: loanApplicationId, operationId: requestId, payloadDigest: digest, unresolvedAt: new Date().toISOString() })
        await Promise.allSettled([accountQuery.refetch(), invalidateAffected()])
        setOperation({ status: 'RESULT_UNKNOWN', error: commandError, detail: retrying ? 'Closure is still unconfirmed. Retry this same closure.' : 'The closure result is not confirmed. Meridian did not submit it again automatically; retry this same closure.' })
        return
      }
      if (!(commandError instanceof ApiError)) return
      const keepIdentity = commandError.errorCode === 'IDEMPOTENCY_KEY_REUSED'
      if (!keepIdentity) removeUnresolvedOperation(operationType, loanApplicationId)
      if (['LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED', 'SYSTEM_STATE_CONFLICT'].includes(commandError.errorCode)) {
        await Promise.allSettled([accountQuery.refetch(), invalidateAffected()])
      }
      setOperation({ status: 'BLOCKED', error: commandError, detail: keepIdentity ? 'The saved recovery action conflicts with recorded closure evidence. Do not start a replacement action; operator resolution is required.' : operatorErrorMessage(commandError, 'The closure was rejected. A later closed account status does not confirm this attempted action.') })
    }
  }

  const submit = async () => {
    const digest = await digestOperationPayload(semanticPayload(loanApplicationId))
    const identity = decideOperationIdentity(operationType, loanApplicationId, digest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation({ status: 'BLOCKED', detail: 'The saved recovery action does not match this account. Do not start a replacement closure.' })
      return
    }
    await postCommand(identity.operationId, digest, identity.kind === 'REUSE_EXISTING')
  }

  const retryExact = async () => {
    if (!unresolved || readLocked) return
    const digest = await digestOperationPayload(semanticPayload(loanApplicationId))
    if (digest !== unresolved.payloadDigest) {
      setOperation({ status: 'BLOCKED', detail: 'This account does not match the unresolved closure. Retry remains blocked until the account matches.' })
      return
    }
    await postCommand(unresolved.operationId, digest, true)
  }

  const refreshOnly = async () => {
    void provenanceQuery.refetch()
    try {
      const refreshed = await accountQuery.refetch({ throwOnError: true })
      if (refreshed.isError) throw new NetworkError()
      if (confirmedRefreshFailed) {
        await invalidateAffected()
        setConfirmedRefreshFailed(false)
        setOperation({ status: 'RESOLVED', detail: 'The closure remains confirmed and the latest account information is now loaded.' })
      }
    } catch { /* cached evidence remains inspection-only */ }
  }

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Closure workspace unavailable</h1><Alert variant="warning"><AlertTriangle /><AlertTitle>Invalid application identifier</AlertTitle></Alert></section>
  if (!canRead) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Closure workspace unavailable</h1><Alert variant="warning"><ShieldAlert /><AlertTitle>Loan account access required</AlertTitle><AlertDescription>You need access to the loan account before you can close it.</AlertDescription></Alert></section>
  if (accountQuery.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading closure workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading loan account information…</div></section>
  if (accountQuery.isError && !account) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Administrative closure</h1><QueryErrorPanel error={accountQuery.error} resource="case" onRetry={() => void accountQuery.refetch()} /></section>
  if (!account) return null

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/closures">← Back to closure queue</Link><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/loan-account`}>Loan account workspace</Link></div>
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">ACCOUNTING OFFICER OPERATION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Administrative closure</h1><p className="mt-2 text-muted-foreground">Account {account.accountNumber} · current state {accountStatusLabel(account.status)}</p></div><Button variant="outline" onClick={() => void refreshOnly()} disabled={accountQuery.isFetching}><RefreshCw className={accountQuery.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div></header>
    <Alert variant="information"><AlertTitle>Separate non-financial outcome</AlertTitle><AlertDescription>Closure changes a settled loan account to closed. It records no payment and leaves allocations, balances, the final schedule, installment progress, lending exposure, and application status unchanged.</AlertDescription></Alert>
    {!canClose ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting Officer access required</AlertTitle><AlertDescription>Accounting Officer access is required to close the loan account.</AlertDescription></Alert> : null}
    {accountQuery.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Previously loaded details are shown. Refresh successfully before closing the account.</AlertDescription></Alert> : null}
    {!safeAccount ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Loan account details need review</AlertTitle><AlertDescription>Account details are incomplete or inconsistent. Refresh before taking action.</AlertDescription></Alert> : null}
    {unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Previous closure result unknown</AlertTitle><AlertDescription>A later closed account status does not confirm this attempted closure. Retry this same closure before starting any different action.</AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Closure action</CardTitle></CardHeader><CardContent className="space-y-4"><p className="text-sm text-muted-foreground">Meridian checks that the balance is fully settled and reconciled before closing the account.</p>{unresolved ? <Button disabled={!canClose || readLocked} onClick={() => void retryExact()}>Retry this closure</Button> : <Button id="confirm-closure-trigger" disabled={!canClose || newClosureLocked} onClick={() => setConfirmation(true)}>Review administrative closure</Button>}{account.status !== 'SETTLED' && !unresolved ? <p className="text-sm font-semibold text-muted-foreground">Refresh to confirm that the account is settled and reconciled before closing it.</p> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} headingId="closure-command-result" headingLabel={`Closure result for account ${account.accountNumber}`} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{confirmedRefreshFailed && result ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Closure recorded; refresh needed</AlertTitle><AlertDescription>The account was confirmed closed at {formatTimestamp(result.closedAt)}. The latest account information is still unavailable; use Refresh and do not close the account again.</AlertDescription></Alert> : null}</CardContent></Card>
    {result ? <Card><CardHeader><CardTitle>{result.idempotentReplay ? 'Previously recorded closure confirmed' : 'Administrative closure confirmed'}</CardTitle></CardHeader><CardContent><p>Account status: <strong>{accountStatusLabel(result.resultingStatus)}</strong> · closed {formatTimestamp(result.closedAt)}</p></CardContent></Card> : null}
    <LoanAccountEvidence account={account} />
    <StaffServicingProvenancePanel data={visibleProvenance} pending={provenanceQuery.isPending} error={provenanceError} onRetry={() => void provenanceQuery.refetch()} />
    <RepaymentHistoryPanel data={visibleProvenance?.repaymentHistory} pending={provenanceQuery.isPending} error={provenanceError} fetching={provenanceQuery.isFetching} onRetry={() => void provenanceQuery.refetch()} onPage={setHistoryPage} />
    {confirmation ? <ConfirmationDialog onDismiss={() => { setConfirmation(false); setTimeout(() => document.getElementById('confirm-closure-trigger')?.focus(), 0) }} className="max-w-xl"><ConfirmationDialogTitle className="text-xl font-semibold">Confirm administrative closure</ConfirmationDialogTitle><ConfirmationDialogDescription>Close account <strong>{account.accountNumber}</strong> from its current <strong>Settled</strong> status.</ConfirmationDialogDescription><p className="text-sm text-muted-foreground">This creates no payment and changes no financial evidence.</p><ConfirmationDialogFooter><ConfirmationDialogCancel asChild><Button variant="outline">Cancel</Button></ConfirmationDialogCancel><Button autoFocus disabled={newClosureLocked} onClick={() => { requestResultFocus(); setConfirmation(false); void submit() }}>Close loan account</Button></ConfirmationDialogFooter></ConfirmationDialog> : null}
  </section>
}
