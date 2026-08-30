import { CalendarClock, CalendarRange, FileText } from 'lucide-react'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { CustomerApplicationSummary } from '@/features/applications/application-api'
import { applicationStatusPresentation } from '@/features/applications/application-presentation'
import { productNameForCode } from '@/features/loan-products/loan-product-presentation'
import { formatTimestamp } from '@/lib/format/presentation'

export function ApplicationSummary({ application }: { application: CustomerApplicationSummary }) {
  return (
    <Card className="min-w-0">
      <CardHeader className="gap-3">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-semibold tracking-[0.12em] text-muted-foreground uppercase">
              {productNameForCode(application.productCode)}
            </p>
            <CardTitle className="mt-1 break-all text-lg">{application.applicationNumber}</CardTitle>
          </div>
          <StatusBadge presentation={applicationStatusPresentation(application.status)} />
        </div>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-4 text-sm sm:grid-cols-3">
          <div className="min-w-0">
            <dt className="flex items-center gap-2 text-muted-foreground">
              <FileText aria-hidden="true" className="size-4" /> Requested amount
            </dt>
            <dd className="mt-1"><MoneyDisplay value={application.requestedAmount} /></dd>
          </div>
          <div className="min-w-0">
            <dt className="flex items-center gap-2 text-muted-foreground">
              <CalendarRange aria-hidden="true" className="size-4" /> Requested term
            </dt>
            <dd className="mt-1 font-semibold tabular-nums">
              {application.requestedTermMonths} {application.requestedTermMonths === 1 ? 'month' : 'months'}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="flex items-center gap-2 text-muted-foreground">
              <CalendarClock aria-hidden="true" className="size-4" /> Submitted
            </dt>
            <dd className="mt-1 break-words font-medium">{formatTimestamp(application.submittedAt)}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  )
}
