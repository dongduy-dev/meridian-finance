import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { formatTimestamp } from '@/lib/format/presentation'
import type { StaffServicingProvenance } from '../api/contracts'
import { accountStatusLabel } from '../model/presentation'

type Actor = StaffServicingProvenance['originatingDisbursement']['actor']

export function servicingActorLabel(actor: Actor): string {
  if (actor.type === 'SYSTEM' && actor.staff === null) return 'Meridian system'
  if (actor.type === 'USER' && actor.staff) {
    return `${actor.staff.displayName} · ${actor.staff.email}`
  }
  return 'Actor unavailable'
}

const actionLabels: Record<string, string> = {
  ACTIVATION_INITIALIZED: 'Activated',
  REPAYMENT_RECORDED: 'Repayment changed account status',
  OVERDUE_EVALUATED: 'Overdue evaluated',
  APPROVED_SETTLEMENT: 'Settlement approved',
  ADMINISTRATIVE_CLOSURE: 'Administrative closure',
}

export function StaffServicingProvenancePanel({ data, pending, error, onRetry }: {
  data?: StaffServicingProvenance
  pending: boolean
  error?: Error | null
  onRetry: () => void
}) {
  return <Card><CardHeader><CardTitle>Account activity</CardTitle></CardHeader>
    <CardContent className="space-y-5">
      {pending ? <div role="status" className="flex items-center gap-2"><Spinner /> Loading account activity…</div> : null}
      {error ? <QueryErrorPanel error={error} resource="case" onRetry={onRetry} /> : null}
      {data && !error ? <>
        <div><h3 className="font-semibold">Disbursement</h3><p className="text-sm">Confirmed by {servicingActorLabel(data.originatingDisbursement.actor)} · {formatTimestamp(data.originatingDisbursement.at)}</p></div>
        <div><h3 className="font-semibold">Status history</h3><ol className="mt-2 space-y-2">{data.statusHistory.map((event) => <li key={event.sequenceNumber} className="rounded-md border p-3 text-sm"><span className="font-medium">{actionLabels[event.action] ?? 'Activity unavailable'}</span> · {servicingActorLabel(event.actor)} · {formatTimestamp(event.occurredAt)}<span className="block text-muted-foreground">{event.fromStatus ? `${accountStatusLabel(event.fromStatus)} → ` : ''}{accountStatusLabel(event.toStatus)}</span></li>)}</ol></div>
        {data.settlement ? <div><h3 className="font-semibold">Approved settlement</h3><p className="text-sm">Approved by {servicingActorLabel(data.settlement.actor)} · {formatTimestamp(data.settlement.at)}</p></div> : null}
        {data.closure ? <div><h3 className="font-semibold">Administrative closure</h3><p className="text-sm">Closed by {servicingActorLabel(data.closure.actor)} · {formatTimestamp(data.closure.at)}</p></div> : null}
      </> : null}
      {!pending && !error && !data ? <Alert variant="warning"><AlertTitle>Account activity unavailable</AlertTitle><AlertDescription>Refresh this loan account to inspect its history.</AlertDescription></Alert> : null}
    </CardContent>
  </Card>
}
