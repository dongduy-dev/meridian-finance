import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Inbox } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { productLabel } from '@/features/staff-applications/model/presentation'
import { formatDateOnly, formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { staffServicingWorkQuery } from '../api/queries'
import { knownAccountStatuses, serviceableAccountStatuses } from '../model/presentation'
import { ServicingStatusBadge } from '../components/ServicingStatusBadge'

export function StaffServicingWorkQueuePage() {
  const { manager, state } = useAuth()
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const [productCode, setProductCode] = useState('')
  const [accountStatus, setAccountStatus] = useState('')
  const [page, setPage] = useState(0)
  const filters = {
    productCode: productCode || undefined,
    accountStatus: accountStatus ? accountStatus as 'ACTIVE' | 'OVERDUE' : undefined,
    page,
    size: 25,
  }
  const query = useQuery({
    ...staffServicingWorkQuery(manager, filters, canRead),
    placeholderData: keepPreviousData,
  })
  const data = query.data

  return <section className="mx-auto max-w-6xl space-y-6">
    <header><p className="text-sm font-semibold text-muted-foreground">ACCOUNT SERVICING</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">LoanAccount servicing queue</h1><p className="mt-2 max-w-3xl text-muted-foreground">Membership is server-owned: only ACTIVE and OVERDUE LoanAccounts eligible for ordinary repayment work appear. Overdue state is evaluated by the backend scheduler.</p></header>
    <Card><CardHeader><CardTitle>Queue filters</CardTitle></CardHeader><CardContent><div className="grid gap-4 sm:grid-cols-2"><label className="grid gap-2 text-sm font-semibold">Product<select className="h-11 rounded-md border bg-card px-3 font-normal" value={productCode} onChange={(event) => { setProductCode(event.target.value); setPage(0) }}><option value="">All products</option><option value="SALARY_ADVANCE">Salary Advance</option><option value="UNSECURED_CONSUMER_LOAN">Unsecured Consumer Loan</option><option value="COLLATERAL_LOAN">Collateral Loan</option></select></label><label className="grid gap-2 text-sm font-semibold">Serviceable status<select className="h-11 rounded-md border bg-card px-3 font-normal" value={accountStatus} onChange={(event) => { setAccountStatus(event.target.value); setPage(0) }}><option value="">All serviceable</option><option value="ACTIVE">Active</option><option value="OVERDUE">Overdue</option></select></label></div></CardContent></Card>
    {query.isPending ? <div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative servicing work…</div> : null}
    {query.isError && !data ? <QueryErrorPanel error={query.error} resource="queue" onRetry={() => void query.refetch()} /> : null}
    {data ? <>{query.isError ? <Alert variant="warning"><Inbox /><AlertTitle>Latest queue refresh unavailable</AlertTitle><AlertDescription>The last validated page remains visible for inspection only.</AlertDescription></Alert> : null}{data.items.length === 0 ? <Alert variant="information"><Inbox /><AlertTitle>No ordinary repayment work</AlertTitle><AlertDescription>No ACTIVE or OVERDUE accounts match the selected server filters.</AlertDescription></Alert> : <div className="grid gap-4">{data.items.map((item) => {
      const safe = knownAccountStatuses.has(item.accountStatus)
        && serviceableAccountStatuses.has(item.accountStatus)
        && item.totalOutstanding > 0
      return <Card key={item.loanAccountId}><CardContent className="grid gap-4 p-5 lg:grid-cols-[1fr_auto] lg:items-center"><div className="min-w-0 space-y-2"><div className="flex flex-wrap items-center gap-3"><h2 className="font-semibold">{item.applicationNumber}</h2><ServicingStatusBadge status={item.accountStatus} /></div><p className="text-sm text-muted-foreground">Account {item.accountNumber} · {productLabel(item.productCode)} · activated {formatTimestamp(item.activatedAt)}</p><p className="text-sm"><span className="font-semibold">{formatVnd(item.totalOutstanding)} outstanding</span> · {formatVnd(item.totalPaid)} paid · {formatVnd(item.originatedPrincipal)} originated</p><p className="text-sm text-muted-foreground">Evaluated {formatDateOnly(item.servicingEvaluationDate)} · last payment {formatDateOnly(item.lastPaymentValueDate)}</p>{!safe ? <p className="text-sm font-semibold text-danger">Unknown or non-serviceable evidence is not actionable.</p> : null}</div><Button asChild variant={safe ? 'default' : 'outline'}><Link to={`/staff/applications/${item.loanApplicationId}/loan-account`}>{safe ? 'Open LoanAccount' : 'Inspect unavailable state'}</Link></Button></CardContent></Card>
    })}</div>}<div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-muted-foreground">Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} account{data.totalElements === 1 ? '' : 's'}</p><div className="flex gap-2"><Button variant="outline" disabled={data.page === 0 || query.isFetching} onClick={() => setPage((value) => Math.max(0, value - 1))}><ChevronLeft />Previous</Button><Button variant="outline" disabled={data.page + 1 >= data.totalPages || query.isFetching} onClick={() => setPage((value) => value + 1)}>Next<ChevronRight /></Button></div></div></> : null}
  </section>
}
