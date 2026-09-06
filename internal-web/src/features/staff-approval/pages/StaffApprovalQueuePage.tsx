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
import { StatusBadge } from '@/features/staff-applications/components/StatusBadge'
import { humanizeKnownValue, productLabel } from '@/features/staff-applications/model/presentation'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { staffApprovalQueueQuery } from '../api/queries'

export function StaffApprovalQueuePage() {
  const { manager, state } = useAuth()
  const canDecide = state.status === 'authenticated' && hasPermission(state.actor, 'approval:decide')
  const [productCode, setProductCode] = useState('')
  const [page, setPage] = useState(0)
  const filters = { productCode: productCode || undefined, page, size: 25 }
  const query = useQuery({ ...staffApprovalQueueQuery(manager, filters, canDecide), placeholderData: keepPreviousData })
  const data = query.data

  return <section className="mx-auto max-w-6xl space-y-6"><header><p className="text-sm font-semibold text-muted-foreground">APPROVER WORK</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Independent decision queue</h1><p className="mt-2 max-w-3xl text-muted-foreground">Membership is server-owned: exact APPROVAL_PENDING applications, ordered by submission time and identifier. Maker-checker eligibility is derived by the backend.</p></header><Card><CardHeader><CardTitle>Queue filter</CardTitle></CardHeader><CardContent><label className="grid max-w-md gap-2 text-sm font-semibold">Product<select className="h-11 rounded-md border bg-card px-3 font-normal" value={productCode} onChange={(event) => { setProductCode(event.target.value); setPage(0) }}><option value="">All products</option><option value="SALARY_ADVANCE">Salary Advance</option><option value="UNSECURED_CONSUMER_LOAN">Unsecured Consumer Loan</option><option value="COLLATERAL_LOAN">Collateral Loan</option></select></label></CardContent></Card>{query.isPending ? <div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative approval work…</div> : null}{query.isError && !data ? <QueryErrorPanel error={query.error} resource="queue" onRetry={() => void query.refetch()} /> : null}{data ? <>{query.isError ? <Alert variant="warning"><Inbox /><AlertTitle>Latest queue refresh unavailable</AlertTitle><AlertDescription>The last validated page remains visible, but no case should be opened as newly eligible until Refresh succeeds.</AlertDescription></Alert> : null}{data.items.length === 0 ? <Alert variant="information"><Inbox /><AlertTitle>No approval work</AlertTitle><AlertDescription>No applications match the authoritative queue filter.</AlertDescription></Alert> : <div className="grid gap-4">{data.items.map((item) => <Card key={item.loanApplicationId}><CardContent className="grid gap-4 p-5 lg:grid-cols-[1fr_auto] lg:items-center"><div className="space-y-2"><div className="flex flex-wrap items-center gap-3"><h2 className="font-semibold">{item.applicationNumber}</h2><StatusBadge status={item.applicationStatus} /></div><p className="text-sm text-muted-foreground">{productLabel(item.productCode)} · {formatVnd(item.requestedAmount)} · {item.requestedTermMonths} months</p><p className="text-sm">Recommendation: <span className="font-semibold">{humanizeKnownValue(item.recommendationAction)}</span> · {formatTimestamp(item.recommendationSubmittedAt)}</p>{!item.makerCheckerEligible ? <p className="text-sm font-semibold text-danger">You recorded this recommendation and cannot decide it.</p> : null}</div><Button asChild variant={item.decisionAvailable ? 'default' : 'outline'}><Link to={`/staff/applications/${item.loanApplicationId}/decision`}>{item.decisionAvailable ? 'Open decision' : 'View blocked case'}</Link></Button></CardContent></Card>)}</div>}<div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-muted-foreground">Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} item{data.totalElements === 1 ? '' : 's'}</p><div className="flex gap-2"><Button variant="outline" disabled={data.page === 0 || query.isFetching} onClick={() => setPage((value) => Math.max(0, value - 1))}><ChevronLeft />Previous</Button><Button variant="outline" disabled={data.page + 1 >= data.totalPages || query.isFetching} onClick={() => setPage((value) => value + 1)}>Next<ChevronRight /></Button></div></div></> : null}</section>
}
