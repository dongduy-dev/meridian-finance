import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { formatTimestamp } from '@/lib/format/presentation'

export function RecordedStaffAction({ outcome, recordedAt, recordedBy, known = true, children }: {
  outcome: string
  recordedAt: string
  recordedBy: { displayName: string; email: string } | null
  known?: boolean
  children?: ReactNode
}) {
  return <Alert variant={known ? 'success' : 'warning'} className="min-w-0 grid-cols-[auto_minmax(0,1fr)] [overflow-wrap:anywhere]">
    {known ? <CheckCircle2 aria-hidden="true" /> : <AlertTriangle aria-hidden="true" />}
    <AlertTitle className="min-w-0 text-base font-bold leading-snug">{outcome}</AlertTitle>
    <AlertDescription className="min-w-0 font-normal">
      <dl className="mt-3 grid min-w-0 gap-4 sm:grid-cols-2">
        <div className="min-w-0"><dt className="text-xs font-medium uppercase tracking-wide text-current/75">Recorded</dt><dd className="mt-1"><time dateTime={recordedAt}>{formatTimestamp(recordedAt)}</time></dd></div>
        <div className="min-w-0"><dt className="text-xs font-medium uppercase tracking-wide text-current/75">Recorded by</dt><dd className="mt-1">{recordedBy ? <><span className="block font-medium">{recordedBy.displayName}</span><span className="block text-sm">{recordedBy.email}</span></> : 'Staff member unavailable'}</dd></div>
      </dl>
      {children ? <div className="mt-4 min-w-0 space-y-2 text-sm font-normal [&_dd]:font-normal">{children}</div> : null}
    </AlertDescription>
  </Alert>
}
