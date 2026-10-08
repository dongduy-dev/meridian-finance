import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import type { FinalRepaymentScheduleItem } from '@/features/loans/loan-api'
import { installmentStatusPresentation } from '@/features/loans/loan-presentation'
import { formatDateOnly, formatTimestamp } from '@/lib/format/presentation'

function Amount({ label, value }: { label: string; value: number }) {
  return <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">{label}</dt><dd className="mt-2 min-w-0 text-base leading-6"><MoneyDisplay value={value} /></dd></div>
}

export function InstallmentRow({ item }: { item: FinalRepaymentScheduleItem }) {
  const { servicing } = item
  return (
    <article className="min-w-0 space-y-6 py-6 [overflow-wrap:anywhere]">
      <header>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="type-section">Installment {item.installmentNumber}</h3>
            <p className="mt-2 text-sm leading-5 text-muted-foreground">Due {formatDateOnly(item.dueDate)}</p>
          </div>
          <StatusBadge presentation={installmentStatusPresentation(servicing.status)} />
        </div>
      </header>
      <div className="space-y-6">
        <section aria-label={`Installment ${item.installmentNumber} scheduled terms`} className="space-y-4">
          <h4 className="text-base font-semibold leading-6">Scheduled terms</h4>
          <dl className="grid min-w-0 gap-x-6 gap-y-4 sm:grid-cols-2">
            <Amount label="Principal due" value={item.principalDue} />
            <Amount label="Interest due" value={item.interestDue} />
            <Amount label="Fee due" value={item.feeDue} />
            <Amount label="Total due" value={item.totalDue} />
          </dl>
        </section>
        <section aria-label={`Installment ${item.installmentNumber} payment status`} className="space-y-4 border-t border-border pt-6">
          <div>
            <h4 className="text-base font-semibold leading-6">Payment status</h4>
            <p className="mt-2 text-sm leading-5 text-muted-foreground">Updated {formatDateOnly(servicing.statusEvaluationDate)}</p>
          </div>
          <dl className="grid min-w-0 gap-x-6 gap-y-4 sm:grid-cols-2">
            <Amount label="Principal paid" value={servicing.principalPaid} />
            <Amount label="Interest paid" value={servicing.interestPaid} />
            <Amount label="Fee paid" value={servicing.feePaid} />
            <Amount label="Total paid" value={servicing.totalPaid} />
            <Amount label="Principal outstanding" value={servicing.principalOutstanding} />
            <Amount label="Interest outstanding" value={servicing.interestOutstanding} />
            <Amount label="Fee outstanding" value={servicing.feeOutstanding} />
            <Amount label="Total outstanding" value={servicing.totalOutstanding} />
          </dl>
          <dl className="grid min-w-0 gap-6 border-t border-border pt-6 sm:grid-cols-2">
            <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">Last effective payment date</dt><dd className="mt-2 text-base font-medium leading-6">{servicing.lastPaymentValueDate ? formatDateOnly(servicing.lastPaymentValueDate) : 'No payment recorded'}</dd></div>
            <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">Last payment recorded</dt><dd className="mt-2 text-base font-medium leading-6">{servicing.lastPaymentRecordedAt ? formatTimestamp(servicing.lastPaymentRecordedAt) : 'No payment recorded'}</dd></div>
          </dl>
        </section>
      </div>
    </article>
  )
}
