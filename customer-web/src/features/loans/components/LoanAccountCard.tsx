import { ArrowRight, CalendarClock, FileText, Landmark } from 'lucide-react'
import { Link } from 'react-router-dom'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { productNameForCode } from '@/features/loan-products/loan-product-presentation'
import type { CustomerLoanAccountSummary } from '@/features/loans/loan-api'
import { loanAccountStatusPresentation } from '@/features/loans/loan-presentation'
import { formatTimestamp } from '@/lib/format/presentation'

function FinancialFact({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-0">
      <dt className="text-sm leading-5 text-muted-foreground">
        {label}
      </dt>
      <dd className="mt-2 min-w-0"><MoneyDisplay value={value} /></dd>
    </div>
  )
}

export function LoanAccountCard({ account, headingLevel = 2 }: {
  account: CustomerLoanAccountSummary
  headingLevel?: 2 | 3
}) {
  const Heading = headingLevel === 3 ? 'h3' : 'h2'
  return (
    <article className="min-w-0 space-y-6 py-6 [overflow-wrap:anywhere]">
      <header>
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm leading-5 text-muted-foreground">
              {productNameForCode(account.productCode)}
            </p>
            <Heading className="type-section mt-2">{account.accountNumber}</Heading>
          </div>
          <StatusBadge presentation={loanAccountStatusPresentation(account.status)} />
        </div>
      </header>
      <div className="space-y-6">
        <dl className="grid gap-4 md:grid-cols-3">
          <FinancialFact label="Amount borrowed (principal)" value={account.originatedPrincipal} />
          <FinancialFact label="Amount paid" value={account.totalPaid} />
          <FinancialFact label="Amount remaining" value={account.totalOutstanding} />
        </dl>
        <dl className="grid gap-4 text-sm leading-5 text-muted-foreground md:grid-cols-3">
          <div className="min-w-0">
            <dt className="flex items-start gap-2"><FileText aria-hidden="true" className="mt-0.5 size-4 shrink-0" />Application</dt>
            <dd className="mt-2 text-base leading-6 font-medium text-foreground">{account.applicationNumber}</dd>
          </div>
          <div className="min-w-0">
            <dt className="flex items-start gap-2"><Landmark aria-hidden="true" className="mt-0.5 size-4 shrink-0" />Account</dt>
            <dd className="mt-2 text-base leading-6 font-medium text-foreground">{account.accountNumber}</dd>
          </div>
          <div className="min-w-0">
            <dt className="flex items-start gap-2"><CalendarClock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />Activated</dt>
            <dd className="mt-2 text-base leading-6 font-medium text-foreground">{formatTimestamp(account.activatedAt)}</dd>
          </div>
        </dl>
      </div>
      <footer className="flex min-w-0 flex-wrap gap-3">
        <Button variant="secondary" asChild>
          <Link to={`/loans/${account.loanApplicationId}`}>
            View loan details <ArrowRight aria-hidden="true" />
          </Link>
        </Button>
      </footer>
    </article>
  )
}
