import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { RepaymentHistoryItem as RepaymentHistoryItemData } from '@/features/loans/loan-api'
import {
  installmentStatusPresentation,
  loanAccountStatusPresentation,
  repaymentAllocationComponentLabel,
} from '@/features/loans/loan-presentation'
import { formatDateOnly, formatTimestamp } from '@/lib/format/presentation'

function BalanceFact({ label, value }: { label: string; value: number }) {
  return <div><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1"><MoneyDisplay value={value} /></dd></div>
}

export function RepaymentHistoryItem({ item }: { item: RepaymentHistoryItemData }) {
  return (
    <Card>
      <CardHeader className="gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">Received amount</p>
            <CardTitle className="mt-1"><MoneyDisplay value={item.receivedAmount} /></CardTitle>
          </div>
          <StatusBadge presentation={loanAccountStatusPresentation(item.resultingLoanAccountStatus)} />
        </div>
        <p className="text-sm text-muted-foreground">
          Value date {formatDateOnly(item.paymentValueDate)} · Recorded {formatTimestamp(item.recordedAt)}
        </p>
      </CardHeader>
      <CardContent className="space-y-5">
        <section aria-label="Balance after this repayment" className="space-y-3">
          <h3 className="font-semibold">Balance after this repayment</h3>
          <dl className="grid grid-cols-2 gap-4 rounded-md bg-background p-4 sm:grid-cols-4">
            <BalanceFact label="Total paid" value={item.accountBalance.totalPaid} />
            <BalanceFact label="Total outstanding" value={item.accountBalance.totalOutstanding} />
            <BalanceFact label="Principal outstanding" value={item.accountBalance.principalOutstanding} />
            <BalanceFact label="Interest outstanding" value={item.accountBalance.interestOutstanding} />
          </dl>
          <p className="text-xs text-muted-foreground">
            Servicing state evaluated {formatDateOnly(item.accountBalance.servicingEvaluationDate)}
          </p>
        </section>

        <details className="rounded-md border border-border bg-background px-4 py-3">
          <summary className="cursor-pointer font-semibold">Allocation detail</summary>
          {item.allocations.length ? (
            <ol className="mt-4 space-y-3">
              {item.allocations.map((allocation) => (
                <li key={`${allocation.sequence}-${allocation.repaymentScheduleItemId}`} className="flex flex-wrap justify-between gap-2 border-t border-border pt-3 first:border-t-0 first:pt-0">
                  <span className="text-sm">Installment {allocation.installmentNumber} · {repaymentAllocationComponentLabel(allocation.component)}</span>
                  <MoneyDisplay value={allocation.allocatedAmount} className="text-sm" />
                </li>
              ))}
            </ol>
          ) : <p className="mt-3 text-sm text-muted-foreground">No allocation detail was returned.</p>}
        </details>

        {item.affectedInstallments.length ? (
          <details className="rounded-md border border-border bg-background px-4 py-3">
            <summary className="cursor-pointer font-semibold">Installment outcomes</summary>
            <div className="mt-4 space-y-4">
              {item.affectedInstallments.map((outcome) => (
                <section key={outcome.repaymentScheduleItemId} aria-label={`Installment ${outcome.installmentNumber} outcome`} className="space-y-2 border-t border-border pt-4 first:border-t-0 first:pt-0">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-medium">Installment {outcome.installmentNumber} · due {formatDateOnly(outcome.dueDate)}</p>
                    <StatusBadge presentation={installmentStatusPresentation(outcome.resultingStatus)} />
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Previous: {installmentStatusPresentation(outcome.previousStatus).label}. {outcome.statusChanged ? 'Status changed.' : 'Status unchanged.'}
                  </p>
                  <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
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
      </CardContent>
    </Card>
  )
}
