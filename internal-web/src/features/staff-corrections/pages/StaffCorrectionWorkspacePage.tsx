import { staffVerificationKeys } from '@/features/staff-verification/api/queries'
import { staffReviewKeys } from '@/features/staff-review/api/queries'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, FileUp, ShieldAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { RecordedCustomerActionPanel } from '@/components/operations/RecordedCustomerActionPanel'
import { CorrectionHistory } from '../components/CorrectionHistory'
import { ApplicationWorkspaceShell } from '@/components/operations/ApplicationWorkspaceShell'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { staffApplicationCaseQuery, staffApplicationKeys } from '@/features/staff-applications/api/queries'
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
import {
  completeAssistedCustomerCorrectionTask,
  completeStaffCorrectionTask,
  recordAssistedUclCancellation,
  resubmitStaffCorrection,
  uploadAssistedCancellationEvidence,
} from '../api/staff-corrections-api'

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
  const canReadCase = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const canUploadStaff = state.status === 'authenticated' && hasPermission(state.actor, 'document:upload:staff')
  const canUploadAssistedCorrection = state.status === 'authenticated'
    && hasPermission(state.actor, 'document:upload:assisted-correction')
  const canReview = state.status === 'authenticated' && hasPermission(state.actor, 'document:review')
  const canRecordAssistedCancellation = state.status === 'authenticated'
    && hasPermission(state.actor, 'loan:cancel:staff')
    && hasRole(state.actor, 'LOAN_OFFICER')
  const canUploadAssistedAction = state.status === 'authenticated'
    && hasPermission(state.actor, 'document:upload:assisted-action')
    && canRecordAssistedCancellation
  const query = useQuery(staffCorrectionCaseQuery(manager, loanApplicationId, validId && allowed))
  const caseQuery = useQuery(staffApplicationCaseQuery(manager, loanApplicationId, validId && allowed && canReadCase))
  const [files, setFiles] = useState<Record<string, File | undefined>>({})
  const [fileErrors, setFileErrors] = useState<Record<string, string | undefined>>({})
  const [actions, setActions] = useState<Record<string, ActionState>>({})
  const [confirmingResubmission, setConfirmingResubmission] = useState(false)
  const [cancellationFile, setCancellationFile] = useState<File>()
  const [cancellationFileError, setCancellationFileError] = useState<string>()
  const [confirmingCancellation, setConfirmingCancellation] = useState(false)
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
    const cancellation = query.data?.assistedCancellation
    if (cancellation?.correctionRequestId) {
      const uploadResource = `cancel-evidence:${loanApplicationId}:${cancellation.correctionRequestId}`
      const pendingUpload = findUnresolvedOperation(
        'ASSISTED_CANCELLATION_EVIDENCE_UPLOAD', uploadResource,
      )
      const pendingBaseline = pendingUpload?.semanticPayload
        && typeof pendingUpload.semanticPayload === 'object'
        && 'expectedCurrentVersionId' in pendingUpload.semanticPayload
        ? (pendingUpload.semanticPayload as { expectedCurrentVersionId?: string }).expectedCurrentVersionId
        : undefined
      if (pendingUpload && cancellation.evidence
          && cancellation.evidence.documentVersionId !== pendingBaseline) {
        removeUnresolvedOperation('ASSISTED_CANCELLATION_EVIDENCE_UPLOAD', uploadResource)
      }
    }
    if (query.data?.applicationStatus === 'CANCELLED' || request.status === 'CANCELLED') {
      removeUnresolvedOperation('ASSISTED_UCL_CANCELLATION', `cancel:${loanApplicationId}`)
    }
  }, [query.data, resubmissionKey, loanApplicationId])

  const closeResubmissionConfirmation = () => {
    setConfirmingResubmission(false)
    queueMicrotask(() => document.getElementById('review-resubmission')?.focus())
  }

  const reconcile = async (documentRelevant = false) => {
    const work = [
      queryClient.invalidateQueries({ queryKey: staffVerificationKeys.all }),
      queryClient.invalidateQueries({ queryKey: staffReviewKeys.all }),
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
        type, resource: key, operationId: id, payloadDigest, semanticPayload,
        unresolvedAt: new Date().toISOString(),
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

  const chooseCancellationFile = (file?: File) => {
    if (!file) { setCancellationFile(undefined); setCancellationFileError(undefined); return }
    const problem = !ALLOWED_TYPES.has(file.type)
      ? 'Choose a PDF, JPEG, or PNG file.'
      : file.size > MAX_SIZE ? 'Choose a file no larger than 10 MiB.' : undefined
    setCancellationFileError(problem)
    setCancellationFile(problem ? undefined : file)
  }

  const uploadCancellationEvidence = async () => {
    const cancellation = query.data?.assistedCancellation
    if (!cancellation?.correctionRequestId || !cancellationFile) return
    const resource = `cancel-evidence:${loanApplicationId}:${cancellation.correctionRequestId}`
    const fileHash = await digestFile(cancellationFile)
    const expectedCurrentVersionId = cancellation.evidence?.documentVersionId
    const completed = await runAction(
      resource,
      'ASSISTED_CANCELLATION_EVIDENCE_UPLOAD',
      { correctionRequestId: cancellation.correctionRequestId, expectedCurrentVersionId, fileHash },
      (id) => uploadAssistedCancellationEvidence(
        manager,
        loanApplicationId,
        cancellation.correctionRequestId!,
        id,
        expectedCurrentVersionId,
        cancellationFile,
      ),
      true,
    )
    if (completed) setCancellationFile(undefined)
  }

  const recordCancellation = async () => {
    const cancellation = query.data?.assistedCancellation
    if (!cancellation?.correctionRequestId || !cancellation.evidence) return
    const completed = await runAction(
      `cancel:${loanApplicationId}`,
      'ASSISTED_UCL_CANCELLATION',
      {
        expectedCorrectionRequestId: cancellation.correctionRequestId,
        evidenceDocumentVersionId: cancellation.evidence.documentVersionId,
      },
      (id) => recordAssistedUclCancellation(
        manager,
        loanApplicationId,
        id,
        cancellation.correctionRequestId!,
        cancellation.evidence!.documentVersionId,
      ),
      true,
    )
    if (completed) setConfirmingCancellation(false)
  }

  if (!validId) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Corrections unavailable</h1><Alert variant="warning"><AlertTriangle /><AlertTitle>Invalid application identifier</AlertTitle></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 tabIndex={-1} className="text-2xl font-semibold">Application corrections</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading correction evidence…</div></section>
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Application corrections</h1><QueryErrorPanel error={query.error} resource="correction evidence" onRetry={() => void query.refetch()} /></section>
  if (!query.data) return null
  const data = query.data
  const request = data.correctionRequest
  const resubmitState = actions[resubmissionKey]
  const assistedCancellation = data.assistedCancellation
  const cancellationEvidenceKey = assistedCancellation.correctionRequestId
    ? `cancel-evidence:${loanApplicationId}:${assistedCancellation.correctionRequestId}` : undefined
  const cancellationCommandKey = `cancel:${loanApplicationId}`
  const cancellationEvidenceState = cancellationEvidenceKey ? actions[cancellationEvidenceKey] : undefined
  const cancellationCommandState = actions[cancellationCommandKey]

  const featureFacts = {
    loanApplicationId: data.loanApplicationId, applicationNumber: data.applicationNumber,
    applicationStatus: data.applicationStatus, productCode: data.productCode,
    originationChannel: data.originationChannel,
  }
  const headerCase = canReadCase && caseQuery.isSuccess ? caseQuery.data : undefined

  return <ApplicationWorkspaceShell
    actor={state.status === 'authenticated' ? state.actor : undefined}
    context={headerCase ? { source: 'case', facts: {
      ...featureFacts, requestedAmount: headerCase.requestedAmount,
      requestedTermMonths: headerCase.requestedTermMonths, submittedAt: headerCase.submittedAt,
    } } : { source: 'feature', facts: featureFacts }} activeSection="corrections"
    updatedAt={query.dataUpdatedAt} refreshing={query.isFetching || (canReadCase && caseQuery.isFetching)} stale={query.isStale || (canReadCase && caseQuery.isStale)}
    contextUnavailable={canReadCase && caseQuery.isError}
    onRetryContext={canReadCase ? () => void caseQuery.refetch() : undefined}
    onRefresh={() => void Promise.all([query.refetch(), ...(canReadCase ? [caseQuery.refetch()] : [])])}
  >
    <h2 className="text-xl font-semibold">Application corrections</h2>
    {!request ? <div className="rounded-lg border bg-card p-8 text-center"><CheckCircle2 className="mx-auto size-8 text-success" /><h2 className="mt-3 text-lg font-semibold">No correction request</h2><p className="mt-1 text-sm text-muted-foreground">No correction evidence exists for this application.</p></div> : <>
      <Card><CardHeader><CardTitle>Correction request</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-4"><div><dt className="text-sm text-muted-foreground">Status</dt><dd className="font-semibold">{humanizeKnownValue(request.status)}</dd></div><div><dt className="text-sm text-muted-foreground">Reason</dt><dd className="font-semibold">{humanizeKnownValue(request.reasonCode)}</dd></div><div><dt className="text-sm text-muted-foreground">Channel</dt><dd className="font-semibold">{humanizeKnownValue(data.originationChannel)}</dd></div><div><dt className="text-sm text-muted-foreground">Created</dt><dd className="font-semibold">{formatTimestamp(request.createdAt)}</dd></div></dl></CardContent></Card>
      {request.makerCheckerBlockedForCurrentActor ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Separation of duties applies</AlertTitle><AlertDescription>You created this correction request. Another authorized Staff member must complete its Staff tasks.</AlertDescription></Alert> : null}
      <div className="space-y-4"><h2 className="text-xl font-semibold">Correction tasks</h2>{request.tasks.map((task) => {
        const staffOwned = task.responsibleParty === 'STAFF'
        const customerViaStaff = task.customerSourceViaStaff
        const canUploadForTask = customerViaStaff ? canUploadAssistedCorrection : canUploadStaff
        const completion = actions[`complete:${task.taskId}`]
        const uploadState = actions[`upload:${task.taskId}`]
        const highlighted = task.taskId === selectedTaskId
        return <Card key={task.taskId} className={highlighted ? 'ring-2 ring-primary/35' : undefined}><CardHeader><div className="flex flex-wrap items-start justify-between gap-3"><div><CardTitle>{humanizeKnownValue(task.scope)}</CardTitle><p className="mt-1 text-sm text-muted-foreground">{customerViaStaff ? 'Customer-sourced task · Staff records Customer-provided evidence' : `${humanizeKnownValue(task.responsibleParty)} task`} · {humanizeKnownValue(task.status)}</p></div><span className="rounded-full border px-3 py-1 text-xs font-semibold">Proof: {humanizeKnownValue(task.proofState)}</span></div></CardHeader><CardContent className="space-y-4"><dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-muted-foreground">Document</dt><dd className="font-semibold">{task.documentType ? humanizeKnownValue(task.documentType) : 'Not document-specific'}</dd></div><div><dt className="text-muted-foreground">Baseline version</dt><dd className="break-all font-semibold">{task.baselineDocumentVersionId ?? 'None'}</dd></div><div className="sm:col-span-2"><dt className="text-muted-foreground">Instruction</dt><dd className="font-semibold">{customerViaStaff ? task.customerInstruction ?? 'No Customer instruction' : staffOwned ? task.staffInstruction ?? 'No Staff instruction' : 'The Customer completes these tasks in Customer Web.'}</dd></div></dl>
          {task.scope === 'DOCUMENT_REVIEW' && task.checklistItemId && task.baselineDocumentVersionId && canReview ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}/documents?checklistItemId=${task.checklistItemId}&documentVersionId=${task.baselineDocumentVersionId}`}>Open document evidence</Link></Button> : null}
          {task.uploadActionAvailable ? canUploadForTask ? <div className="space-y-3 rounded-md border bg-muted/25 p-4"><label className="grid gap-2 text-sm font-semibold">Upload proof<Input type="file" accept="application/pdf,image/jpeg,image/png" onChange={(event) => chooseFile(task.taskId, event.target.files?.[0])} /></label>{fileErrors[task.taskId] ? <p role="alert" className="text-sm font-semibold text-danger">{fileErrors[task.taskId]}</p> : null}<Button onClick={() => void upload(task)} disabled={!files[task.taskId] || uploadState?.status === 'IN_FLIGHT'}><FileUp /> {customerViaStaff ? 'Upload Customer-provided evidence' : 'Upload Staff document'}</Button>{uploadState && uploadState.status !== 'DRAFT' ? <OperationStatusPanel status={uploadState.status} /> : null}{uploadState?.error ? <ActionError error={uploadState.error} /> : null}</div> : <Alert variant="information"><FileUp /><AlertTitle>Upload authority required</AlertTitle><AlertDescription>You do not have access to upload the evidence required for this task.</AlertDescription></Alert> : null}
          {completion && completion.status !== 'DRAFT' ? <OperationStatusPanel status={completion.status} /> : null}
          {completion?.error ? <ActionError error={completion.error} /> : null}
          {(staffOwned || customerViaStaff) && task.status === 'OPEN' ? <Button onClick={() => void runAction(`complete:${task.taskId}`, 'TASK_COMPLETION', { taskId: task.taskId }, (id) => customerViaStaff ? completeAssistedCustomerCorrectionTask(manager, loanApplicationId, task.taskId, id) : completeStaffCorrectionTask(manager, task.taskId, id), true)} disabled={!task.completionActionAvailable || completion?.status === 'IN_FLIGHT'}>{customerViaStaff ? 'Record Customer task complete' : 'Complete Staff task'}</Button> : null}
        </CardContent></Card>
      })}</div>
      {assistedCancellation.completedCancellation ? <RecordedCustomerActionPanel title="Customer-requested cancellation recorded" value={assistedCancellation.completedCancellation} loanApplicationId={loanApplicationId} evidenceType="CUSTOMER_CANCELLATION_REQUEST" /> : null}
      {assistedCancellation.available && !assistedCancellation.completedCancellation ? <Card><CardHeader><CardTitle>Customer-requested cancellation</CardTitle><p className="text-sm text-muted-foreground">Record cancellation only at the Customer&apos;s signed request.</p></CardHeader><CardContent className="space-y-5"><div className="rounded-md border bg-muted/25 p-4"><p className="text-sm font-semibold">Signed Customer cancellation request</p><p className="mt-1 text-sm text-muted-foreground">Upload PDF, JPEG, or PNG evidence bound to correction {assistedCancellation.correctionRequestId}.</p>{assistedCancellation.evidence ? <p className="mt-3 text-sm">Current document version {assistedCancellation.evidence.versionNumber} · {assistedCancellation.evidence.detectedMimeType} · uploaded {formatTimestamp(assistedCancellation.evidence.uploadedAt)}</p> : <p className="mt-3 text-sm text-muted-foreground">No current signed cancellation evidence.</p>}<label className="mt-4 grid gap-2 text-sm font-semibold">Signed request<Input type="file" accept="application/pdf,image/jpeg,image/png" onChange={(event) => chooseCancellationFile(event.target.files?.[0])} /></label>{cancellationFileError ? <p role="alert" className="mt-2 text-sm font-semibold text-danger">{cancellationFileError}</p> : null}<Button className="mt-3" variant="outline" onClick={() => void uploadCancellationEvidence()} disabled={!assistedCancellation.evidenceUploadAvailable || !canUploadAssistedAction || !cancellationFile || cancellationEvidenceState?.status === 'IN_FLIGHT' || cancellationEvidenceState?.status === 'RECONCILING'}><FileUp />{assistedCancellation.evidence ? 'Replace signed request' : 'Upload signed request'}</Button>{cancellationEvidenceState ? <div className="mt-3"><OperationStatusPanel status={cancellationEvidenceState.status} /></div> : null}{cancellationEvidenceState?.error ? <ActionError error={cancellationEvidenceState.error} /> : null}</div><div><p className="text-sm text-muted-foreground">Recording the signed Customer request closes this correction and cancels the application.</p><Button className="mt-3" variant="destructive" onClick={() => setConfirmingCancellation(true)} disabled={!assistedCancellation.cancellationCommandAvailable || !canRecordAssistedCancellation || cancellationCommandState?.status === 'IN_FLIGHT' || cancellationCommandState?.status === 'RECONCILING'}>Review Customer-requested cancellation</Button>{cancellationCommandState ? <div className="mt-3"><OperationStatusPanel status={cancellationCommandState.status} /></div> : null}{cancellationCommandState?.error ? <ActionError error={cancellationCommandState.error} /> : null}</div></CardContent></Card> : null}
      <Card><CardHeader><CardTitle>Staff resubmission</CardTitle><p className="text-sm text-muted-foreground">Meridian checks Customer, product, and document requirements again before resubmitting.</p></CardHeader><CardContent className="space-y-4">{request.staffResubmissionReady ? <Button id="review-resubmission" onClick={() => setConfirmingResubmission(true)} disabled={resubmitState?.status === 'IN_FLIGHT' || resubmitState?.status === 'RECONCILING'}>Review Staff resubmission</Button> : <p className="text-sm text-muted-foreground">{request.allTasksComplete ? 'Staff resubmission is not applicable to this set of correction tasks.' : 'Resubmission is unavailable until every required task is complete.'}</p>}{resubmitState ? <OperationStatusPanel status={resubmitState.status} /> : null}{resubmitState?.error ? <ActionError error={resubmitState.error} /> : null}</CardContent></Card>
      {confirmingResubmission ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="resubmit-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><div><h2 id="resubmit-confirm-title" className="text-xl font-semibold">Confirm Staff resubmission</h2><p className="mt-1 text-sm text-muted-foreground">Meridian checks the latest application requirements again before resubmitting.</p></div><dl className="grid gap-3 text-sm"><div><dt className="text-muted-foreground">Application</dt><dd className="font-semibold">{data.applicationNumber}</dd></div><div><dt className="text-muted-foreground">Current correction status</dt><dd className="font-semibold">{humanizeKnownValue(request.status)}</dd></div><div><dt className="text-muted-foreground">Required tasks</dt><dd className="font-semibold">All required tasks are complete</dd></div><div><dt className="text-muted-foreground">Final validation</dt><dd className="font-semibold">Customer, product, and document requirements will be checked again</dd></div></dl><div className="flex justify-end gap-2"><Button variant="outline" onClick={closeResubmissionConfirmation}>Cancel</Button><Button autoFocus onClick={() => { closeResubmissionConfirmation(); void runAction(resubmissionKey, 'STAFF_RESUBMISSION', { loanApplicationId }, (id) => resubmitStaffCorrection(manager, loanApplicationId, id), true) }}><CheckCircle2 /> Confirm resubmission</Button></div></div></div> : null}
      {confirmingCancellation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="cancellation-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><div><h2 id="cancellation-confirm-title" className="text-xl font-semibold">Confirm Customer-requested cancellation</h2><p className="mt-1 text-sm text-muted-foreground">The Customer requested and signed this cancellation. You are recording the Customer&apos;s request.</p></div><dl className="grid gap-3 text-sm"><div><dt className="text-muted-foreground">Application</dt><dd className="font-semibold">{data.applicationNumber}</dd></div><div><dt className="text-muted-foreground">Correction request</dt><dd className="break-all font-semibold">{assistedCancellation.correctionRequestId}</dd></div><div><dt className="text-muted-foreground">Evidence version</dt><dd className="break-all font-semibold">{assistedCancellation.evidence?.documentVersionId}</dd></div><div><dt className="text-muted-foreground">Outcome</dt><dd className="font-semibold">Correction and application are cancelled</dd></div></dl><div className="flex justify-end gap-2"><Button variant="outline" onClick={() => setConfirmingCancellation(false)}>Back</Button><Button autoFocus variant="destructive" onClick={() => void recordCancellation()}>Record Customer request</Button></div></div></div> : null}
    </>}
    <CorrectionHistory loanApplicationId={loanApplicationId} history={data.correctionHistory} latestId={request?.correctionRequestId} canReadDocuments={state.status === 'authenticated' && (canReview || hasPermission(state.actor, 'approval:decide'))} />
  </ApplicationWorkspaceShell>
}

function ActionError({ error }: { error: Error }) {
  return <Alert variant="destructive"><AlertTriangle /><AlertTitle>Operation not confirmed</AlertTitle><AlertDescription>{error instanceof ApiError || error instanceof UnresolvedOperationConflictError ? error.message : 'Meridian could not confirm the result. Review the latest correction information, then retry the same action.'}{error instanceof ApiError && error.requestId ? <RequestCorrelation requestId={error.requestId} /> : null}</AlertDescription></Alert>
}
