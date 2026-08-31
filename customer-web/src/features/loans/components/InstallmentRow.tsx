import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { FinalRepaymentScheduleItem } from '@/features/loans/loan-api'
import { installmentStatusPresentation } from '@/features/loans/loan-presentation'
import { formatDateOnly, formatTimestamp } from '@/lib/format/presentation'

function Amount({ label, value }: { label: string; value: number }) {
  return <div><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1"><MoneyDisplay value={value} /></dd></div>
}

export function InstallmentRow({ item }: { item: FinalRepaymentScheduleItem }) {
  const { servicing } = item
  return (
    <Card>
      <CardHeader className="gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="text-lg">Installment {item.installmentNumber}</CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">Due {formatDateOnly(item.dueDate)}</p>
          </div>
          <StatusBadge presentation={installmentStatusPresentation(servicing.status)} />
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <section aria-label={`Installment ${item.installmentNumber} scheduled terms`} className="space-y-3">
          <h4 className="text-sm font-semibold">Scheduled terms</h4>
          <dl className="grid grid-cols-2 gap-4 rounded-md bg-background p-4 sm:grid-cols-4">
            <Amount label="Principal due" value={item.principalDue} />
            <Amount label="Interest due" value={item.interestDue} />
            <Amount label="Fee due" value={item.feeDue} />
            <Amount label="Total due" value={item.totalDue} />
          </dl>
        </section>
        <section aria-label={`Installment ${item.installmentNumber} servicing state`} className="space-y-3 border-t border-border pt-5">
          <div>
            <h4 className="text-sm font-semibold">Servicing state</h4>
            <p className="mt-1 text-xs text-muted-foreground">Evaluated {formatDateOnly(servicing.statusEvaluationDate)}</p>
          </div>
          <dl className="grid grid-cols-2 gap-4 rounded-md bg-background p-4 sm:grid-cols-4">
            <Amount label="Principal paid" value={servicing.principalPaid} />
            <Amount label="Interest paid" value={servicing.interestPaid} />
            <Amount label="Fee paid" value={servicing.feePaid} />
            <Amount label="Total paid" value={servicing.totalPaid} />
            <Amount label="Principal outstanding" value={servicing.principalOutstanding} />
            <Amount label="Interest outstanding" value={servicing.interestOutstanding} />
            <Amount label="Fee outstanding" value={servicing.feeOutstanding} />
            <Amount label="Total outstanding" value={servicing.totalOutstanding} />
          </dl>
          <dl className="grid gap-2 text-xs text-muted-foreground sm:grid-cols-2">
            <div><dt>Last payment value date</dt><dd className="mt-1 font-medium text-foreground">{servicing.lastPaymentValueDate ? formatDateOnly(servicing.lastPaymentValueDate) : 'No payment recorded'}</dd></div>
            <div><dt>Last payment recorded</dt><dd className="mt-1 font-medium text-foreground">{servicing.lastPaymentRecordedAt ? formatTimestamp(servicing.lastPaymentRecordedAt) : 'No payment recorded'}</dd></div>
          </dl>
        </section>
      </CardContent>
    </Card>
  )
}
