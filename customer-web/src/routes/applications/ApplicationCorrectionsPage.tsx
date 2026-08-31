import { AlertCircle, ArrowLeft, Ban, CheckCircle2, FileCheck2, Info } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { EmptyState } from '@/components/common/EmptyState'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { FocusedFlowLayout } from '@/components/layout/FocusedFlowLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import {
  useCancelOwnApplicationMutation,
  useOwnApplicationQuery,
  useOwnApplicationsQuery,
} from '@/features/applications/application-queries'
import { CorrectionTaskCard } from '@/features/corrections/components/CorrectionTaskCard'
import { correctionErrorMessage } from '@/features/corrections/correction-presentation'
import {
  correctionKeys,
  useCorrectionTasksQuery,
  useResubmitCorrectionMutation,
} from '@/features/corrections/correction-queries'
import { useDocumentChecklistQuery } from '@/features/documents/document-queries'
import { ApiError } from '@/lib/api'
import { useOperationIdentity } from '@/lib/ids/use-operation-identity'

export function ApplicationCorrectionsPage() {
  const { loanApplicationId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useOwnApplicationQuery(loanApplicationId)
  const indexQuery = useOwnApplicationsQuery()
  const tasksQuery = useCorrectionTasksQuery(loanApplicationId)
  const checklistQuery = useDocumentChecklistQuery(loanApplicationId)
  const resubmission = useResubmitCorrectionMutation()
  const cancellation = useCancelOwnApplicationMutation()
  const resubmissionOperation = useOperationIdentity()
  const cancellationOperation = useOperationIdentity()
  const [resubmissionError, setResubmissionError] = useState<unknown>()
  const [cancellationError, setCancellationError] = useState<unknown>()

  const indexedApplication = indexQuery.data?.find(
    (application) => application.loanApplicationId === loanApplicationId,
  )
  const allTasksCompleted = Boolean(
    tasksQuery.data?.length && tasksQuery.data.every((task) => task.status === 'COMPLETED'),
  )
  const canResubmit = allTasksCompleted
    && indexedApplication?.requiredAction === 'COMPLETE_CORRECTIONS'
  const canCancel = detailQuery.data?.status === 'RETURNED_FOR_REVISION'
    && ['SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN'].includes(detailQuery.data.productCode)

  const refreshAuthoritative = async () => {
    await Promise.all([
      detailQuery.refetch(),
      indexQuery.refetch(),
      tasksQuery.refetch(),
      checklistQuery.refetch(),
    ])
  }

  const resubmit = async () => {
    if (!loanApplicationId || resubmission.isPending || !canResubmit) return
    setResubmissionError(undefined)
    try {
      const result = await resubmission.resubmit({
        loanApplicationId,
        resubmissionRequestId: resubmissionOperation.begin(),
      })
      resubmissionOperation.reset()
      navigate(`/applications/${loanApplicationId}`, {
        state: { workflowResult: { kind: 'resubmitted', status: result.loanApplicationStatus } },
      })
    } catch (error) {
      setResubmissionError(error)
      if (error instanceof ApiError) {
        resubmissionOperation.reset()
        await refreshAuthoritative()
      }
    }
  }

  const cancel = async () => {
    if (!loanApplicationId || cancellation.isPending || !canCancel) return
    setCancellationError(undefined)
    try {
      const result = await cancellation.cancel({
        loanApplicationId,
        requestId: cancellationOperation.begin(),
      })
      cancellationOperation.reset()
      await queryClient.invalidateQueries({ queryKey: correctionKeys.tasks(loanApplicationId) })
      navigate(`/applications/${loanApplicationId}`, {
        state: { workflowResult: { kind: 'cancelled', status: result.resultingStatus } },
      })
    } catch (error) {
      setCancellationError(error)
      if (error instanceof ApiError) {
        cancellationOperation.reset()
        await refreshAuthoritative()
      }
    }
  }

  const notFound = detailQuery.error instanceof ApiError && detailQuery.error.status === 404

  return (
    <FocusedFlowLayout
      eyebrow="Application corrections"
      title={notFound ? 'Corrections unavailable' : 'Complete corrections'}
      description={notFound
        ? 'This correction workflow could not be found or is not available to this Customer.'
        : `Review and complete only the Customer tasks Meridian returned${detailQuery.data ? ` for application ${detailQuery.data.applicationNumber}` : ''}.`}
      backAction={<Button variant="secondary" asChild><Link to={loanApplicationId ? `/applications/${loanApplicationId}` : '/applications'}><ArrowLeft aria-hidden="true" />Application</Link></Button>}
      continueAction={canResubmit ? (
        <Button disabled={resubmission.isPending} onClick={() => void resubmit()}>
          {resubmission.isPending ? 'Resubmitting…' : 'Resubmit corrections'}
        </Button>
      ) : undefined}
    >
      <div className="space-y-7">
        {notFound ? (
          <EmptyState icon={Info} title="Corrections unavailable" description="Return to your application list and choose an available application." action={<Button asChild><Link to="/applications">Applications</Link></Button>} />
        ) : null}
        {!notFound && (detailQuery.isPending || indexQuery.isPending || tasksQuery.isPending || checklistQuery.isPending) ? (
          <div className="space-y-4" role="status" aria-label="Loading corrections"><Skeleton className="h-32" /><Skeleton className="h-80" /></div>
        ) : null}
        {!notFound && detailQuery.isError ? <QueryErrorFeedback error={detailQuery.error} title="Application context could not be loaded" onRetry={() => void detailQuery.refetch()} /> : null}
        {!notFound && indexQuery.isError ? <QueryErrorFeedback error={indexQuery.error} title="Customer action could not be loaded" onRetry={() => void indexQuery.refetch()} /> : null}
        {!notFound && tasksQuery.isError ? <QueryErrorFeedback error={tasksQuery.error} title="Correction tasks could not be loaded" onRetry={() => void tasksQuery.refetch()} /> : null}
        {!notFound && checklistQuery.isError ? <QueryErrorFeedback error={checklistQuery.error} title="Correction evidence could not be loaded" onRetry={() => void checklistQuery.refetch()} /> : null}

        {resubmissionError ? (
          <MutationFailure title="Corrections were not resubmitted" error={resubmissionError} fallback="The correction resubmission could not be completed. Check your connection and retry the same action if appropriate." />
        ) : null}

        {tasksQuery.data?.length ? (
          <section aria-labelledby="customer-correction-tasks" className="space-y-5">
            <div><h2 id="customer-correction-tasks" className="text-xl font-semibold">Customer correction tasks</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">Evidence upload and task completion are separate backend operations.</p></div>
            {tasksQuery.data.map((task) => (
              <CorrectionTaskCard
                key={task.correctionTaskId}
                loanApplicationId={loanApplicationId!}
                task={task}
                checklistItem={checklistQuery.data?.items.find((item) => item.checklistItemId === task.checklistItemId)}
                checklistReady={Boolean(checklistQuery.data)}
                onRefreshAuthoritative={refreshAuthoritative}
              />
            ))}
          </section>
        ) : null}

        {tasksQuery.data?.length === 0 ? (
          <EmptyState icon={FileCheck2} title="No Customer correction tasks" description="Meridian did not return any Customer-owned correction work for this application." />
        ) : null}

        {allTasksCompleted && indexedApplication?.requiredAction !== 'COMPLETE_CORRECTIONS' ? (
          <Alert variant="success"><CheckCircle2 aria-hidden="true" /><AlertTitle>Your Customer tasks are complete</AlertTitle><AlertDescription>No further Customer action is currently required.</AlertDescription></Alert>
        ) : null}

        {canResubmit ? (
          <Alert><Info aria-hidden="true" /><AlertTitle>Ready for Customer resubmission</AlertTitle><AlertDescription>Every returned Customer task is complete and Meridian still reports corrections as the required Customer action.</AlertDescription></Alert>
        ) : null}

        {canCancel ? (
          <section aria-labelledby="cancel-application-heading" className="space-y-3 border-t border-border pt-6">
            <div><h2 id="cancel-application-heading" className="text-lg font-semibold">End this correction flow</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">Cancellation is available only for this returned Salary Advance or Unsecured Consumer Loan application.</p></div>
            <Dialog>
              <DialogTrigger asChild><Button variant="destructive"><Ban aria-hidden="true" />Cancel application</Button></DialogTrigger>
              <DialogContent>
                <DialogHeader><DialogTitle>Cancel this application?</DialogTitle><DialogDescription>This application will be cancelled and the correction flow will end. This action does not implement any Staff or administrative cancellation.</DialogDescription></DialogHeader>
                {cancellationError ? <MutationFailure title="Application was not cancelled" error={cancellationError} fallback="The cancellation could not be completed. Check your connection and retry the same action if appropriate." /> : null}
                <DialogFooter>
                  <DialogClose asChild><Button variant="secondary" disabled={cancellation.isPending}>Keep application</Button></DialogClose>
                  <Button variant="destructive" disabled={cancellation.isPending} onClick={() => void cancel()}>{cancellation.isPending ? 'Cancelling…' : 'Cancel application'}</Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          </section>
        ) : null}
      </div>
    </FocusedFlowLayout>
  )
}

function MutationFailure({ title, error, fallback }: { title: string; error: unknown; fallback: string }) {
  return (
    <Alert variant="destructive" aria-live="polite">
      <AlertCircle aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription className="space-y-2">
        <p>{correctionErrorMessage(error, fallback)}</p>
        {error instanceof ApiError && error.requestId ? <p className="break-all text-xs">Support reference: {error.requestId}</p> : null}
      </AlertDescription>
    </Alert>
  )
}
