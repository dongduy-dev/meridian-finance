import { ConfirmationDialog, ConfirmationDialogCancel, ConfirmationDialogTitle, ConfirmationDialogDescription, ConfirmationDialogFooter } from '@/components/ui/confirmation-dialog'
import { operatorErrorMessage } from '@/lib/api/operator-error-message'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { useState } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import {
  decideOperationIdentity,
  digestOperationPayload,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
  UnresolvedOperationConflictError,
} from '@/lib/operation/unresolved-operation'
import { staffCorrectionKeys } from '@/features/staff-corrections/api/queries'
import type { StaffDocumentItem } from '../api/contracts'
import { staffDocumentKeys } from '../api/queries'
import { reviewDocument, type ReviewDocumentInput } from '../api/staff-documents-api'

type Outcome = ReviewDocumentInput['outcome']
type Props = { manager: AuthSessionManager; loanApplicationId: string; item: StaffDocumentItem; canWaive: boolean; stale: boolean }

export function DocumentReviewForm({ manager, loanApplicationId, item, canWaive, stale }: Props) {
  const queryClient = useQueryClient()
  const [outcome, setOutcome] = useState<Outcome>('ACCEPT_DOCUMENT')
  const [waiverReasonCode, setWaiverReasonCode] = useState('')
  const [customerInstruction, setCustomerInstruction] = useState('')
  const [restrictedStaffNotes, setRestrictedStaffNotes] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [status, setStatus] = useState<OperationStatus>('DRAFT')
  const [operation, setOperation] = useState<{ id: string; signature: string }>()
  const [error, setError] = useState<ApiError | Error>()
  const version = item.currentVersion
  const semanticPayload = { outcome, waiverReasonCode, customerInstruction, restrictedStaffNotes, version: version?.documentVersionId }
  const signature = JSON.stringify(semanticPayload)
  const recoveryResource = `${loanApplicationId}:${item.checklistItemId}`

  const openConfirmation = async () => {
    if (!version || stale) return
    if (outcome === 'WAIVE_DOCUMENT' && !waiverReasonCode) return
    if (outcome === 'REQUEST_REPLACEMENT' && !customerInstruction.trim()) return
    const digest = await digestOperationPayload(semanticPayload)
    const decision = decideOperationIdentity('DOCUMENT_REVIEW', recoveryResource, digest)
    if (decision.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation(undefined)
      setStatus('RESULT_UNKNOWN')
      setError(new UnresolvedOperationConflictError())
      return
    }
    setOperation((current) => current?.signature === signature
      ? current
      : { id: decision.operationId, signature })
    setError(undefined)
    setConfirming(true)
  }

  const closeConfirmation = () => {
    setConfirming(false)
    queueMicrotask(() => document.getElementById('review-final-details')?.focus())
  }

  const submit = async () => {
    if (!version || !operation) return
    setStatus('IN_FLIGHT'); setError(undefined)
    const input: ReviewDocumentInput = {
      reviewRequestId: operation.id,
      documentVersionId: version.documentVersionId,
      outcome,
      ...(outcome === 'WAIVE_DOCUMENT' ? { waiverReasonCode } : {}),
      ...(outcome === 'REQUEST_REPLACEMENT' ? {
        correctionReasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED',
        customerInstruction: customerInstruction.trim(),
      } : {}),
      ...(restrictedStaffNotes.trim() ? { restrictedStaffNotes: restrictedStaffNotes.trim() } : {}),
    }
    try {
      await reviewDocument(manager, loanApplicationId, item.checklistItemId, input)
      setStatus('RECONCILING')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: staffDocumentKeys.all }),
        queryClient.invalidateQueries({ queryKey: staffCorrectionKeys.all }),
      ])
      setRestrictedStaffNotes(''); setCustomerInstruction(''); setStatus('RESOLVED'); setConfirming(false)
      removeUnresolvedOperation('DOCUMENT_REVIEW', recoveryResource)
    } catch (caught) {
      setError(caught as Error)
      if (caught instanceof NetworkError) {
        setStatus('RESULT_UNKNOWN')
        saveUnresolvedOperation({
          type: 'DOCUMENT_REVIEW', resource: recoveryResource,
          operationId: operation.id,
          payloadDigest: await digestOperationPayload(semanticPayload),
          unresolvedAt: new Date().toISOString(),
        })
      }
      else {
        setStatus('BLOCKED')
        removeUnresolvedOperation('DOCUMENT_REVIEW', recoveryResource)
        if (caught instanceof ApiError && [
          'STALE_DOCUMENT_VERSION', 'DOCUMENT_ALREADY_REVIEWED',
        ].includes(caught.errorCode)) {
          await queryClient.invalidateQueries({ queryKey: staffDocumentKeys.case(loanApplicationId) })
        }
      }
    }
  }

  if (!version) return null
  const invalid = stale || (outcome === 'WAIVE_DOCUMENT' && !waiverReasonCode)
    || (outcome === 'REQUEST_REPLACEMENT' && !customerInstruction.trim())
  return <div className="space-y-4 rounded-lg border bg-card p-5">
    <div><h2 className="text-xl font-semibold">Review outcome</h2><p className="text-sm text-muted-foreground">This review applies to the exact document version shown below.</p></div>
    <fieldset className="grid gap-2"><legend className="mb-2 text-sm font-semibold">Outcome</legend>{(['ACCEPT_DOCUMENT', ...(canWaive ? ['WAIVE_DOCUMENT'] : []), 'REQUEST_REPLACEMENT'] as Outcome[]).map((value) => <label key={value} className="flex min-h-11 items-center gap-3 rounded-md border p-3"><input type="radio" name="outcome" value={value} checked={outcome === value} onChange={() => { setOutcome(value); setStatus('DRAFT') }} /> {value === 'ACCEPT_DOCUMENT' ? 'Accept document' : value === 'WAIVE_DOCUMENT' ? 'Waive requirement' : 'Request replacement'}</label>)}</fieldset>
    {outcome === 'WAIVE_DOCUMENT' ? <label className="grid gap-2 text-sm font-semibold">Waiver reason<select className="min-h-11 rounded-md border bg-background px-3 font-normal" value={waiverReasonCode} onChange={(event) => setWaiverReasonCode(event.target.value)}><option value="">Select a reason</option><option value="EVIDENCE_SATISFIED_BY_VERIFIED_SOURCE">Evidence satisfied by verified source</option><option value="DOCUMENT_NOT_APPLICABLE">Document not applicable</option></select></label> : null}
    {outcome === 'REQUEST_REPLACEMENT' ? <label className="grid gap-2 text-sm font-semibold">Customer-visible instruction<textarea className="min-h-28 rounded-md border bg-background p-3 font-normal" maxLength={500} value={customerInstruction} onChange={(event) => setCustomerInstruction(event.target.value)} /><span className="text-xs font-normal text-muted-foreground">Visible to the Customer. Maximum 500 characters.</span></label> : null}
    <label className="grid gap-2 text-sm font-semibold">Restricted Staff notes <span className="font-normal text-muted-foreground">Optional · internal only</span><textarea className="min-h-24 rounded-md border bg-background p-3 font-normal" maxLength={2000} value={restrictedStaffNotes} onChange={(event) => setRestrictedStaffNotes(event.target.value)} /></label>
    {stale ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Selected evidence is stale</AlertTitle><AlertDescription>Refresh or select the latest current version before submitting a review.</AlertDescription></Alert> : null}
    {status !== 'DRAFT' ? <OperationStatusPanel status={status} /> : null}
    {error ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Review not confirmed</AlertTitle><AlertDescription>{error instanceof UnresolvedOperationConflictError ? error.message : operatorErrorMessage(error, 'Meridian could not confirm the review. Check the latest document version and recovery guidance before trying again.')}{error instanceof ApiError && error.requestId ? <RequestCorrelation requestId={error.requestId} /> : null}</AlertDescription></Alert> : null}
    <Button id="review-final-details" disabled={invalid || status === 'IN_FLIGHT' || status === 'RECONCILING'} onClick={() => void openConfirmation()}>Review document decision</Button>
    {confirming ? <ConfirmationDialog onDismiss={closeConfirmation}><div><ConfirmationDialogTitle className="text-xl font-semibold">Confirm document review</ConfirmationDialogTitle><ConfirmationDialogDescription className="mt-1 text-sm text-muted-foreground">This review applies to the exact evidence shown below.</ConfirmationDialogDescription></div><dl className="grid gap-3 text-sm"><div><dt className="text-muted-foreground">Document type</dt><dd className="font-semibold">{humanizeKnownValue(item.documentType)}</dd></div><div><dt className="text-muted-foreground">Version</dt><dd className="break-all font-semibold">{version.versionNumber}</dd></div><div><dt className="text-muted-foreground">Filename</dt><dd className="font-semibold">{version.originalFilename}</dd></div><div><dt className="text-muted-foreground">Outcome</dt><dd className="font-semibold">{humanizeKnownValue(outcome)}</dd></div></dl><ConfirmationDialogFooter><ConfirmationDialogCancel asChild><Button variant="outline">Cancel</Button></ConfirmationDialogCancel><Button autoFocus onClick={() => void submit()} disabled={status === 'IN_FLIGHT'}><CheckCircle2 /> Record document review</Button></ConfirmationDialogFooter></ConfirmationDialog> : null}
  </div>
}
