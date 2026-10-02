import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatTimestamp } from '@/lib/format/presentation'
import type { AccountingCaseContext } from '../api/accounting-context'

function ActorEvent({ label, event }: {
  label: string
  event: NonNullable<AccountingCaseContext['handoff']['contractPrepared']>
}) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 font-semibold">{event.actor.displayName}<span className="block break-all text-sm font-normal text-muted-foreground">{event.actor.email}</span><span className="block text-sm font-normal">{formatTimestamp(event.at)}</span></dd></div>
}

export function AccountingCaseContextPanel({ context, contractVersion }: {
  context: AccountingCaseContext
  contractVersion: number | null
}) {
  const acknowledgment = context.handoff.customerAcknowledgment
  return <div className="grid gap-5 lg:grid-cols-2">
    <Card><CardHeader><CardTitle>Customer</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2"><div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Customer number</dt><dd className="mt-1 font-semibold">{context.customer.customerNumber}</dd></div><div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Full name</dt><dd className="mt-1 font-semibold">{context.customer.fullName}</dd></div></dl></CardContent></Card>
    <Card><CardHeader><CardTitle>Approval and contract history</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2">
      <ActorEvent label="Approved by" event={context.handoff.approved} />
      {context.handoff.contractPrepared ? <ActorEvent label="Contract prepared by" event={context.handoff.contractPrepared} /> : null}
      {acknowledgment ? <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Customer acknowledgment</dt><dd className="mt-1 font-semibold">{acknowledgment.mode === 'CUSTOMER_SELF_SERVICE' ? 'Customer — self-service' : acknowledgment.mode === 'STAFF_RECORDED_CUSTOMER_EVIDENCE' && acknowledgment.recordedBy ? <>Customer acknowledgment recorded by {acknowledgment.recordedBy.displayName}<span className="block break-all text-sm font-normal text-muted-foreground">{acknowledgment.recordedBy.email}</span></> : 'Acknowledgment method unavailable'}<span className="block text-sm font-normal">{acknowledgment.mode === 'STAFF_RECORDED_CUSTOMER_EVIDENCE' ? 'Recorded at' : 'Acknowledged at'}: {formatTimestamp(acknowledgment.at)}</span>{contractVersion ? <span className="block text-sm font-normal">Acknowledged contract version {contractVersion}</span> : null}{acknowledgment.mode === 'STAFF_RECORDED_CUSTOMER_EVIDENCE' && acknowledgment.evidenceDocumentVersionId && contractVersion ? <span className="block break-all text-sm font-normal text-muted-foreground">Signed document for contract version {contractVersion} · document reference {acknowledgment.evidenceDocumentVersionId}</span> : null}</dd></div> : null}
      {context.handoff.readinessConfirmed ? <ActorEvent label="Readiness confirmed by" event={context.handoff.readinessConfirmed} /> : null}
      {context.handoff.disbursementConfirmed ? <ActorEvent label="Disbursement confirmed by" event={context.handoff.disbursementConfirmed} /> : null}
    </dl></CardContent></Card>
  </div>
}
