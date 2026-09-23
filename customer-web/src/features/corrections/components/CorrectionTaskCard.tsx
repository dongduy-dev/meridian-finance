import { AlertCircle, CheckCircle2, FileQuestion, Info } from 'lucide-react'
import { useState } from 'react'

import { StatusBadge } from '@/components/common/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { DocumentStatus } from '@/features/documents/components/DocumentStatus'
import { DocumentUpload } from '@/features/documents/components/DocumentUpload'
import type { CustomerDocumentChecklistItem } from '@/features/documents/document-api'
import { documentTypeLabel } from '@/features/loan-products/loan-product-presentation'
import { ApiError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'
import { useOperationIdentity } from '@/lib/ids/use-operation-identity'

import type { CustomerCorrectionTask } from '../correction-api'
import {
  correctionErrorMessage,
  correctionReasonLabel,
  correctionScopePresentation,
  correctionTaskStatusPresentation,
} from '../correction-presentation'
import { useCompleteCorrectionTaskMutation } from '../correction-queries'

export function CorrectionTaskCard({
  loanApplicationId,
  task,
  checklistItem,
  checklistReady,
  onRefreshAuthoritative,
}: {
  loanApplicationId: string
  task: CustomerCorrectionTask
  checklistItem?: CustomerDocumentChecklistItem
  checklistReady: boolean
  onRefreshAuthoritative: () => Promise<unknown>
}) {
  const scope = correctionScopePresentation(task.scope)
  const completed = task.status === 'COMPLETED'
  const open = task.status === 'OPEN'
  const completion = useCompleteCorrectionTaskMutation()
  const operation = useOperationIdentity()
  const [error, setError] = useState<unknown>()
  const [completedResult, setCompletedResult] = useState(false)

  const completeTask = async () => {
    if (completion.isPending) return
    setError(undefined)
    setCompletedResult(false)
    try {
      await completion.complete({
        loanApplicationId,
        taskId: task.correctionTaskId,
        completionRequestId: operation.begin(),
      })
      operation.reset()
      setCompletedResult(true)
    } catch (failure) {
      setError(failure)
      if (failure instanceof ApiError) {
        operation.reset()
        if ([
          'CORRECTION_TASK_PROOF_MISSING',
          'CORRECTION_TASK_ALREADY_COMPLETED',
          'CORRECTION_REQUEST_CONFLICT',
          'IDEMPOTENCY_KEY_REUSED',
        ].includes(failure.errorCode)) {
          await onRefreshAuthoritative()
        }
      }
    }
  }

  return (
    <Card className="min-w-0">
      <CardHeader className="gap-3">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle className="break-words">{scope.label}</CardTitle>
            <CardDescription className="mt-1">{correctionReasonLabel(task.reasonCode)}</CardDescription>
          </div>
          <StatusBadge presentation={correctionTaskStatusPresentation(task.status)} />
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <Alert>
          <Info aria-hidden="true" />
          <AlertTitle>What you need to update</AlertTitle>
          <AlertDescription className="break-words whitespace-pre-wrap">{task.customerInstruction}</AlertDescription>
        </Alert>
        <p className="text-sm leading-6 text-muted-foreground">{scope.description}</p>
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div><dt className="text-muted-foreground">Document</dt><dd className="mt-1 font-medium">{task.documentType ? documentTypeLabel(task.documentType) : 'Document unavailable'}</dd></div>
          <div><dt className="text-muted-foreground">Created</dt><dd className="mt-1 font-medium">{formatTimestamp(task.createdAt)}</dd></div>
          {task.completedAt ? <div className="sm:col-span-2"><dt className="text-muted-foreground">Completed</dt><dd className="mt-1 font-medium">{formatTimestamp(task.completedAt)}</dd></div> : null}
        </dl>
        {completed ? (
          <Alert variant="success"><CheckCircle2 aria-hidden="true" /><AlertTitle>Change completed</AlertTitle><AlertDescription>This requested change is complete.</AlertDescription></Alert>
        ) : null}
        {!completed && !open ? (
          <Alert variant="warning"><FileQuestion aria-hidden="true" /><AlertTitle>Status unavailable</AlertTitle><AlertDescription>This action is not available right now. Refresh the page or try again later.</AlertDescription></Alert>
        ) : null}
        {open && !scope.customerCompletable ? (
          <Alert variant="warning"><FileQuestion aria-hidden="true" /><AlertTitle>Action unavailable</AlertTitle><AlertDescription>{scope.description}</AlertDescription></Alert>
        ) : null}
        {open && scope.documentAction && !checklistReady ? (
          <Alert variant="warning"><FileQuestion aria-hidden="true" /><AlertTitle>Document status unavailable</AlertTitle><AlertDescription>Refresh the document list before completing this change.</AlertDescription></Alert>
        ) : null}
        {open && scope.documentAction && checklistReady && !checklistItem ? (
          <Alert variant="warning"><FileQuestion aria-hidden="true" /><AlertTitle>Document unavailable</AlertTitle><AlertDescription>We can't show the document needed for this change. Refresh the page or try again later.</AlertDescription></Alert>
        ) : null}
        {open && scope.documentAction && checklistItem ? (
          <div className="space-y-4">
            <DocumentStatus status={checklistItem.customerStatus} />
            <DocumentUpload loanApplicationId={loanApplicationId} item={checklistItem} action={scope.documentAction} onVersionConflict={onRefreshAuthoritative} />
          </div>
        ) : null}
        {error ? (
          <Alert variant="destructive" aria-live="polite">
            <AlertCircle aria-hidden="true" />
            <AlertTitle>Task completion failed</AlertTitle>
            <AlertDescription className="space-y-2">
              <p>{correctionErrorMessage(error, 'The task could not be completed. Check your connection and retry the same action if appropriate.')}</p>
              {error instanceof ApiError && error.requestId ? <p className="break-all text-xs">Support reference: {error.requestId}</p> : null}
            </AlertDescription>
          </Alert>
        ) : null}
        {completedResult ? (
          <Alert variant="success" aria-live="polite"><CheckCircle2 aria-hidden="true" /><AlertTitle>Requested change completed</AlertTitle><AlertDescription>This change is complete and the application status was refreshed.</AlertDescription></Alert>
        ) : null}
        {open && scope.customerCompletable && checklistItem ? (
          <div className="space-y-2 border-t border-border pt-5">
            <p className="text-sm leading-6 text-muted-foreground">Uploading a document does not complete this change. Mark it complete after the required document is available.</p>
            <Button type="button" disabled={completion.isPending} onClick={() => void completeTask()}>
              {completion.isPending ? <Spinner /> : <CheckCircle2 aria-hidden="true" />}
              {completion.isPending ? 'Completing…' : 'Mark as complete'}
            </Button>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}
