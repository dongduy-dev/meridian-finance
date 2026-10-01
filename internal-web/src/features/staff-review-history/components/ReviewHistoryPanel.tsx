import { useQuery } from '@tanstack/react-query'
import { RecordedStaffAction } from '@/components/operations/RecordedStaffAction'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { decisionActions } from '@/features/staff-approval/api/contracts'
import { recommendationActions } from '@/features/staff-review/api/contracts'
import { formatTimestamp } from '@/lib/format/presentation'
import type { StaffReviewHistory } from '../api/contracts'
import { staffReviewHistoryQuery } from '../api/queries'

const cycleStatuses = new Set(['ACTIVE', 'COMPLETED', 'SUPERSEDED', 'CORRECTION_REQUIRED', 'CORRECTED'])
type Cycle = StaffReviewHistory['cycles'][number]

function cycleStatusLabel(status: string) {
  if (!cycleStatuses.has(status)) return 'Review status unavailable'
  return status === 'SUPERSEDED' ? 'Superseded' : humanizeKnownValue(status)
}

function Rationale({ value }: { value: NonNullable<Cycle['recommendation'] | Cycle['decision']> }) {
  return <div className="space-y-3">
    <div><p className="font-semibold">Reason</p><p className="whitespace-pre-wrap">{value.reason ?? 'No free-text reason recorded.'}</p>
      {value.reasonCode ? <p>Controlled reason: {humanizeKnownValue(value.reasonCode)}</p> : null}</div>
    <div className="rounded-md border border-dashed p-3"><p className="font-semibold">Internal credit note</p>
      <p className="whitespace-pre-wrap">{value.internalNoteReadable
        ? value.internalNotes ?? 'No internal credit note recorded.'
        : 'Restricted to the responsible Loan Officer and authorized Approvers.'}</p></div>
  </div>
}

export function ReviewHistoryPanel({ loanApplicationId }: { loanApplicationId: string }) {
  const { manager, state } = useAuth()
  const canRead = state.status === 'authenticated'
    && ['loan:review', 'approval:recommend', 'approval:decide'].some((permission) => hasPermission(state.actor, permission))
  const query = useQuery(staffReviewHistoryQuery(manager, loanApplicationId,
    canRead && uuidSchema.safeParse(loanApplicationId).success))
  if (!canRead) return null
  return <Card><CardHeader><CardTitle>Review and decision history</CardTitle>
    <p className="text-sm text-muted-foreground">Recorded cycles are read-only. Use the current workspace controls for new actions.</p>
  </CardHeader><CardContent className="space-y-5">
    {query.isPending ? <p role="status">Loading review history…</p>
      : query.isError ? <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} />
        : query.data?.cycles.length === 0 ? <p>No review cycles have been recorded.</p>
          : query.data?.cycles.map((cycle, index) => <section key={cycle.reviewCycleId}
            aria-labelledby={`history-cycle-${cycle.cycleNumber}`} className="min-w-0 space-y-4 rounded-md border p-4">
            <div><h3 id={`history-cycle-${cycle.cycleNumber}`} className="font-semibold">Review Cycle {cycle.cycleNumber}</h3>
              <p className="text-sm text-muted-foreground">{index === query.data.cycles.length - 1 ? 'Latest recorded cycle' : 'Earlier cycle'} · {cycleStatusLabel(cycle.status)}</p></div>
            <dl className="grid min-w-0 gap-3 text-sm sm:grid-cols-2">
              <div className="min-w-0"><dt className="font-semibold">Assigned Loan Officer</dt><dd className="break-words">{cycle.assignedLoanOfficer
                ? <>{cycle.assignedLoanOfficer.displayName}<span className="block">{cycle.assignedLoanOfficer.email}</span></>
                : 'Assignment unavailable'}</dd></div>
              <div><dt className="font-semibold">Started</dt><dd>{formatTimestamp(cycle.startedAt)}</dd></div>
              <div><dt className="font-semibold">Ended</dt><dd>{cycle.endedAt ? formatTimestamp(cycle.endedAt) : 'No end recorded'}</dd></div>
            </dl>
            <div className="grid min-w-0 gap-4 lg:grid-cols-2">
              <div className="min-w-0 space-y-2"><h4 className="font-semibold">Recommendation</h4>{cycle.recommendation
                ? <RecordedStaffAction outcome={recommendationActions.some((action) => action === cycle.recommendation?.action)
                  ? humanizeKnownValue(cycle.recommendation.action) : 'Recommendation action unavailable'}
                known={recommendationActions.some((action) => action === cycle.recommendation?.action)}
                recordedAt={cycle.recommendation.submittedAt} recordedBy={cycle.recommendation.recordedBy}>
                  <Rationale value={cycle.recommendation} /></RecordedStaffAction>
                : <p className="text-sm">No recommendation recorded for this cycle.</p>}</div>
              <div className="min-w-0 space-y-2"><h4 className="font-semibold">Decision</h4>{cycle.decision
                ? <RecordedStaffAction outcome={decisionActions.some((action) => action === cycle.decision?.action)
                  ? humanizeKnownValue(cycle.decision.action) : 'Decision action unavailable'}
                known={decisionActions.some((action) => action === cycle.decision?.action)}
                recordedAt={cycle.decision.decidedAt} recordedBy={cycle.decision.recordedBy}>
                  <Rationale value={cycle.decision} /></RecordedStaffAction>
                : <p className="text-sm">No decision recorded for this cycle.</p>}</div>
            </div>
          </section>)}
  </CardContent></Card>
}
