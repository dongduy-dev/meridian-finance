import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import {
  decideOperationIdentity, digestOperationPayload, findUnresolvedOperation,
  removeUnresolvedOperation, saveUnresolvedOperation,
} from '@/lib/operation/unresolved-operation'
import { assistedOfferCommandPayloadSchema, type AssistedOfferDecision } from '../api/contracts'
import { assistedOfferResponseCaseQuery } from '../api/queries'
import {
  recordAssistedOfferResponse, uploadOfferResponseEvidence,
} from '../api/staff-offer-response-api'

const operationType = 'ASSISTED_OFFER_RESPONSE' as const

export function StaffOfferResponseWorkspacePage() {
  const { manager, state } = useAuth()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const authorized = state.status === 'authenticated'
    && hasPermission(state.actor, 'loan:offer:respond:staff')
    && hasRole(state.actor, 'LOAN_OFFICER')
  const canUpload = state.status === 'authenticated'
    && hasPermission(state.actor, 'document:upload:assisted-action')
  const query = useQuery(assistedOfferResponseCaseQuery(manager, loanApplicationId, validId && authorized))
  const [decision, setDecision] = useState<AssistedOfferDecision>('ACCEPT')
  const [file, setFile] = useState<File>()
  const [uploading, setUploading] = useState(false)
  const [commanding, setCommanding] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [message, setMessage] = useState<string>()
  const [error, setError] = useState<Error>()
  const unresolved = validId ? findUnresolvedOperation(operationType, loanApplicationId) : undefined

  const refresh = async () => {
    await query.refetch().catch(() => undefined)
  }

  const upload = async () => {
    const data = query.data
    if (!data || !file || !canUpload || data.workState !== 'ACTION_AVAILABLE') return
    setUploading(true); setError(undefined); setMessage(undefined)
    try {
      await uploadOfferResponseEvidence(
        manager, loanApplicationId, data.approvedOffer.approvedOfferId, decision, file,
        crypto.randomUUID(), data.evidence?.documentVersionId,
      )
      setFile(undefined); setConfirmed(false)
      await refresh()
      setMessage('Signed Customer Offer Response evidence uploaded. Review it before recording the decision.')
    } catch (value) {
      setError(value instanceof Error ? value : new NetworkError())
      setMessage('The upload was not retried automatically. Refresh before selecting another file or request identity.')
      await refresh()
    } finally { setUploading(false) }
  }

  const execute = async (payload: unknown, requestId?: string) => {
    const parsed = assistedOfferCommandPayloadSchema.safeParse(payload)
    if (!parsed.success) { setMessage('The retained command cannot be verified.'); return }
    setCommanding(true); setError(undefined); setMessage(undefined)
    const digest = await digestOperationPayload(parsed.data)
    const identity = requestId
      ? { kind: 'REUSE_EXISTING' as const, operationId: requestId }
      : decideOperationIdentity(operationType, loanApplicationId, digest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setCommanding(false); setMessage('A different offer response is unresolved. Retry that exact command first.'); return
    }
    try {
      await recordAssistedOfferResponse(manager, parsed.data, identity.operationId)
      removeUnresolvedOperation(operationType, loanApplicationId)
      setConfirmed(false)
      await refresh()
      setMessage('The evidenced Customer decision was recorded and authoritative state was refreshed.')
    } catch (value) {
      const commandError = value instanceof Error ? value : new NetworkError()
      if (commandError instanceof ApiError && commandError.status < 500) {
        removeUnresolvedOperation(operationType, loanApplicationId)
        setError(commandError); setMessage('The backend rejected the command. Authoritative state was refreshed.')
      } else {
        saveUnresolvedOperation({
          type: operationType, resource: loanApplicationId, operationId: identity.operationId,
          payloadDigest: digest, semanticPayload: parsed.data, unresolvedAt: new Date().toISOString(),
        })
        setError(commandError)
        setMessage('The command result is unknown. No POST was retried automatically; use the exact retry after refresh.')
      }
      await refresh()
    } finally { setCommanding(false) }
  }

  if (!validId) return <section><h1 data-route-heading tabIndex={-1}>Offer response unavailable</h1></section>
  if (query.isPending) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading Customer offer response</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border"><Spinner /> Loading exact approved offer…</div></section>
  if (query.isError && !query.data) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Customer offer response</h1><QueryErrorPanel error={query.error} resource="case" onRetry={() => void refresh()} /></section>
  if (!query.data) return null
  const data = query.data
  const evidenceMatches = data.evidence?.declaredOfferDecision === decision
    && data.evidence.targetId === data.approvedOffer.approvedOfferId
  const actionAvailable = data.workState === 'ACTION_AVAILABLE'

  return <section className="mx-auto max-w-5xl space-y-6">
    <div className="flex justify-between gap-3"><Link className="font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}`}>← Application case</Link><Button variant="outline" onClick={() => void refresh()} disabled={query.isFetching}><RefreshCw />Refresh</Button></div>
    <header className="rounded-lg border bg-card p-6"><p className="text-sm font-semibold text-muted-foreground">EVIDENCED CUSTOMER DECISION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold">{data.applicationNumber}</h1><p className="mt-2 text-sm text-muted-foreground">The Customer makes and signs the decision. You are recording actor, not decision subject.</p></header>
    {message ? <Alert variant={error ? 'warning' : 'success'}>{error ? <AlertTriangle /> : <CheckCircle2 />}<AlertTitle>{error ? 'Attention required' : 'Operation updated'}</AlertTitle><AlertDescription>{message}</AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Exact pending approved offer</CardTitle></CardHeader><CardContent className="grid gap-4 sm:grid-cols-2"><div><p className="text-sm text-muted-foreground">Offer ID</p><p className="break-all font-semibold">{data.approvedOffer.approvedOfferId}</p></div><div><p className="text-sm text-muted-foreground">Status</p><p className="font-semibold">{data.approvedOffer.status}</p></div><div><p className="text-sm text-muted-foreground">Approved principal</p><p className="font-semibold">{formatVnd(data.approvedOffer.approvedPrincipal)}</p></div><div><p className="text-sm text-muted-foreground">Term</p><p className="font-semibold">{data.approvedOffer.approvedTermMonths} months</p></div><div><p className="text-sm text-muted-foreground">Total repayment</p><p className="font-semibold">{formatVnd(data.approvedOffer.totalRepaymentAmount)}</p></div><div><p className="text-sm text-muted-foreground">Expires</p><p className="font-semibold">{formatTimestamp(data.approvedOffer.expiresAt)}</p></div></CardContent></Card>
    <Card><CardHeader><CardTitle>Signed Customer Offer Response Form</CardTitle><p className="text-sm text-muted-foreground">Upload PDF, JPEG, or PNG evidence for this exact offer. Replacement creates another immutable version.</p></CardHeader><CardContent className="space-y-4"><label className="block text-sm font-semibold">Customer decision on form<select className="mt-2 min-h-11 w-full rounded-md border bg-background px-3" value={decision} onChange={(event) => { setDecision(event.target.value as AssistedOfferDecision); setConfirmed(false) }} disabled={!actionAvailable}><option value="ACCEPT">ACCEPT</option><option value="DECLINE">DECLINE</option></select></label><label className="block text-sm font-semibold">Signed form<input className="mt-2 block w-full text-sm" type="file" accept="application/pdf,image/jpeg,image/png" onChange={(event) => setFile(event.target.files?.[0])} /></label><Button variant="outline" disabled={!actionAvailable || !canUpload || !file || uploading} onClick={() => void upload()}>{uploading ? <Spinner /> : null}{data.evidence ? 'Replace signed evidence' : 'Upload signed evidence'}</Button>{data.evidence ? <p className="text-sm">Current immutable version {data.evidence.versionNumber} · {data.evidence.detectedMimeType} · uploaded {formatTimestamp(data.evidence.uploadedAt)} · declares {data.evidence.declaredOfferDecision}</p> : <p className="text-sm text-muted-foreground">No current signed evidence.</p>}</CardContent></Card>
    <Card><CardHeader><CardTitle>Record evidenced Customer response</CardTitle></CardHeader><CardContent className="space-y-4"><label className="flex items-start gap-3 text-sm"><input className="mt-1" type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /><span>I confirm this signed form records the Customer&apos;s {decision} decision for this exact offer.</span></label><Button disabled={!actionAvailable || !evidenceMatches || !confirmed || commanding || Boolean(unresolved)} onClick={() => void execute({ loanApplicationId, expectedApprovedOfferId: data.approvedOffer.approvedOfferId, action: decision, evidenceDocumentVersionId: data.evidence?.documentVersionId })}>{commanding ? <Spinner /> : null}Record Customer {decision}</Button>{!evidenceMatches ? <p className="text-sm text-warning">Current evidence must target this offer and declare the selected decision.</p> : null}{unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Offer response result unknown</AlertTitle><AlertDescription><p>No automatic POST retry occurred.</p><Button className="mt-3" variant="outline" disabled={commanding} onClick={() => void execute(unresolved.semanticPayload, unresolved.operationId)}>Retry exact command</Button></AlertDescription></Alert> : null}</CardContent></Card>
  </section>
}
