import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { ApplicationWorkspaceShell } from '@/components/operations/ApplicationWorkspaceShell'
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

const decisionLabels: Record<AssistedOfferDecision, string> = {
  ACCEPT: 'Accept',
  DECLINE: 'Decline',
}

const offerStatusLabels: Record<string, string> = {
  PENDING: 'Awaiting Customer response',
  ACCEPTED: 'Accepted by Customer',
  DECLINED: 'Declined by Customer',
  EXPIRED: 'Expired',
}

function decisionLabel(value: AssistedOfferDecision | null) {
  return value ? decisionLabels[value] : 'Decision unavailable'
}

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
      setMessage('Signed offer response uploaded. Review it before recording the Customer decision.')
    } catch (value) {
      setError(value instanceof Error ? value : new NetworkError())
      setMessage('Meridian did not retry the upload automatically. Refresh before selecting another file.')
      await refresh()
    } finally { setUploading(false) }
  }

  const execute = async (payload: unknown, requestId?: string) => {
    const parsed = assistedOfferCommandPayloadSchema.safeParse(payload)
    if (!parsed.success) { setMessage('The saved offer response cannot be verified. Check the latest offer before continuing.'); return }
    setCommanding(true); setError(undefined); setMessage(undefined)
    const digest = await digestOperationPayload(parsed.data)
    const identity = requestId
      ? { kind: 'REUSE_EXISTING' as const, operationId: requestId }
      : decideOperationIdentity(operationType, loanApplicationId, digest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setCommanding(false); setMessage('A previous offer response is still unconfirmed. Retry that same response before recording another.'); return
    }
    try {
      await recordAssistedOfferResponse(manager, parsed.data, identity.operationId)
      removeUnresolvedOperation(operationType, loanApplicationId)
      setConfirmed(false)
      await refresh()
      setMessage('The Customer decision was recorded and the latest offer details are loaded.')
    } catch (value) {
      const commandError = value instanceof Error ? value : new NetworkError()
      if (commandError instanceof ApiError && commandError.status < 500) {
        removeUnresolvedOperation(operationType, loanApplicationId)
        setError(commandError); setMessage('The action was rejected. Review the reason and the latest offer information before trying again.')
      } else {
        saveUnresolvedOperation({
          type: operationType, resource: loanApplicationId, operationId: identity.operationId,
          payloadDigest: digest, semanticPayload: parsed.data, unresolvedAt: new Date().toISOString(),
        })
        setError(commandError)
        setMessage('The offer-response result is not confirmed. Meridian did not submit it again automatically; refresh and retry the exact same action.')
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

  return <ApplicationWorkspaceShell
    actor={state.status === 'authenticated' ? state.actor : undefined}
    context={{ source: 'feature', facts: {
      loanApplicationId: data.loanApplicationId, applicationNumber: data.applicationNumber,
      applicationStatus: data.applicationStatus, productCode: data.productCode,
      submittedAt: data.submittedAt, originationChannel: data.originationChannel,
    } }}
    updatedAt={query.dataUpdatedAt} refreshing={query.isFetching} stale={query.isStale}
    onRefresh={() => void refresh()}
  >
    <div><h2 className="text-xl font-semibold">Customer offer response</h2><p className="mt-2 text-sm text-muted-foreground">The Customer makes and signs the decision. Staff records the response shown on the signed form.</p></div>
    {message ? <Alert variant={error ? 'warning' : 'success'}>{error ? <AlertTriangle /> : <CheckCircle2 />}<AlertTitle>{error ? 'Attention required' : 'Operation updated'}</AlertTitle><AlertDescription>{message}</AlertDescription></Alert> : null}
    <Card><CardHeader><CardTitle>Customer offer</CardTitle></CardHeader><CardContent className="grid gap-4 sm:grid-cols-2"><details><summary className="cursor-pointer text-sm text-muted-foreground">Technical offer details</summary><p className="mt-2 text-xs text-muted-foreground">Offer ID</p><code className="break-all text-xs">{data.approvedOffer.approvedOfferId}</code></details><div><p className="text-sm text-muted-foreground">Status</p><p className="font-semibold">{offerStatusLabels[data.approvedOffer.status] ?? 'Offer status unavailable'}</p></div><div><p className="text-sm text-muted-foreground">Approved principal</p><p className="font-semibold">{formatVnd(data.approvedOffer.approvedPrincipal)}</p></div><div><p className="text-sm text-muted-foreground">Term</p><p className="font-semibold">{data.approvedOffer.approvedTermMonths} months</p></div><div><p className="text-sm text-muted-foreground">Total repayment</p><p className="font-semibold">{formatVnd(data.approvedOffer.totalRepaymentAmount)}</p></div><div><p className="text-sm text-muted-foreground">Expires</p><p className="font-semibold">{formatTimestamp(data.approvedOffer.expiresAt)}</p></div></CardContent></Card>
    <Card><CardHeader><CardTitle>Signed Customer Offer Response Form</CardTitle><p className="text-sm text-muted-foreground">Upload the signed form for this offer as a PDF, JPEG, or PNG. Replacing it keeps the previous version in the document history.</p></CardHeader><CardContent className="space-y-4"><label className="block text-sm font-semibold">Customer decision on signed form<select className="mt-2 min-h-11 w-full rounded-md border bg-background px-3" value={decision} onChange={(event) => { setDecision(event.target.value as AssistedOfferDecision); setConfirmed(false) }} disabled={!actionAvailable}><option value="ACCEPT">Accept</option><option value="DECLINE">Decline</option></select></label><label className="block text-sm font-semibold">Signed form<input className="mt-2 block w-full text-sm" type="file" accept="application/pdf,image/jpeg,image/png" onChange={(event) => setFile(event.target.files?.[0])} /></label><Button variant="outline" disabled={!actionAvailable || !canUpload || !file || uploading} onClick={() => void upload()}>{uploading ? <Spinner /> : null}{data.evidence ? 'Replace signed evidence' : 'Upload signed evidence'}</Button>{data.evidence ? <p className="text-sm">Current document version {data.evidence.versionNumber} · {data.evidence.detectedMimeType} · uploaded {formatTimestamp(data.evidence.uploadedAt)} · records {decisionLabel(data.evidence.declaredOfferDecision)}</p> : <p className="text-sm text-muted-foreground">No current signed evidence.</p>}</CardContent></Card>
    <Card><CardHeader><CardTitle>Record Customer response</CardTitle></CardHeader><CardContent className="space-y-4"><label className="flex items-start gap-3 text-sm"><input className="mt-1" type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /><span>{decision === 'ACCEPT' ? 'I confirm this signed form records the Customer\'s acceptance of this exact offer.' : 'I confirm this signed form records the Customer\'s decision to decline this exact offer.'}</span></label><Button disabled={!actionAvailable || !evidenceMatches || !confirmed || commanding || Boolean(unresolved)} onClick={() => void execute({ loanApplicationId, expectedApprovedOfferId: data.approvedOffer.approvedOfferId, action: decision, evidenceDocumentVersionId: data.evidence?.documentVersionId })}>{commanding ? <Spinner /> : null}{decision === 'ACCEPT' ? 'Record Customer acceptance' : 'Record Customer decline'}</Button>{!evidenceMatches ? <p className="text-sm text-warning">Current evidence must target this offer and record the selected decision.</p> : null}{unresolved ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Offer response result unknown</AlertTitle><AlertDescription><p>Meridian could not confirm the previous offer response. Retry this exact same action before recording a different response.</p><Button className="mt-3" variant="outline" disabled={commanding} onClick={() => void execute(unresolved.semanticPayload, unresolved.operationId)}>Retry this response</Button></AlertDescription></Alert> : null}</CardContent></Card>
  </ApplicationWorkspaceShell>
}
