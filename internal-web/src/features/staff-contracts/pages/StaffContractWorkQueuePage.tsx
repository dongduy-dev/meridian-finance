import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { AlertTriangle, ChevronLeft, ChevronRight, Inbox } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { StatusBadge } from '@/features/staff-applications/components/StatusBadge'
import { productLabel } from '@/features/staff-applications/model/presentation'
import { ApiError } from '@/lib/api'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { staffContractWorkQuery } from '../api/queries'
import {
  blockerLabel,
  contractStageLabel,
  contractStatusLabel,
  hasCoherentContractLifecycle,
  knownContractStatuses,
  knownContractWorkStages,
  knownReadinessBlockers,
} from '../model/presentation'

export function StaffContractWorkQueuePage() {
  const { manager, state } = useAuth()
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:contract:read')
  const isAccounting = state.status === 'authenticated' && hasRole(state.actor, 'ACCOUNTING_OFFICER')
  const [productCode, setProductCode] = useState('')
  const [page, setPage] = useState(0)
  const filters = { productCode: productCode || undefined, page, size: 25 }
  const query = useQuery({
    ...staffContractWorkQuery(manager, filters, canRead),
    placeholderData: keepPreviousData,
  })
  const data = query.data
  const accountingRejected = query.error instanceof ApiError
    && query.error.errorCode === 'ACCOUNTING_ROLE_REQUIRED'

  return <section className="mx-auto max-w-6xl space-y-6">
    <header>
      <p className="text-sm font-semibold text-muted-foreground">ACCOUNTING WORK</p>
      <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Contract and readiness queue</h1>
      <p className="mt-2 max-w-3xl text-muted-foreground">Membership is server-owned: only CONTRACT_PENDING applications appear. Readiness is point-in-time advisory evidence.</p>
    </header>
    {!isAccounting || accountingRejected ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>This route is visible because your session has contract-read permission, but contract operations additionally require the ACCOUNTING_OFFICER role.</AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Queue filter</CardTitle></CardHeader><CardContent><label className="grid max-w-md gap-2 text-sm font-semibold">Product<select className="h-11 rounded-md border bg-card px-3 font-normal" value={productCode} onChange={(event) => { setProductCode(event.target.value); setPage(0) }}><option value="">All products</option><option value="SALARY_ADVANCE">Salary Advance</option><option value="UNSECURED_CONSUMER_LOAN">Unsecured Consumer Loan</option><option value="COLLATERAL_LOAN">Collateral Loan</option></select></label></CardContent></Card>
    {query.isPending ? <div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative contract work…</div> : null}
    {query.isError && !data ? accountingRejected ? null : <QueryErrorPanel error={query.error} resource="queue" onRetry={() => void query.refetch()} /> : null}
    {data ? <>
      {query.isError ? <Alert variant="warning"><Inbox /><AlertTitle>Latest queue refresh unavailable</AlertTitle><AlertDescription>The last validated page remains visible. Open cases only to inspect; refreshed evidence is required before any command.</AlertDescription></Alert> : null}
      {data.items.length === 0 ? <Alert variant="information"><Inbox /><AlertTitle>No contract work</AlertTitle><AlertDescription>No applications match the authoritative queue filter.</AlertDescription></Alert> : <div className="grid gap-4">{data.items.map((item) => {
        const knownStage = knownContractWorkStages.has(item.workStage)
        const knownStatus = !item.currentContract || knownContractStatuses.has(item.currentContract.status)
        const unknownBlocker = item.readiness.blockerCodes.some((code) => !knownReadinessBlockers.has(code))
        const safe = knownStage && knownStatus && !unknownBlocker && hasCoherentContractLifecycle(item)
        return <Card key={item.loanApplicationId}><CardContent className="grid gap-4 p-5 lg:grid-cols-[1fr_auto] lg:items-center"><div className="space-y-2"><div className="flex flex-wrap items-center gap-3"><h2 className="font-semibold">{item.applicationNumber}</h2><StatusBadge status={item.applicationStatus} /></div><p className="text-sm text-muted-foreground">{productLabel(item.productCode)} · {formatVnd(item.requestedAmount)} · {item.requestedTermMonths} months · submitted {formatTimestamp(item.submittedAt)}</p><p className="text-sm"><span className="font-semibold">{contractStageLabel(item.workStage)}</span>{item.currentContract ? ` · Contract v${item.currentContract.contractVersion} · ${contractStatusLabel(item.currentContract.status)}` : ' · No current contract'}</p><p className="text-sm text-muted-foreground">{item.readiness.ready && item.readiness.blockerCodes.length === 0 && !unknownBlocker ? 'Advisory readiness reports ready.' : item.readiness.blockerCodes.length > 0 ? item.readiness.blockerCodes.map(blockerLabel).join(' ') : 'Readiness evidence is unavailable.'}</p>{!safe ? <p className="text-sm font-semibold text-danger">Unknown operational evidence requires an authoritative refresh. Consequential actions will remain disabled.</p> : null}</div><Button asChild variant={safe ? 'default' : 'outline'}><Link to={`/staff/applications/${item.loanApplicationId}/contract`}>{safe ? 'Open contract workspace' : 'Inspect unavailable state'}</Link></Button></CardContent></Card>
      })}</div>}
      <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-muted-foreground">Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} item{data.totalElements === 1 ? '' : 's'}</p><div className="flex gap-2"><Button variant="outline" disabled={data.page === 0 || query.isFetching} onClick={() => setPage((value) => Math.max(0, value - 1))}><ChevronLeft />Previous</Button><Button variant="outline" disabled={data.page + 1 >= data.totalPages || query.isFetching} onClick={() => setPage((value) => value + 1)}>Next<ChevronRight /></Button></div></div>
    </> : null}
  </section>
}
