import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, FileUp, RefreshCw, ShieldAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { staffDocumentKeys } from '@/features/staff-documents/api/queries'
import { uploadStaffDocument } from '@/features/staff-documents/api/staff-documents-api'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'
import {
  decideOperationIdentity,
  digestFile,
  digestOperationPayload,
  findUnresolvedOperation,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
  UnresolvedOperationConflictError,
  type UnresolvedOperationType,
} from '@/lib/operation/unresolved-operation'
import type { StaffCorrectionCaseTask } from '../api/contracts'
import { staffCorrectionCaseQuery, staffCorrectionKeys } from '../api/queries'
import { completeStaffCorrectionTask, resubmitStaffCorrection } from '../api/staff-corrections-api'

const ALLOWED_TYPES = new Set(['application/pdf', 'image/jpeg', 'image/png'])
const MAX_SIZE = 10 * 1024 * 1024
type ActionState = { status: OperationStatus; id?: string; error?: Error }

export function StaffCorrectionWorkspacePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const [params] = useSearchParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const allowed = state.status === 'authenticated' && hasPermission(state.actor, 'loan:correction:staff')
  const canUpload = state.status === 'authenticated' && hasPermission(state.actor, 'document:upload:staff')
  const canReview = state.status === 'authenticated' && hasPermission(state.actor, 'document:review')
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const query = useQuery(staffCorrectionCaseQuery(manager, loanApplicationId, validId && allowed))
  const [files, setFiles] = useState<Record<string, File | undefined>>({})
  const [fileErrors, setFileErrors] = useState<Record<string, string | undefined>>({})
  const [actions, setActions] = useState<Record<string, ActionState>>({})
  const [confirmingResubmission, setConfirmingResubmission] = useState(false)
  const selectedTaskId = params.get('taskId')
  const resubmissionKey = `resubmit:${loanApplicationId}`

  useEffect(() => {
    const request = query.data?.correctionRequest
    if (!request) return
    request.tasks.forEach((task) => {
      const completionKey = `complete:${task.taskId}`
      const uploadKey = `upload:${task.taskId}`
      if (task.status === 'COMPLETED'
          && findUnresolvedOperation('TASK_COMPLETION', completionKey)) {
        removeUnresolvedOperation('TASK_COMPLETION', completionKey)
      }
      if ((task.status === 'COMPLETED' || task.proofState === 'SATISFIED')
          && findUnresolvedOperation('STAFF_UPLOAD', uploadKey)) {
        removeUnresolvedOperation('STAFF_UPLOAD', uploadKey)
      }
    })
    if (request.status === 'RESUBMITTED'
        && findUnresolvedOperation('STAFF_RESUBMISSION', resubmissionKey)) {
      removeUnresolvedOperation('STAFF_RESUBMISSION', resubmissionKey)
    }
  }, [query.data, resubmissionKey])

  const closeResubmissionConfirmation = () => {
    setConfirmingResubmission(false)
    queueMicrotask(() => document.getElementById('review-resubmission')?.focus())
  }

  const reconcile = async (documentRelevant = false) => {
    const work = [
      queryClient.invalidateQueries({ queryKey: staffCorrectionKeys.all }),
      queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all }),
    ]
    if (documentRelevant && canReview) work.push(queryClient.invalidateQueries({ queryKey: staffDocumentKeys.all }))
    await Promise.all(work)
  }

  const runAction = async (
    key: string,
    type: UnresolvedOperationType,
    semanticPayload: unknown,
    run: (id: string) => Promise<unknown>,
    documentRelevant = false,
  ) => {
    const payloadDigest = await digestOperationPayload(semanticPayload)
    const decision = decideOperationIdentity(type, key, payloadDigest)
    if (decision.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setActions((value) => ({ ...value, [key]: {
        status: 'RESULT_UNKNOWN', id: decision.operation.operationId,
        error: new UnresolvedOperationConflictError(),
      } }))
      return false
    }
    const id = decision.operationId
    setActions((value) => ({ ...value, [key]: { status: 'IN_FLIGHT', id } }))
    try {
      await run(id)
      setActions((value) => ({ ...value, [key]: { status: 'RECONCILING', id } }))
      await reconcile(documentRelevant)
      setActions((value) => ({ ...value, [key]: { status: 'RESOLVED', id } }))
      removeUnresolvedOperation(type, key)
      return true
    } catch (caught) {
      const error = caught as Error
      setActions((value) => ({ ...value, [key]: {
        status: error instanceof NetworkError ? 'RESULT_UNKNOWN' : 'BLOCKED', id, error,
      } }))
      if (error instanceof NetworkError) saveUnresolvedOperation({
        type, resource: key, operationId: id, payloadDigest, unresolvedAt: new Date().toISOString(),
      })
      else removeUnresolvedOperation(type, key)
      if (error instanceof ApiError && [
        'STAFF_CORRECTION_MAKER_CHECKER_VIOLATION', 'CORRECTION_TASK_PROOF_MISSING',
        'CORRECTION_TASK_ALREADY_COMPLETED', 'CORRECTION_REQUEST_CONFLICT',
      ].includes(error.errorCode)) await reconcile(documentRelevant)
      return false
    }
  }

  const chooseFile = (taskId: string, file?: File) => {
    if (!file) { setFiles((value) => ({ ...value, [taskId]: undefined })); return }
    const problem = !ALLOWED_TYPES.has(file.type)
      ? 'Choose a PDF, JPEG, or PNG file.'
      : file.size > MAX_SIZE ? 'Choose a file no larger than 10 MiB.' : undefined
    setFileErrors((value) => ({ ...value, [taskId]: problem }))
    setFiles((value) => ({ ...value, [taskId]: problem ? undefined : file }))
    setActions((value) => ({ ...value, [`upload:${taskId}`]: { status: 'DRAFT' } }))
  }

  const upload = async (task: StaffCorrectionCaseTask) => {
    const file = files[task.taskId]
    if (!file || !task.checklistItemId) return
    const key = `upload:${task.taskId}`
    const fileHash = await digestFile(file)
    const completed = await runAction(key, 'STAFF_UPLOAD', {
      taskId: task.taskId, baseline: task.baselineDocumentVersionId, fileHash,
    }, (id) => uploadStaffDocument(
      manager, loanApplicationId, task.checklistItemId!, id,
      task.baselineDocumentVersionId, file,
    ), true)
    if (completed) setFiles((value) => ({ ...value, [task.taskId]: undefined }))
  }

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Corrections unavailable</h1><Alert variant="warning"><AlertTriangle /><AlertTitle>Invalid application identifier</AlertTitle></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application corrections</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading correction evidence…</div></section>
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application corrections</h1><QueryErrorPanel error={query.error} resource="correction evidence" onRetry={() => void query.refetch()} /></section>
  if (!query.data) return null
  const data = query.data
  const request = data.correctionRequest
  const resubmitState = actions[resubmissionKey]

  return <section className="mx-auto max-w-7xl space-y-6">
    <header className="rounded-lg border bg-card p-5"><p className="text-sm font-semibold text-muted-foreground">CORRECTION CASE</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">{data.applicationNumber}</h1><p className="mt-2 text-sm text-muted-foreground">{humanizeKnownValue(data.productCode)} · {humanizeKnownValue(data.applicationStatus)}</p><div className="mt-4 flex flex-wrap gap-2"><Button variant="outline" onClick={() => void query.refetch()} disabled={query.isFetching}>{query.isFetching ? <Spinner /> : <RefreshCw />} Refresh proof</Button>{canReadCase ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}`}>Application overview</Link></Button> : null}{canReview ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}/documents`}>Documents</Link></Button> : null}</div></header>
    {!request ? <div className="rounded-lg border bg-card p-8 text-center"><CheckCircle2 className="mx-auto size-8 text-success" /><h2 className="mt-3 text-lg font-semibold">No correction request</h2><p className="mt-1 text-sm text-muted-foreground">No correction evidence exists for this application.</p></div> : <>
      <Card><CardHeader><CardTitle>Correction request</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-3"><div><dt className="text-sm text-muted-foreground">Status</dt><dd className="font-semibold">{humanizeKnownValue(request.status)}</dd></div><div><dt className="text-sm text-muted-foreground">Reason</dt><dd className="font-semibold">{humanizeKnownValue(request.reasonCode)}</dd></div><div><dt className="text-sm text-muted-foreground">Created</dt><dd className="font-semibold">{formatTimestamp(request.createdAt)}</dd></div></dl></CardContent></Card>
      {request.makerCheckerBlockedForCurrentActor ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Separation of duties applies</AlertTitle><AlertDescription>You created this correction request. Another authorized Staff actor must complete its Staff tasks.</AlertDescription></Alert> : null}
      <div className="space-y-4"><h2 className="text-xl font-semibold">Correction tasks</h2>{request.tasks.map((task) => {
        const staffOwned = task.responsibleParty === 'STAFF'
        const actionable = staffOwned && task.status === 'OPEN' && !request.makerCheckerBlockedForCurrentActor
        const completion = actions[`complete:${task.taskId}`]
        const uploadState = actions[`upload:${task.taskId}`]
        const highlighted = task.taskId === selectedTaskId
        return <Card key={task.taskId} className={highlighted ? 'ring-2 ring-primary/35' : undefined}><CardHeader><div className="flex flex-wrap items-start justify-between gap-3"><div><CardTitle>{humanizeKnownValue(task.scope)}</CardTitle><p className="mt-1 text-sm text-muted-foreground">{humanizeKnownValue(task.responsibleParty)} task · {humanizeKnownValue(task.status)}</p></div><span className="rounded-full border px-3 py-1 text-xs font-semibold">Proof: {humanizeKnownValue(task.proofState)}</span></div></CardHeader><CardContent className="space-y-4"><dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-muted-foreground">Document</dt><dd className="font-semibold">{task.documentType ? humanizeKnownValue(task.documentType) : 'Not document-specific'}</dd></div><div><dt className="text-muted-foreground">Baseline version</dt><dd className="break-all font-semibold">{task.baselineDocumentVersionId ?? 'None'}</dd></div><div className="sm:col-span-2"><dt className="text-muted-foreground">Staff instruction</dt><dd className="font-semibold">{staffOwned ? task.staffInstruction ?? 'No Staff instruction' : 'Customer-owned work; no Staff command is available.'}</dd></div></dl>
          {task.scope === 'DOCUMENT_REVIEW' && task.checklistItemId && task.baselineDocumentVersionId && canReview ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}/documents?checklistItemId=${task.checklistItemId}&documentVersionId=${task.baselineDocumentVersionId}`}>Open document evidence</Link></Button> : null}
          {task.scope === 'SUPPORTING_DOCUMENT_UPLOAD' && actionable ? canUpload ? <div className="space-y-3 rounded-md border bg-muted/25 p-4"><label className="grid gap-2 text-sm font-semibold">Upload proof<Input type="file" accept="application/pdf,image/jpeg,image/png" onChange={(event) => chooseFile(task.taskId, event.target.files?.[0])} /></label>{fileErrors[task.taskId] ? <p role="alert" className="text-sm font-semibold text-danger">{fileErrors[task.taskId]}</p> : null}<Button onClick={() => void upload(task)} disabled={!files[task.taskId] || uploadState?.status === 'IN_FLIGHT'}><FileUp /> Upload Staff document</Button>{uploadState && uploadState.status !== 'DRAFT' ? <OperationStatusPanel status={uploadState.status} /> : null}{uploadState?.error ? <ActionError error={uploadState.error} /> : null}</div> : <Alert variant="information"><FileUp /><AlertTitle>Upload authority required</AlertTitle><AlertDescription>This task needs document:upload:staff in addition to correction authority.</AlertDescription></Alert> : null}
          {completion && completion.status !== 'DRAFT' ? <OperationStatusPanel status={completion.status} /> : null}
          {completion?.error ? <ActionError error={completion.error} /> : null}
          {staffOwned && task.status === 'OPEN' ? <Button onClick={() => void runAction(`complete:${task.taskId}`, 'TASK_COMPLETION', { taskId: task.taskId }, (id) => completeStaffCorrectionTask(manager, task.taskId, id), true)} disabled={!actionable || task.proofState !== 'SATISFIED' || completion?.status === 'IN_FLIGHT'}>Complete Staff task</Button> : null}
        </CardContent></Card>
      })}</div>
      <Card><CardHeader><CardTitle>Staff resubmission</CardTitle><p className="text-sm text-muted-foreground">Meridian revalidates Customer, product, and document state. The resulting lifecycle status is not predicted here.</p></CardHeader><CardContent className="space-y-4">{request.staffResubmissionReady ? <Button id="review-resubmission" onClick={() => setConfirmingResubmission(true)} disabled={resubmitState?.status === 'IN_FLIGHT' || resubmitState?.status === 'RECONCILING'}>Review Staff resubmission</Button> : <p className="text-sm text-muted-foreground">{request.allTasksComplete ? 'Staff resubmission is not applicable to this correction composition.' : 'Resubmission is unavailable until the backend reports every required task complete.'}</p>}{resubmitState ? <OperationStatusPanel status={resubmitState.status} /> : null}{resubmitState?.error ? <ActionError error={resubmitState.error} /> : null}</CardContent></Card>
      {confirmingResubmission ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="resubmit-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><div><h2 id="resubmit-confirm-title" className="text-xl font-semibold">Confirm Staff resubmission</h2><p className="mt-1 text-sm text-muted-foreground">The backend remains authoritative for the resulting application lifecycle state.</p></div><dl className="grid gap-3 text-sm"><div><dt className="text-muted-foreground">Application</dt><dd className="font-semibold">{data.applicationNumber}</dd></div><div><dt className="text-muted-foreground">Current correction status</dt><dd className="font-semibold">{humanizeKnownValue(request.status)}</dd></div><div><dt className="text-muted-foreground">Required tasks</dt><dd className="font-semibold">Backend reports all tasks complete</dd></div><div><dt className="text-muted-foreground">Final validation</dt><dd className="font-semibold">Customer, product, and document state will be revalidated</dd></div></dl><div className="flex justify-end gap-2"><Button variant="outline" onClick={closeResubmissionConfirmation}>Cancel</Button><Button autoFocus onClick={() => { closeResubmissionConfirmation(); void runAction(resubmissionKey, 'STAFF_RESUBMISSION', { loanApplicationId }, (id) => resubmitStaffCorrection(manager, loanApplicationId, id), true) }}><CheckCircle2 /> Confirm resubmission</Button></div></div></div> : null}
    </>}
  </section>
}

function ActionError({ error }: { error: Error }) {
  return <Alert variant="destructive"><AlertTriangle /><AlertTitle>Operation not confirmed</AlertTitle><AlertDescription>{error instanceof ApiError || error instanceof UnresolvedOperationConflictError ? error.message : 'The result is unknown. Reconcile before retrying with the retained operation identity.'}{error instanceof ApiError && error.requestId ? <RequestCorrelation requestId={error.requestId} /> : null}</AlertDescription></Alert>
}
