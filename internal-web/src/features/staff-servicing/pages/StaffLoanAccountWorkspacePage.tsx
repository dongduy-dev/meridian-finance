import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { LoanAccountEvidence } from '../components/LoanAccountEvidence'
import { RepaymentHistoryPanel } from '../components/RepaymentHistoryPanel'
import { loanAccountQuery, repaymentHistoryQuery } from '../api/queries'
import { hasCoherentLoanAccount, serviceableAccountStatuses } from '../model/presentation'

export function StaffLoanAccountWorkspacePage() {
  const { manager, state } = useAuth()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const canRecord = state.status === 'authenticated' && hasPermission(state.actor, 'repayment:update')
  const [historyPage, setHistoryPage] = useState(0)
  const accountQuery = useQuery(loanAccountQuery(manager, loanApplicationId, canRead && validId))
  const historyQuery = useQuery(repaymentHistoryQuery(
    manager,
    loanApplicationId,
    historyPage,
    20,
    canRead && validId,
  ))
  const account = accountQuery.data
  const safeAccount = Boolean(account && hasCoherentLoanAccount(account))
  const recordAvailable = Boolean(account
    && canRecord
    && safeAccount
    && serviceableAccountStatuses.has(account.status)
    && !accountQuery.isFetching
    && !accountQuery.isRefetchError)

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">LoanAccount workspace unavailable</h1><Alert variant="warning"><AlertTriangle /><AlertTitle>Invalid application identifier</AlertTitle><AlertDescription>This route does not contain a valid loanApplicationId.</AlertDescription></Alert></section>
  if (accountQuery.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading LoanAccount workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative LoanAccount evidence…</div></section>
  if (accountQuery.isError && !account) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">LoanAccount servicing workspace</h1><QueryErrorPanel error={accountQuery.error} resource="case" onRetry={() => void accountQuery.refetch()} /></section>
  if (!account) return null

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/servicing">← Servicing queue</Link><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}`}>Application case</Link></div>
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">LOANACCOUNT SERVICING</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">{account.accountNumber}</h1><p className="mt-2 break-all text-sm text-muted-foreground">Application {account.loanApplicationId}</p></div><div className="flex flex-wrap gap-2"><Button variant="outline" onClick={() => void Promise.all([accountQuery.refetch(), historyQuery.refetch()])} disabled={accountQuery.isFetching || historyQuery.isFetching}><RefreshCw className={accountQuery.isFetching ? 'animate-spin' : undefined} />Refresh</Button>{recordAvailable ? <Button asChild><Link to={`/staff/applications/${loanApplicationId}/repayments/new`}>Record repayment</Link></Button> : null}</div></div></header>
    {accountQuery.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest account refresh unavailable</AlertTitle><AlertDescription>Cached financial evidence remains visible but cannot authorize a repayment until Refresh succeeds.</AlertDescription></Alert> : null}
    {!safeAccount ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>LoanAccount evidence unavailable</AlertTitle><AlertDescription>Unknown or contradictory response evidence is shown only as a neutral inspection state. Repayment is disabled.</AlertDescription></Alert> : null}
    {safeAccount && !serviceableAccountStatuses.has(account.status) ? <Alert variant="information"><AlertTitle>Read-only terminal account</AlertTitle><AlertDescription>Ordinary repayment is unavailable for {account.status}. Settlement and administrative closure operations are outside CP8.</AlertDescription></Alert> : null}
    <LoanAccountEvidence account={account} />
    <RepaymentHistoryPanel data={historyQuery.data} pending={historyQuery.isPending} error={historyQuery.error} fetching={historyQuery.isFetching} onRetry={() => void historyQuery.refetch()} onPage={setHistoryPage} />
  </section>
}
