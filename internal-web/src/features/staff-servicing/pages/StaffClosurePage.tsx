import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
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
import { loanAccountQuery, staffServicingKeys } from '../api/queries'
import { closeLoanAccount } from '../api/staff-servicing-api'
import { LoanAccountEvidence } from '../components/LoanAccountEvidence'
import { hasCoherentLoanAccount } from '../model/presentation'

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
  const [confirmation, setConfirmation] = useState(false)
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const [result, setResult] = useState<ClosedLoanAccountResult>()
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const account = accountQuery.data
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
    try {
      const refreshed = await accountQuery.refetch({ throwOnError: true })
      if (refreshed.isError) throw new NetworkError()
      setConfirmedRefreshFailed(false)
      setOperation({ status: 'RESOLVED', detail: confirmed.idempotentReplay ? 'The original closure was recovered through exact same-request replay; current account state was refreshed separately.' : 'Administrative closure was confirmed and current account and work queues were reconciled.' })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({ status: 'BLOCKED', error: error instanceof Error ? error : new NetworkError(), detail: 'Closure is confirmed. Current account refresh failed; retry GET only.' })
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
        setOperation({ status: 'RESULT_UNKNOWN', error: commandError, detail: retrying ? 'The exact replay result is also unknown; the same closure UUID remains retained.' : 'The result is unknown. The stable request UUID and minimal semantic digest were persisted; no POST was retried.' })
        return
      }
      if (!(commandError instanceof ApiError)) return
      const keepIdentity = commandError.errorCode === 'IDEMPOTENCY_KEY_REUSED'
      if (!keepIdentity) removeUnresolvedOperation(operationType, loanApplicationId)
      if (['LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED', 'SYSTEM_STATE_CONFLICT'].includes(commandError.errorCode)) {
        await Promise.allSettled([accountQuery.refetch(), invalidateAffected()])
      }
      setOperation({ status: 'BLOCKED', error: commandError, detail: keepIdentity ? 'The retained closure UUID conflicts with backend evidence. No replacement UUID was generated.' : 'The backend definitely rejected this request. A CLOSED account after another operator acted is not presented as success for this UUID.' })
    }
  }

  const submit = async () => {
    const digest = await digestOperationPayload(semanticPayload(loanApplicationId))
    const identity = decideOperationIdentity(operationType, loanApplicationId, digest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation({ status: 'BLOCKED', detail: 'The unresolved closure identity does not match this resource. No replacement UUID was created.' })
      return
    }
    await postCommand(identity.operationId, digest, identity.kind === 'REUSE_EXISTING')
  }

  const retryExact = async () => {
    if (!unresolved || readLocked) return
    const digest = await digestOperationPayload(semanticPayload(loanApplicationId))
    if (digest !== unresolved.payloadDigest) {
      setOperation({ status: 'BLOCKED', detail: 'The current resource does not match the unresolved closure digest.' })
      return
    }
    await postCommand(unresolved.operationId, digest, true)
  }

  const refreshOnly = async () => {
    try {
      const refreshed = await accountQuery.refetch({ throwOnError: true })
      if (refreshed.isError) throw new NetworkError()
      if (confirmedRefreshFailed) {
        await invalidateAffected()
        setConfirmedRefreshFailed(false)
        setOperation({ status: 'RESOLVED', detail: 'The confirmed closure is now reconciled with authoritative current state.' })
      }
    } catch { /* cached evidence remains inspection-only */ }
  }

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Closure workspace unavailable</h1><Alert variant="warning"><AlertTriangle /><AlertTitle>Invalid application identifier</AlertTitle></Alert></section>
  if (!canRead) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Closure workspace unavailable</h1><Alert variant="warning"><ShieldAlert /><AlertTitle>LoanAccount read authority required</AlertTitle><AlertDescription>No unauthorized account query was sent.</AlertDescription></Alert></section>
  if (accountQuery.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading closure workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative LoanAccount evidence…</div></section>
  if (accountQuery.isError && !account) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Administrative closure</h1><QueryErrorPanel error={accountQuery.error} resource="case" onRetry={() => void accountQuery.refetch()} /></section>
  if (!account) return null

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/closures">← Closure queue</Link><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}/loan-account`}>LoanAccount workspace</Link></div>
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">ACCOUNTING OFFICER OPERATION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Administrative closure</h1><p className="mt-2 text-muted-foreground">Account {account.accountNumber} · current state {account.status}</p></div><Button variant="outline" onClick={() => void refreshOnly()} disabled={accountQuery.isFetching}><RefreshCw className={accountQuery.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div></header>
    <Alert variant="information"><AlertTitle>Separate non-financial outcome</AlertTitle><AlertDescription>Closure records the separate SETTLED → CLOSED administrative outcome. It does not record another payment or change allocations, balances, the final schedule, installment progress, product exposure, or LoanApplication state.</AlertDescription></Alert>
    {!canClose ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting Officer authority required</AlertTitle><AlertDescription>Both loan:account:close and the ACCOUNTING_OFFICER role are required.</AlertDescription></Alert> : null}
    {accountQuery.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest authoritative refresh unavailable</AlertTitle><AlertDescription>Cached evidence remains inspectable, but closure is disabled.</AlertDescription></Alert> : null}
    {!safeAccount ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Contradictory LoanAccount evidence</AlertTitle><AlertDescription>No consequential action is available until evidence is coherent.</AlertDescription></Alert> : null}
    {unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Previous closure result unknown</AlertTitle><AlertDescription>A later CLOSED state does not prove this UUID succeeded. Exact same-request replay remains available even when the account is CLOSED.</AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Closure action</CardTitle></CardHeader><CardContent className="space-y-4"><p className="text-sm text-muted-foreground">There are no financial inputs. The backend verifies the settled payoff provenance and complete reconciliation.</p>{unresolved ? <Button disabled={!canClose || readLocked} onClick={() => void retryExact()}>Retry exact closure</Button> : <Button id="confirm-closure-trigger" disabled={!canClose || newClosureLocked} onClick={() => setConfirmation(true)}>Review administrative closure</Button>}{account.status !== 'SETTLED' && !unresolved ? <p className="text-sm font-semibold text-muted-foreground">Only a fresh, coherent SETTLED account is eligible for a new closure request.</p> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}{confirmedRefreshFailed && result ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Closure command confirmed</AlertTitle><AlertDescription>The account was confirmed CLOSED at {formatTimestamp(result.closedAt)}. Retry GET reconciliation only.</AlertDescription></Alert> : null}</CardContent></Card>
    {result ? <Card><CardHeader><CardTitle>{result.idempotentReplay ? 'Closure recovered by exact replay' : 'Administrative closure confirmed'}</CardTitle></CardHeader><CardContent><p>Account state: <strong>{result.resultingStatus}</strong> · closed {formatTimestamp(result.closedAt)}</p></CardContent></Card> : null}
    <LoanAccountEvidence account={account} />
    {confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="closure-confirm-title"><div className="max-h-[90vh] w-full max-w-xl space-y-4 overflow-y-auto rounded-lg bg-card p-6 shadow-xl"><h2 id="closure-confirm-title" className="text-xl font-semibold">Confirm administrative closure</h2><p>Close account <strong>{account.accountNumber}</strong> from its current <strong>SETTLED</strong> state.</p><p className="text-sm text-muted-foreground">This creates no payment and changes no financial evidence.</p><div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={() => { setConfirmation(false); setTimeout(() => document.getElementById('confirm-closure-trigger')?.focus(), 0) }}>Cancel</Button><Button autoFocus disabled={newClosureLocked} onClick={() => { setConfirmation(false); void submit() }}>Confirm closure</Button></div></div></div> : null}
  </section>
}
