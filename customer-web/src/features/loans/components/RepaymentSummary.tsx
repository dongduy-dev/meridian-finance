import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { LoanAccount } from '@/features/loans/loan-api'
import { formatDateOnly, formatTimestamp } from '@/lib/format/presentation'

function MoneyFact({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-0 rounded-md bg-background px-4 py-3">
      <dt className="text-xs font-semibold tracking-[0.08em] text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-1"><MoneyDisplay value={value} /></dd>
    </div>
  )
}

export function RepaymentSummary({ account }: { account: LoanAccount }) {
  const { servicing } = account
  return (
    <Card>
      <CardHeader>
        <CardTitle>Repayment summary</CardTitle>
        <p className="text-sm text-muted-foreground">
          Servicing state as of {formatDateOnly(servicing.servicingEvaluationDate)}
        </p>
      </CardHeader>
      <CardContent className="space-y-5">
        <section aria-labelledby="originated-terms-heading" className="space-y-3">
          <h3 id="originated-terms-heading" className="font-semibold">Originated terms</h3>
          <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <MoneyFact label="Originated principal" value={account.originatedPrincipal} />
            <MoneyFact label="Total interest" value={account.totalInterest} />
            <MoneyFact label="Total fee" value={account.totalFee} />
            <MoneyFact label="Total repayment" value={account.totalRepayment} />
          </dl>
          <p className="text-sm text-muted-foreground">
            Approved term: <span className="font-medium text-foreground">{account.approvedTermMonths} {account.approvedTermMonths === 1 ? 'month' : 'months'}</span>
          </p>
        </section>

        <section aria-labelledby="servicing-totals-heading" className="space-y-3 border-t border-border pt-5">
          <h3 id="servicing-totals-heading" className="font-semibold">Current servicing totals</h3>
          <dl className="grid gap-3 sm:grid-cols-2">
            <MoneyFact label="Total paid" value={servicing.totalPaid} />
            <MoneyFact label="Total outstanding" value={servicing.totalOutstanding} />
          </dl>
          <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <MoneyFact label="Principal paid" value={servicing.principalPaid} />
            <MoneyFact label="Principal outstanding" value={servicing.principalOutstanding} />
            <MoneyFact label="Interest paid" value={servicing.interestPaid} />
            <MoneyFact label="Interest outstanding" value={servicing.interestOutstanding} />
            <MoneyFact label="Fee paid" value={servicing.feePaid} />
            <MoneyFact label="Fee outstanding" value={servicing.feeOutstanding} />
          </dl>
        </section>

        <dl className="grid gap-3 border-t border-border pt-5 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-muted-foreground">Last payment value date</dt>
            <dd className="mt-1 font-medium">
              {servicing.lastPaymentValueDate ? formatDateOnly(servicing.lastPaymentValueDate) : 'No payment recorded'}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Last payment recorded</dt>
            <dd className="mt-1 font-medium">
              {servicing.lastPaymentRecordedAt ? formatTimestamp(servicing.lastPaymentRecordedAt) : 'No payment recorded'}
            </dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  )
}
