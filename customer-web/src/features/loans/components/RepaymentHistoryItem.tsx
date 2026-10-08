import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import type { RepaymentHistoryItem as RepaymentHistoryItemData } from '@/features/loans/loan-api'
import {
  installmentStatusPresentation,
  loanAccountStatusPresentation,
  repaymentAllocationComponentLabel,
} from '@/features/loans/loan-presentation'
import { formatDateOnly, formatTimestamp } from '@/lib/format/presentation'

function BalanceFact({ label, value }: { label: string; value: number }) {
  return <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">{label}</dt><dd className="mt-2 min-w-0 text-base leading-6"><MoneyDisplay value={value} /></dd></div>
}

export function RepaymentHistoryItem({ item }: { item: RepaymentHistoryItemData }) {
  return (
    <article className="min-w-0 space-y-6 py-6 [overflow-wrap:anywhere]">
      <header className="space-y-6">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm leading-5 text-muted-foreground">Received amount</p>
            <h3 className="mt-2 min-w-0"><MoneyDisplay value={item.receivedAmount} emphasis="primary" /></h3>
          </div>
          <div className="min-w-0 space-y-2">
            <p className="text-sm leading-5 text-muted-foreground">Loan status after this payment</p>
            <StatusBadge presentation={loanAccountStatusPresentation(item.resultingLoanAccountStatus)} />
          </div>
        </div>
        <dl className="grid min-w-0 gap-6 sm:grid-cols-2">
          <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">Effective payment date</dt><dd className="mt-2 text-base font-medium leading-6">{formatDateOnly(item.paymentValueDate)}</dd></div>
          <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">Recorded</dt><dd className="mt-2 text-base font-medium leading-6">{formatTimestamp(item.recordedAt)}</dd></div>
        </dl>
      </header>
      <div className="space-y-6">
        <section aria-label="Balance after this repayment" className="space-y-4 border-t border-border pt-6">
          <div className="space-y-2">
            <h4 className="text-base font-semibold leading-6">Balance after this repayment</h4>
            <p className="text-sm leading-5 text-muted-foreground">
              Balance updated {formatDateOnly(item.accountBalance.servicingEvaluationDate)}
            </p>
          </div>
          <dl className="grid min-w-0 gap-x-6 gap-y-4 sm:grid-cols-2">
            <BalanceFact label="Total paid" value={item.accountBalance.totalPaid} />
            <BalanceFact label="Total outstanding" value={item.accountBalance.totalOutstanding} />
            <BalanceFact label="Principal outstanding" value={item.accountBalance.principalOutstanding} />
            <BalanceFact label="Interest outstanding" value={item.accountBalance.interestOutstanding} />
          </dl>
        </section>

        <details className="min-w-0 border-t border-border">
          <summary className="min-h-11 cursor-pointer py-3 text-base font-semibold leading-6">How this payment was applied</summary>
          {item.allocations.length ? (
            <ol className="mt-4 space-y-3">
              {item.allocations.map((allocation) => (
                <li key={`${allocation.sequence}-${allocation.repaymentScheduleItemId}`} className="flex min-w-0 flex-wrap justify-between gap-2 border-t border-border py-4 first:border-t-0 first:pt-0">
                  <span className="min-w-0 text-base leading-6">Installment {allocation.installmentNumber} · {repaymentAllocationComponentLabel(allocation.component)}</span>
                  <MoneyDisplay value={allocation.allocatedAmount} className="text-base leading-6" />
                </li>
              ))}
            </ol>
          ) : <p className="mt-2 text-sm leading-5 text-muted-foreground">The payment breakdown is unavailable.</p>}
        </details>

        {item.affectedInstallments.length ? (
          <details className="min-w-0 border-t border-border">
            <summary className="min-h-11 cursor-pointer py-3 text-base font-semibold leading-6">Installments after this payment</summary>
            <div className="mt-4 divide-y divide-border">
              {item.affectedInstallments.map((outcome) => (
                <section key={outcome.repaymentScheduleItemId} aria-label={`Installment ${outcome.installmentNumber} outcome`} className="min-w-0 space-y-4 py-6 first:pt-0">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <h4 className="min-w-0 text-base font-semibold leading-6">Installment {outcome.installmentNumber} · due {formatDateOnly(outcome.dueDate)}</h4>
                    <StatusBadge presentation={installmentStatusPresentation(outcome.resultingStatus)} />
                  </div>
                  <p className="text-sm leading-5 text-muted-foreground">
                    Previous: {installmentStatusPresentation(outcome.previousStatus).label}. {outcome.statusChanged ? 'Status changed.' : 'Status unchanged.'}
                  </p>
                  <dl className="grid min-w-0 gap-x-6 gap-y-4 sm:grid-cols-2">
                    <BalanceFact label="Total paid" value={outcome.totalPaid} />
                    <BalanceFact label="Total outstanding" value={outcome.totalOutstanding} />
                    <BalanceFact label="Principal outstanding" value={outcome.principalOutstanding} />
                    <BalanceFact label="Interest outstanding" value={outcome.interestOutstanding} />
                  </dl>
                </section>
              ))}
            </div>
          </details>
        ) : null}
      </div>
    </article>
  )
}
