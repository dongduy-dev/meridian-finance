import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, ExternalLink, FileSearch2 } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { formatTimestamp } from '@/lib/format/presentation'
import { documentReviewQueueQuery } from '../api/queries'

const PAGE_SIZE = 20
function pageFrom(value: string | null) {
  if (value === null) return 0
  return /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : undefined
}
export function DocumentReviewQueuePage() {
  const { manager, state } = useAuth()
  const [params, setParams] = useSearchParams()
  const page = pageFrom(params.get('page'))
  const allowed = state.status === 'authenticated' && hasPermission(state.actor, 'document:review')
  const query = useQuery(documentReviewQueueQuery(manager, page ?? 0, PAGE_SIZE, allowed && page !== undefined))
  const updatePage = (next: number) => setParams(next === 0 ? new URLSearchParams() : new URLSearchParams({ page: String(next) }))

  return <section className="mx-auto max-w-[90rem] space-y-6">
    <header><p className="text-sm font-semibold text-muted-foreground">DOCUMENT OPERATIONS</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Document review</h1><p className="mt-2 text-muted-foreground">Review the exact immutable version returned by Meridian. This queue does not publish workload totals.</p></header>
    {page === undefined ? <Alert variant="warning"><FileSearch2 /><AlertTitle>Invalid page</AlertTitle><AlertDescription>Use a non-negative queue page.</AlertDescription></Alert> : null}
    {query.isPending && page !== undefined ? <div role="status" className="flex min-h-48 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading review work…</div> : null}
    {query.isError && !query.data ? <QueryErrorPanel error={query.error} resource="document queue" onRetry={() => void query.refetch()} /> : null}
    {query.data ? <>
      {query.data.length === 0 ? <div className="rounded-lg border bg-card p-8 text-center"><FileSearch2 className="mx-auto size-8 text-muted-foreground" /><h2 className="mt-3 text-lg font-semibold">No documents on this page</h2><p className="mt-1 text-sm text-muted-foreground">Return to the previous page or wait for new evidence.</p></div> : <div className="overflow-x-auto rounded-lg border bg-card" tabIndex={0} aria-label="Document review items"><table className="w-full min-w-[62rem] text-left text-sm"><caption className="sr-only">Documents awaiting review</caption><thead className="bg-muted/70 text-xs uppercase text-muted-foreground"><tr><th className="px-4 py-3" scope="col">Document</th><th className="px-4 py-3" scope="col">Application</th><th className="px-4 py-3" scope="col">Uploaded</th><th className="px-4 py-3" scope="col">Uploader</th><th className="px-4 py-3" scope="col">Status</th><th className="px-4 py-3" scope="col"><span className="sr-only">Action</span></th></tr></thead><tbody className="divide-y">{query.data.map((item) => <tr key={item.checklistItemId}><td className="px-4 py-4"><p className="font-semibold">{humanizeKnownValue(item.documentType)}</p><code className="mt-1 block text-xs text-muted-foreground">Version {item.currentVersionId}</code></td><td className="px-4 py-4"><code className="text-xs">{item.loanApplicationId}</code></td><td className="px-4 py-4 whitespace-nowrap">{formatTimestamp(item.uploadedAt)}</td><td className="px-4 py-4">{humanizeKnownValue(item.uploaderActorType)}</td><td className="px-4 py-4">{humanizeKnownValue(item.reviewStatus)}</td><td className="px-4 py-4 text-right"><Button asChild variant="link"><Link aria-label={`Open ${humanizeKnownValue(item.documentType)} review`} to={`/staff/applications/${item.loanApplicationId}/documents?checklistItemId=${item.checklistItemId}&documentVersionId=${item.currentVersionId}`}>Open review <ExternalLink /></Link></Button></td></tr>)}</tbody></table></div>}
      <nav aria-label="Document queue pages" className="flex items-center justify-between rounded-lg border bg-card p-4"><p className="text-sm text-muted-foreground">Page {(page ?? 0) + 1}</p><div className="flex gap-2"><Button variant="outline" disabled={(page ?? 0) === 0} onClick={() => updatePage((page ?? 0) - 1)}><ChevronLeft /> Previous</Button><Button variant="outline" disabled={query.data.length < PAGE_SIZE} onClick={() => updatePage((page ?? 0) + 1)}>Next <ChevronRight /></Button></div></nav>
    </> : null}
  </section>
}
