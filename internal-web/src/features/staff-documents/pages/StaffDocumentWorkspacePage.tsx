import { useQuery } from '@tanstack/react-query'
import { FileClock, RefreshCw } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { formatTimestamp } from '@/lib/format/presentation'
import { findUnresolvedOperation, removeUnresolvedOperation } from '@/lib/operation/unresolved-operation'
import { staffDocumentCaseQuery } from '../api/queries'
import { DocumentContentViewer } from '../components/DocumentContentViewer'
import { DocumentReviewForm } from '../components/DocumentReviewForm'

export function StaffDocumentWorkspacePage() {
  const { manager, state } = useAuth()
  const { loanApplicationId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const allowed = state.status === 'authenticated' && hasPermission(state.actor, 'document:review')
  const query = useQuery(staffDocumentCaseQuery(manager, loanApplicationId, validId && allowed))
  const selectedItemId = params.get('checklistItemId')
  const selectedVersionId = params.get('documentVersionId')
  const selectedItem = query.data?.items.find((item) => item.checklistItemId === selectedItemId)
  const selectedVersion = selectedItem?.versionHistory.find((version) => version.documentVersionId === selectedVersionId)
  const stale = Boolean(selectedVersionId && selectedItem?.currentVersion?.documentVersionId !== selectedVersionId)
  const canWaive = state.status === 'authenticated' && hasPermission(state.actor, 'document:waive')
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')

  useEffect(() => {
    query.data?.items.forEach((item) => {
      if (!['ACCEPTED', 'WAIVED', 'REPLACEMENT_REQUESTED'].includes(item.evidenceStatus)) return
      const resource = `${loanApplicationId}:${item.checklistItemId}`
      if (findUnresolvedOperation('DOCUMENT_REVIEW', resource)) {
        removeUnresolvedOperation('DOCUMENT_REVIEW', resource)
      }
    })
  }, [loanApplicationId, query.data])

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Documents unavailable</h1><Alert variant="warning"><FileClock /><AlertTitle>Invalid application identifier</AlertTitle></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application documents</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading document evidence…</div></section>
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application documents</h1><QueryErrorPanel error={query.error} resource="document evidence" onRetry={() => void query.refetch()} /></section>
  if (!query.data) return null
  const data = query.data
  const select = (itemId: string, versionId: string) => setParams(new URLSearchParams({ checklistItemId: itemId, documentVersionId: versionId }))

  return <section className="mx-auto max-w-7xl space-y-6">
    <header className="rounded-lg border bg-card p-5"><p className="text-sm font-semibold text-muted-foreground">DOCUMENT CASE</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Application documents</h1><p className="mt-2 break-all text-sm text-muted-foreground">{data.loanApplicationId} · {humanizeKnownValue(data.applicationStatus)}</p><div className="mt-4 flex flex-wrap gap-2"><Button variant="outline" onClick={() => void query.refetch()} disabled={query.isFetching}>{query.isFetching ? <Spinner /> : <RefreshCw />} Refresh evidence</Button>{canReadCase ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}`}>Application overview</Link></Button> : null}{state.status === 'authenticated' && hasPermission(state.actor, 'loan:correction:staff') ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}/corrections`}>Corrections</Link></Button> : null}</div></header>
    <div className="grid gap-6 lg:grid-cols-[22rem_1fr]">
      <aside className="space-y-3"><Card><CardHeader><CardTitle>Checklist evidence</CardTitle><p className="text-sm text-muted-foreground">{humanizeKnownValue(data.checklistStage)} · Upload {data.uploadComplete ? 'complete' : 'incomplete'} · Processing {data.processingReady ? 'ready' : 'not ready'}</p></CardHeader><CardContent className="space-y-2">{data.items.map((item) => <div key={item.checklistItemId} className="rounded-md border p-3"><p className="font-semibold">{humanizeKnownValue(item.documentType)}</p><p className="mt-1 text-xs text-muted-foreground">{humanizeKnownValue(item.requirementStatus)} · {humanizeKnownValue(item.evidenceStatus)}</p>{item.currentVersion ? <Button className="mt-3 w-full" variant={item.checklistItemId === selectedItemId ? 'secondary' : 'outline'} onClick={() => select(item.checklistItemId, item.currentVersion!.documentVersionId)}>Review version {item.currentVersion.versionNumber}</Button> : <p className="mt-3 text-sm text-warning">No current upload</p>}</div>)}</CardContent></Card></aside>
      <div className="min-w-0 space-y-5">{selectedItem && selectedVersion ? <>
        {stale ? <Alert variant="warning"><FileClock /><AlertTitle>Historical version selected</AlertTitle><AlertDescription>The authoritative current version is different. This version remains visible for evidence history, but review submission is disabled.<Button className="mt-3" variant="outline" onClick={() => selectedItem.currentVersion && select(selectedItem.checklistItemId, selectedItem.currentVersion.documentVersionId)}>Open current version</Button></AlertDescription></Alert> : null}
        <Card><CardHeader><CardTitle>{selectedVersion.originalFilename}</CardTitle><p className="break-all text-sm text-muted-foreground">Version {selectedVersion.versionNumber} · {selectedVersion.documentVersionId}</p></CardHeader><CardContent className="space-y-4"><dl className="grid gap-3 text-sm sm:grid-cols-3"><div><dt className="text-muted-foreground">Detected type</dt><dd className="font-semibold">{selectedVersion.detectedMimeType}</dd></div><div><dt className="text-muted-foreground">Size</dt><dd className="font-semibold">{selectedVersion.byteSize.toLocaleString()} bytes</dd></div><div><dt className="text-muted-foreground">Uploaded</dt><dd className="font-semibold">{formatTimestamp(selectedVersion.uploadedAt)}</dd></div></dl><DocumentContentViewer manager={manager} loanApplicationId={loanApplicationId} checklistItemId={selectedItem.checklistItemId} documentVersionId={selectedVersion.documentVersionId} filename={selectedVersion.originalFilename} /></CardContent></Card>
        {!stale && selectedItem.evidenceStatus === 'AWAITING_REVIEW' && selectedItem.currentVersion?.documentVersionId === selectedVersion.documentVersionId ? <DocumentReviewForm manager={manager} loanApplicationId={loanApplicationId} item={selectedItem} canWaive={canWaive} stale={stale} /> : null}
        <Card><CardHeader><CardTitle>Immutable history</CardTitle></CardHeader><CardContent><ol className="space-y-3">{selectedItem.versionHistory.map((version) => <li key={version.documentVersionId} className="rounded-md border p-3"><button className="text-left font-semibold text-primary hover:underline" onClick={() => select(selectedItem.checklistItemId, version.documentVersionId)}>Version {version.versionNumber} · {version.originalFilename}</button><p className="mt-1 text-xs text-muted-foreground">{formatTimestamp(version.uploadedAt)}</p><ul className="mt-2 text-sm">{selectedItem.reviewHistory.filter((review) => review.documentVersionId === version.documentVersionId).map((review, index) => <li key={`${review.documentVersionId}-${review.decidedAt}-${index}`}>{humanizeKnownValue(review.outcome)} · {formatTimestamp(review.decidedAt)}{review.waiverReasonCode ? ` · ${humanizeKnownValue(review.waiverReasonCode)}` : ''}</li>)}</ul></li>)}</ol></CardContent></Card>
      </> : <div className="rounded-lg border bg-card p-8 text-center"><FileClock className="mx-auto size-8 text-muted-foreground" /><h2 className="mt-3 text-lg font-semibold">Select current evidence</h2><p className="mt-1 text-sm text-muted-foreground">Choose a checklist item to view its exact immutable version and review history.</p></div>}</div>
    </div>
  </section>
}
