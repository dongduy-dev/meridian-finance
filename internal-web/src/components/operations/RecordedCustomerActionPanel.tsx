import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatTimestamp } from '@/lib/format/presentation'

export function RecordedCustomerActionPanel({ title, value }: {
  title: string
  value: {
    action: string
    recordedBy: { displayName: string; email: string }
    recordedAt: string
    evidence: { documentVersionId: string; versionNumber: number }
  }
}) {
  const labels: Record<string, string> = {
    ACCEPT: 'Accepted', DECLINE: 'Declined',
    CUSTOMER_REQUESTED_CANCELLATION: 'Customer-requested cancellation',
  }
  return <Card><CardHeader><CardTitle>{title}</CardTitle></CardHeader><CardContent>
    <dl className="grid gap-4 sm:grid-cols-2">
      <div><dt className="text-sm text-muted-foreground">Customer decision</dt><dd className="mt-1 font-semibold">{labels[value.action] ?? 'Decision unavailable'}</dd></div>
      <div><dt className="text-sm text-muted-foreground">Customer action</dt><dd className="mt-1">Customer decision recorded from signed evidence</dd></div>
      <div><dt className="text-sm text-muted-foreground">Recorded by</dt><dd className="mt-1 font-semibold">{value.recordedBy.displayName}<span className="block break-all text-sm font-normal text-muted-foreground">{value.recordedBy.email}</span></dd></div>
      <div><dt className="text-sm text-muted-foreground">Recorded at</dt><dd className="mt-1">{formatTimestamp(value.recordedAt)}</dd></div>
      <div className="sm:col-span-2"><dt className="text-sm text-muted-foreground">Signed evidence</dt><dd className="mt-1 break-all">Version {value.evidence.versionNumber} · document reference {value.evidence.documentVersionId}</dd></div>
    </dl>
  </CardContent></Card>
}
