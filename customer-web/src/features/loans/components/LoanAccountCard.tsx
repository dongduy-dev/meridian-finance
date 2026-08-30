import { CalendarClock, FileText, Landmark } from 'lucide-react'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { productNameForCode } from '@/features/loan-products/loan-product-presentation'
import type { CustomerLoanAccountSummary } from '@/features/loans/loan-api'
import { loanAccountStatusPresentation } from '@/features/loans/loan-presentation'
import { formatTimestamp } from '@/lib/format/presentation'

function FinancialFact({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-0 rounded-md bg-background px-4 py-3">
      <dt className="text-xs font-semibold tracking-[0.08em] text-muted-foreground uppercase">
        {label}
      </dt>
      <dd className="mt-1 min-w-0"><MoneyDisplay value={value} /></dd>
    </div>
  )
}

export function LoanAccountCard({ account }: { account: CustomerLoanAccountSummary }) {
  return (
    <Card className="min-w-0">
      <CardHeader className="gap-3">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-semibold tracking-[0.12em] text-muted-foreground uppercase">
              {productNameForCode(account.productCode)}
            </p>
            <CardTitle className="mt-1 break-all text-lg">{account.accountNumber}</CardTitle>
          </div>
          <StatusBadge presentation={loanAccountStatusPresentation(account.status)} />
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <dl className="grid gap-3 sm:grid-cols-3">
          <FinancialFact label="Originated principal" value={account.originatedPrincipal} />
          <FinancialFact label="Total paid" value={account.totalPaid} />
          <FinancialFact label="Total outstanding" value={account.totalOutstanding} />
        </dl>
        <dl className="grid gap-3 border-t border-border pt-4 text-sm text-muted-foreground sm:grid-cols-3">
          <div className="flex min-w-0 items-start gap-2">
            <FileText aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <div className="min-w-0"><dt>Application</dt><dd className="break-all font-medium text-foreground">{account.applicationNumber}</dd></div>
          </div>
          <div className="flex min-w-0 items-start gap-2">
            <Landmark aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <div className="min-w-0"><dt>Account</dt><dd className="break-all font-medium text-foreground">{account.accountNumber}</dd></div>
          </div>
          <div className="flex min-w-0 items-start gap-2">
            <CalendarClock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <div className="min-w-0"><dt>Activated</dt><dd className="break-words font-medium text-foreground">{formatTimestamp(account.activatedAt)}</dd></div>
          </div>
        </dl>
      </CardContent>
    </Card>
  )
}
