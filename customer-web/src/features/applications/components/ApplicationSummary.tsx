import { CalendarClock, CalendarRange, FileText } from 'lucide-react'
import type { ReactNode } from 'react'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { applicationStatusPresentation } from '@/features/applications/application-presentation'
import { productNameForCode } from '@/features/loan-products/loan-product-presentation'
import { formatTimestamp } from '@/lib/format/presentation'
import { cn } from '@/lib/cn'

interface ApplicationSummaryView {
  applicationNumber: string
  productCode: string
  requestedAmount: number
  requestedTermMonths: number
  status: string
  submittedAt: string
}

export function ApplicationSummary({
  application,
  action,
  presentation = 'card',
  headingLevel = 2,
}: {
  application: ApplicationSummaryView
  action?: ReactNode
  presentation?: 'card' | 'row'
  headingLevel?: 2 | 3
}) {
  const Heading = headingLevel === 3 ? 'h3' : 'h2'
  return (
    <Card className={cn('min-w-0 [overflow-wrap:anywhere]', presentation === 'row' && 'border-0 bg-transparent py-6')}>
      <CardHeader className={cn('gap-3', presentation === 'row' && 'p-0 sm:p-0')}>
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm leading-5 text-muted-foreground">
              {productNameForCode(application.productCode)}
            </p>
            <Heading className="type-section mt-2 [overflow-wrap:anywhere]">{application.applicationNumber}</Heading>
          </div>
          <StatusBadge presentation={applicationStatusPresentation(application.status)} />
        </div>
      </CardHeader>
      <CardContent className={presentation === 'row' ? 'px-0 pb-0 pt-6 sm:px-0 sm:pb-0' : undefined}>
        <dl className="grid gap-4 md:grid-cols-3">
          <div className="min-w-0">
            <dt className="flex items-start gap-2 text-sm leading-5 text-muted-foreground">
              <FileText aria-hidden="true" className="mt-0.5 size-4 shrink-0" /> Requested amount
            </dt>
            <dd className="mt-2"><MoneyDisplay value={application.requestedAmount} /></dd>
          </div>
          <div className="min-w-0">
            <dt className="flex items-start gap-2 text-sm leading-5 text-muted-foreground">
              <CalendarRange aria-hidden="true" className="mt-0.5 size-4 shrink-0" /> Requested term
            </dt>
            <dd className="mt-2 font-semibold tabular-nums">
              {application.requestedTermMonths} {application.requestedTermMonths === 1 ? 'month' : 'months'}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="flex items-start gap-2 text-sm leading-5 text-muted-foreground">
              <CalendarClock aria-hidden="true" className="mt-0.5 size-4 shrink-0" /> Submitted
            </dt>
            <dd className="mt-2 break-words font-medium">{formatTimestamp(application.submittedAt)}</dd>
          </div>
        </dl>
        {action ? <div className="mt-6 flex flex-wrap gap-3">{action}</div> : null}
      </CardContent>
    </Card>
  )
}
