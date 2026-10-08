import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import type { LoanAccount } from '@/features/loans/loan-api'
import { formatDateOnly, formatTimestamp } from '@/lib/format/presentation'

function MoneyFact({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-0 border-t border-border py-4">
      <dt className="text-sm leading-5 text-muted-foreground">{label}</dt>
      <dd className="mt-2 min-w-0 text-base leading-6"><MoneyDisplay value={value} /></dd>
    </div>
  )
}

export function RepaymentSummary({ account }: { account: LoanAccount }) {
  const { servicing } = account
  return (
    <section aria-labelledby="repayment-summary-heading" className="min-w-0 space-y-6 [overflow-wrap:anywhere]">
      <div className="space-y-2">
        <h2 id="repayment-summary-heading" className="type-section">Repayment summary</h2>
        <p className="text-sm leading-5 text-muted-foreground">
          Balances as of {formatDateOnly(servicing.servicingEvaluationDate)}
        </p>
      </div>
      <div className="space-y-8">
        <section aria-labelledby="originated-terms-heading" className="space-y-4">
          <h3 id="originated-terms-heading" className="text-base font-semibold leading-6">Loan terms</h3>
          <dl className="grid min-w-0 gap-x-6 border-b border-border sm:grid-cols-2">
            <MoneyFact label="Amount borrowed (principal)" value={account.originatedPrincipal} />
            <MoneyFact label="Total interest" value={account.totalInterest} />
            <MoneyFact label="Total fee" value={account.totalFee} />
            <MoneyFact label="Total repayment" value={account.totalRepayment} />
          </dl>
          <p className="text-sm leading-5 text-muted-foreground">
            Approved term: <span className="font-medium text-foreground">{account.approvedTermMonths} {account.approvedTermMonths === 1 ? 'month' : 'months'}</span>
          </p>
        </section>

        <section aria-labelledby="servicing-totals-heading" className="space-y-4">
          <h3 id="servicing-totals-heading" className="text-base font-semibold leading-6">Current balances</h3>
          <dl className="grid min-w-0 gap-x-6 sm:grid-cols-2">
            <MoneyFact label="Amount paid" value={servicing.totalPaid} />
            <MoneyFact label="Amount remaining" value={servicing.totalOutstanding} />
          </dl>
          <dl className="grid min-w-0 gap-x-6 border-b border-border sm:grid-cols-2">
            <MoneyFact label="Principal paid" value={servicing.principalPaid} />
            <MoneyFact label="Principal outstanding" value={servicing.principalOutstanding} />
            <MoneyFact label="Interest paid" value={servicing.interestPaid} />
            <MoneyFact label="Interest outstanding" value={servicing.interestOutstanding} />
            <MoneyFact label="Fee paid" value={servicing.feePaid} />
            <MoneyFact label="Fee outstanding" value={servicing.feeOutstanding} />
          </dl>
        </section>

        <dl className="grid min-w-0 gap-6 sm:grid-cols-2">
          <div className="min-w-0">
            <dt className="text-sm leading-5 text-muted-foreground">Last effective payment date</dt>
            <dd className="mt-2 text-base font-medium leading-6">
              {servicing.lastPaymentValueDate ? formatDateOnly(servicing.lastPaymentValueDate) : 'No payment recorded'}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="text-sm leading-5 text-muted-foreground">Last payment recorded</dt>
            <dd className="mt-2 text-base font-medium leading-6">
              {servicing.lastPaymentRecordedAt ? formatTimestamp(servicing.lastPaymentRecordedAt) : 'No payment recorded'}
            </dd>
          </div>
        </dl>
      </div>
    </section>
  )
}
