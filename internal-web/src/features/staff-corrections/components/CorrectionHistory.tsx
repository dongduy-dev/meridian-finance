import { Link } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { formatTimestamp } from '@/lib/format/presentation'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import type { CorrectionActor, HistoricalCorrection } from '../api/contracts'

function Actor({ value }: { value: CorrectionActor | null }) {
  if (value?.actorType === 'STAFF' && value.staffActor) return <>{value.staffActor.displayName}<span className="block break-all text-xs text-muted-foreground">{value.staffActor.email}</span></>
  return <>{value?.actorType === 'CUSTOMER_SELF_SERVICE' ? 'Customer — self-service' : value?.actorType === 'SYSTEM' ? 'System' : 'Actor unavailable'}</>
}

export function CorrectionHistory({ loanApplicationId, history, latestId, canReadDocuments }: {
  loanApplicationId: string; history: HistoricalCorrection[]; latestId?: string; canReadDocuments: boolean
}) {
  return <section className="space-y-4"><h2 className="text-xl font-semibold">Correction history</h2>
    {history.length === 0 ? <p className="text-sm text-muted-foreground">No correction history exists.</p> : <ol className="space-y-4">{history.map((request, index) => <li key={request.correctionRequestId}>
      <Card><CardHeader><CardTitle>Correction {index + 1}{request.correctionRequestId === latestId ? ' · Latest' : ''}</CardTitle><p className="text-sm text-muted-foreground">Read-only recorded evidence</p></CardHeader>
        <CardContent className="space-y-4"><dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div><dt className="text-muted-foreground">Status</dt><dd>{humanizeKnownValue(request.status)}</dd></div>
          <div><dt className="text-muted-foreground">Reason</dt><dd>{humanizeKnownValue(request.reasonCode)}</dd></div>
          <div><dt className="text-muted-foreground">Source action</dt><dd>{humanizeKnownValue(request.sourceAction)}</dd></div>
          <div><dt className="text-muted-foreground">Created by</dt><dd><Actor value={request.createdBy} /></dd></div>
          <div><dt className="text-muted-foreground">Created at</dt><dd>{formatTimestamp(request.createdAt)}</dd></div>
          {request.readyAt ? <div><dt className="text-muted-foreground">Ready at</dt><dd>{formatTimestamp(request.readyAt)}</dd></div> : null}
          {request.cancelledAt ? <div><dt className="text-muted-foreground">Cancelled at</dt><dd>{formatTimestamp(request.cancelledAt)}</dd></div> : null}
          {request.resubmittedAt ? <><div><dt className="text-muted-foreground">Resubmitted by</dt><dd><Actor value={request.resubmittedBy} /></dd></div><div><dt className="text-muted-foreground">Resubmitted at</dt><dd>{formatTimestamp(request.resubmittedAt)}</dd></div></> : null}
          {request.resultingApplicationStatus ? <div><dt className="text-muted-foreground">Resulting application status</dt><dd>{humanizeKnownValue(request.resultingApplicationStatus)}</dd></div> : null}
        </dl>
        <ol className="space-y-3">{request.tasks.map((task) => <li key={task.taskId} className="space-y-3 rounded-md border p-3">
          <h3 className="font-semibold">Task {task.sequence} · {humanizeKnownValue(task.responsibleParty)} · {humanizeKnownValue(task.scope)}</h3>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div><dt className="text-muted-foreground">Status</dt><dd>{humanizeKnownValue(task.status)}</dd></div>
            <div><dt className="text-muted-foreground">Created at</dt><dd>{formatTimestamp(task.createdAt)}</dd></div>
            {task.documentType ? <div><dt className="text-muted-foreground">Document type</dt><dd>{humanizeKnownValue(task.documentType)}</dd></div> : null}
            {task.customerInstruction ? <div><dt className="text-muted-foreground">Customer instruction</dt><dd className="whitespace-pre-wrap break-words">{task.customerInstruction}</dd></div> : null}
            {task.staffInstruction ? <div><dt className="text-muted-foreground">Staff instruction</dt><dd className="whitespace-pre-wrap break-words">{task.staffInstruction}</dd></div> : null}
            {task.completedAt ? <><div><dt className="text-muted-foreground">{task.responsibleParty === 'CUSTOMER' && task.completedBy?.actorType === 'STAFF' ? 'Customer task recorded by' : 'Completed by'}</dt><dd><Actor value={task.completedBy} /></dd></div><div><dt className="text-muted-foreground">Completed at</dt><dd>{formatTimestamp(task.completedAt)}</dd></div></> : null}
          </dl>
          {canReadDocuments && task.checklistItemId && task.baselineDocumentVersionId ? <Button asChild variant="outline"><Link to={`/staff/applications/${loanApplicationId}/documents?checklistItemId=${task.checklistItemId}&documentVersionId=${task.baselineDocumentVersionId}`}>Open baseline document</Link></Button> : null}
          {task.completedAt && task.documentType ? <p className="text-xs text-muted-foreground">Completion actor/time is retained. An exact completion-proof version was not retained.</p> : null}
        </li>)}</ol>
        <details className="text-xs text-muted-foreground"><summary>Technical references</summary><p className="break-all">Correction: {request.correctionRequestId}</p>{request.sourceReviewCycleId ? <p className="break-all">Source review cycle: {request.sourceReviewCycleId}</p> : null}</details>
        </CardContent>
      </Card>
    </li>)}</ol>}
  </section>
}
