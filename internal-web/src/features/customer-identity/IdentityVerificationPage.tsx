import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { ApiError } from '@/lib/api/errors'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { DocumentContentViewer } from '@/features/staff-documents/components/DocumentContentViewer'
import { identityQueue, identityDetail, decideIdentity, rejectionReasons } from './api'

const decisionErrors: Record<string, { title: string; description: string }> = {
  IDENTITY_REFERENCE_MISMATCH: { title: 'Identity Reference does not match', description: 'The reference entered does not match the Customer profile. Check the identity document and try again.' },
  INVALID_IDENTITY_VERIFICATION_REQUEST: { title: 'Check the identity review details', description: 'Enter the reference shown on the document or select a controlled rejection reason, then try again.' },
  VALIDATION_FAILED: { title: 'Check the identity review details', description: 'Check the required review fields and try again.' },
  IDENTITY_VERIFICATION_EVIDENCE_STALE: { title: 'Identity evidence has changed', description: 'The evidence or Customer identity details have changed. Submit current evidence for review.' },
  IDENTITY_VERIFICATION_ALREADY_COMPLETED: { title: 'Identity review already completed', description: 'Another decision has completed this review. Check the refreshed verification outcome.' },
}

export function IdentityVerificationPage() {
  const { state } = useAuth()
  const { verificationId } = useParams()
  const allowed = state.status === 'authenticated' && hasPermission(state.actor, 'customer:identity:verify')
  if (!allowed) return <p role="alert">Customer identity verification authority is required.</p>
  return <IdentityWorkspace key={`${state.actor.userId}:${state.epoch}:${verificationId ?? 'queue'}`} verificationId={verificationId} />
}
function IdentityWorkspace({ verificationId }: { verificationId?: string }) {
  const { manager, state } = useAuth()
  const client = useQueryClient()
  const [page, setPage] = useState(0)
  const queue = useQuery({ queryKey: ['customer-identity', 'queue', page], queryFn: () => identityQueue(manager, page), enabled: !verificationId })
  const detail = useQuery({ queryKey: ['customer-identity', verificationId], queryFn: () => identityDetail(manager, verificationId!), enabled: Boolean(verificationId) })
  const [reference, setReference] = useState('')
  const [reason, setReason] = useState('UNREADABLE_EVIDENCE')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string>()
  const [decisionError, setDecisionError] = useState<{ title: string; description: string }>()
  const [uncertain, setUncertain] = useState(false)
  const decide = async (verify: boolean) => {
    const v = detail.data
    if (!v?.evidence || busy) return
    setBusy(true); setMessage(undefined); setDecisionError(undefined)
    const presented = reference; setReference('')
    try { await decideIdentity(manager, v.verificationId, verify, v.evidence.versionId, presented, reason); setMessage('Manual identity review completed.'); await client.invalidateQueries({ queryKey: ['staff-origination'] }); await client.invalidateQueries({ queryKey: ['customer-identity', 'queue'] }) }
    catch (error) {
      if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 408 && error.errorCode !== 'UNEXPECTED_RESPONSE') {
        setUncertain(false)
        setDecisionError(decisionErrors[error.errorCode] ?? { title: 'Identity review could not be completed', description: 'Check your review authority and the refreshed verification details before trying again.' })
      } else {
        setUncertain(true); setMessage('The decision was not confirmed. Refresh its authoritative state before acting again.')
      }
    }
    finally { setReference(''); await detail.refetch(); setBusy(false) }
  }
  const v = detail.data
  const query = verificationId ? detail : queue
  return <section className="mx-auto max-w-5xl space-y-5">
    <h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Customer identity verification</h1>
    <p className="text-muted-foreground">Review exact identity evidence and attest the Customer identity. The stored Identity Reference is never revealed.</p>
    {query.isPending ? <p role="status">Loading verification…</p> : null}
    {query.isError ? <p role="alert">Verification could not be loaded.</p> : null}
    {message ? <p role="status">{message}</p> : null}
    {decisionError ? <Alert variant="destructive"><AlertTitle>{decisionError.title}</AlertTitle><AlertDescription>{decisionError.description}</AlertDescription></Alert> : null}
    <Button variant="outline" disabled={busy} onClick={() => { setReference(''); void query.refetch().then(result => { if (!result.isError) setUncertain(false) }) }}>Refresh</Button>
    {!verificationId && queue.data ? <>
      {queue.data.length === 0 ? <p>No pending identity verifications.</p> : queue.data.map(row => <article key={row.verificationId} className="rounded-lg border bg-card p-4">
        <h2 className="break-words font-semibold">{row.customerNumber} · {row.fullName}</h2><p className="text-sm">{row.source === 'CUSTOMER_DIGITAL' ? 'Customer digital evidence' : row.source === 'STAFF_ASSISTED_INTAKE' ? 'Staff intake evidence' : 'Source unavailable'} · {row.submittedAt} · {row.status === 'PENDING_REVIEW' ? 'Pending review' : 'Status unavailable'}</p>
        <Button variant="link" asChild><Link to={`/staff/customer-identity-verifications/${row.verificationId}`}>Open verification</Link></Button>
      </article>)}
      <div className="flex gap-3"><Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><Button variant="outline" disabled={queue.data.length < 25} onClick={() => setPage(page + 1)}>Next</Button></div>
    </> : null}
    {verificationId && v ? <article className="space-y-4 rounded-lg border bg-card p-5">
      <Button variant="link" asChild><Link to="/staff/customer-identity-verifications">Pending queue</Link></Button>
      <h2 className="break-words text-xl font-semibold">{v.customerNumber} · {v.fullName}</h2>
      <p>{({ PENDING_REVIEW: 'Pending review', VERIFIED: 'Verified', REJECTED: 'Rejected', SUPERSEDED: 'Superseded' } as Record<string, string>)[v.status] ?? 'Status unavailable'} · Manual Staff document review</p>
      {v.rejectionReason ? <p>{rejectionReasons[v.rejectionReason] ?? 'Reason unavailable'}</p> : null}
      {v.evidence ? <><p className="break-words">{v.evidence.filename} · Version {v.evidence.versionNumber} · {v.evidence.mimeType} · {v.evidence.byteSize} bytes</p>
        <DocumentContentViewer manager={manager} identityVerificationId={v.verificationId} filename={v.evidence.filename} buttonLabel="View identity evidence" />
      </> : null}
      {v.status === 'PENDING_REVIEW' && v.method === 'MANUAL_STAFF_DOCUMENT_REVIEW' && ['CUSTOMER_DIGITAL', 'STAFF_ASSISTED_INTAKE'].includes(v.source) && v.evidence && state.status === 'authenticated' ? <div className="space-y-4">
        <label className="grid gap-2 font-medium">Identity Reference shown on document<Input autoComplete="off" maxLength={100} value={reference} onChange={event => setReference(event.target.value)} disabled={busy || uncertain} /></label>
        <p className="text-sm text-muted-foreground">Verify attests that the document supports this Customer identity. Confirm the name and type the reference presented on the exact evidence.</p>
        <Button disabled={busy || uncertain || !reference.trim()} onClick={() => void decide(true)}>Verify identity</Button>
        <label className="grid gap-2 font-medium">Rejection reason<select value={reason} onChange={event => setReason(event.target.value)} disabled={busy || uncertain} className="min-h-11 rounded-md border bg-background p-2">{Object.entries(rejectionReasons).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
        <Button variant="destructive" disabled={busy || uncertain} onClick={() => void decide(false)}>Reject evidence</Button>
      </div> : null}
    </article> : null}
  </section>
}
