import { ChevronLeft, ChevronRight, History } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { formatDateOnly, formatTimestamp, formatVnd } from '@/lib/format/presentation'
import type { RepaymentHistoryPage } from '../api/contracts'
import { ServicingStatusBadge } from './ServicingStatusBadge'

export function RepaymentHistoryPanel({ data, pending, error, fetching, onRetry, onPage }: {
  data?: RepaymentHistoryPage
  pending: boolean
  error?: Error | null
  fetching: boolean
  onRetry: () => void
  onPage: (page: number) => void
}) {
  return <Card><CardHeader><CardTitle>Immutable repayment history</CardTitle><p className="text-sm text-muted-foreground">Historical allocation and installment outcomes are durable operation evidence. They are not recalculated from the current account.</p></CardHeader><CardContent className="space-y-4">{pending ? <div role="status" className="flex min-h-28 items-center justify-center gap-2"><Spinner /> Loading repayment history…</div> : null}{error && !data ? <QueryErrorPanel error={error} resource="case" onRetry={onRetry} /> : null}{error && data ? <Alert variant="warning"><History /><AlertTitle>Latest history refresh unavailable</AlertTitle><AlertDescription>The last validated page remains visible.</AlertDescription></Alert> : null}{data?.items.length === 0 ? <Alert variant="information"><History /><AlertTitle>No repayments recorded</AlertTitle><AlertDescription>This LoanAccount has no immutable repayment outcomes.</AlertDescription></Alert> : null}{data?.items.map((item) => <details key={item.repaymentTransactionId} className="rounded-md border p-4"><summary className="cursor-pointer font-semibold">{formatVnd(item.receivedAmount)} · {formatDateOnly(item.paymentValueDate)} · <ServicingStatusBadge status={item.resultingLoanAccountStatus} /></summary><div className="mt-4 space-y-4"><p className="text-sm text-muted-foreground">Recorded {formatTimestamp(item.recordedAt)} · resulting balance {formatVnd(item.accountBalance.totalOutstanding)} · principal allocated {formatVnd(item.principalAllocated)} · exposure released {formatVnd(item.principalReleased)}</p><div className="overflow-x-auto rounded-md border"><table className="w-full min-w-[36rem] text-left text-sm"><thead className="bg-muted text-xs uppercase text-muted-foreground"><tr><th className="p-3">Sequence</th><th className="p-3">Installment</th><th className="p-3">Component</th><th className="p-3">Allocated</th></tr></thead><tbody>{item.allocations.map((allocation) => <tr key={`${allocation.sequence}-${allocation.repaymentScheduleItemId}`} className="border-t"><td className="p-3">{allocation.sequence}</td><td className="p-3">{allocation.installmentNumber}</td><td className="p-3">{allocation.component}</td><td className="p-3">{formatVnd(allocation.allocatedAmount)}</td></tr>)}</tbody></table></div></div></details>)}{data ? <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-muted-foreground">Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} payment{data.totalElements === 1 ? '' : 's'}</p><div className="flex gap-2"><Button variant="outline" disabled={data.page === 0 || fetching} onClick={() => onPage(data.page - 1)}><ChevronLeft />Previous</Button><Button variant="outline" disabled={data.page + 1 >= data.totalPages || fetching} onClick={() => onPage(data.page + 1)}>Next<ChevronRight /></Button></div></div> : null}</CardContent></Card>
}
