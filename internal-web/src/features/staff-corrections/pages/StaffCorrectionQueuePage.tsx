import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, ExternalLink, ListChecks } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { formatTimestamp } from '@/lib/format/presentation'
import { staffCorrectionQueueQuery } from '../api/queries'

const PAGE_SIZE = 20
function pageFrom(value: string | null) {
  if (value === null) return 0
  return /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : undefined
}

export function StaffCorrectionQueuePage() {
  const { manager, state } = useAuth()
  const [params, setParams] = useSearchParams()
  const page = pageFrom(params.get('page'))
  const allowed = state.status === 'authenticated' && hasPermission(state.actor, 'loan:correction:staff')
  const query = useQuery(staffCorrectionQueueQuery(manager, page ?? 0, PAGE_SIZE, allowed && page !== undefined))
  const updatePage = (next: number) => setParams(next === 0 ? new URLSearchParams() : new URLSearchParams({ page: String(next) }))

  return <section className="mx-auto max-w-[90rem] space-y-6">
    <header><p className="text-sm font-semibold text-muted-foreground">CORRECTION OPERATIONS</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Staff corrections</h1><p className="mt-2 text-muted-foreground">Open Staff-owned tasks. Customer-owned work remains visible only in the case workspace.</p></header>
    {page === undefined ? <Alert variant="warning"><ListChecks /><AlertTitle>Invalid page</AlertTitle><AlertDescription>Use a non-negative queue page.</AlertDescription></Alert> : null}
    {query.isPending && page !== undefined ? <div role="status" className="flex min-h-48 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading correction work…</div> : null}
    {query.isError && !query.data ? <QueryErrorPanel error={query.error} resource="correction queue" onRetry={() => void query.refetch()} /> : null}
    {query.data ? <>
      {query.data.length === 0 ? <div className="rounded-lg border bg-card p-8 text-center"><ListChecks className="mx-auto size-8 text-muted-foreground" /><h2 className="mt-3 text-lg font-semibold">No Staff corrections on this page</h2></div> : <div className="overflow-x-auto rounded-lg border bg-card" tabIndex={0} aria-label="Staff correction tasks"><table className="w-full min-w-[68rem] text-left text-sm"><caption className="sr-only">Open Staff correction tasks</caption><thead className="bg-muted/70 text-xs uppercase text-muted-foreground"><tr><th className="px-4 py-3" scope="col">Scope</th><th className="px-4 py-3" scope="col">Application</th><th className="px-4 py-3" scope="col">Reason</th><th className="px-4 py-3" scope="col">Instruction</th><th className="px-4 py-3" scope="col">Created</th><th className="px-4 py-3" scope="col"><span className="sr-only">Action</span></th></tr></thead><tbody className="divide-y">{query.data.map((task) => <tr key={task.taskId}><td className="px-4 py-4"><p className="font-semibold">{humanizeKnownValue(task.scope)}</p><p className="text-xs text-muted-foreground">{task.documentType ? humanizeKnownValue(task.documentType) : 'No document type'}</p></td><td className="px-4 py-4"><code className="text-xs">{task.loanApplicationId}</code></td><td className="px-4 py-4">{humanizeKnownValue(task.reasonCode)}</td><td className="max-w-sm px-4 py-4">{task.staffInstruction ?? 'No Staff instruction'}</td><td className="px-4 py-4 whitespace-nowrap">{formatTimestamp(task.createdAt)}</td><td className="px-4 py-4 text-right"><Button asChild variant="link"><Link aria-label={`Open correction task ${task.taskId}`} to={`/staff/applications/${task.loanApplicationId}/corrections?taskId=${task.taskId}`}>Open task <ExternalLink /></Link></Button></td></tr>)}</tbody></table></div>}
      <nav aria-label="Correction queue pages" className="flex items-center justify-between rounded-lg border bg-card p-4"><p className="text-sm text-muted-foreground">Page {(page ?? 0) + 1}</p><div className="flex gap-2"><Button variant="outline" disabled={(page ?? 0) === 0} onClick={() => updatePage((page ?? 0) - 1)}><ChevronLeft /> Previous</Button><Button variant="outline" disabled={query.data.length < PAGE_SIZE} onClick={() => updatePage((page ?? 0) + 1)}>Next <ChevronRight /></Button></div></nav>
    </> : null}
  </section>
}
