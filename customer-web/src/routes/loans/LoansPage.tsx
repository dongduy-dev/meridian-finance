import { Landmark } from 'lucide-react'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { Skeleton } from '@/components/ui/skeleton'
import { LoanAccountCard } from '@/features/loans/components/LoanAccountCard'
import { useOwnLoanAccountsQuery } from '@/features/loans/loan-queries'

export function LoansPage() {
  const accountsQuery = useOwnLoanAccountsQuery()

  return (
    <div className="space-y-[var(--section-editorial)]">
      <PageHeader
        headingRole="browse"
        eyebrow="Loan overview"
        title="Your loans"
        description="View your balance, repayment schedule, and payment history."
      />
      {accountsQuery.isPending ? (
        <div className="space-y-6 border-t border-border pt-6" role="status" aria-label="Loading loans">
          <Skeleton className="h-72" />
          <Skeleton className="h-72" />
        </div>
      ) : null}
      {accountsQuery.isError ? (
        <QueryErrorFeedback
          error={accountsQuery.error}
          title="Loans could not be loaded"
          onRetry={() => void accountsQuery.refetch()}
        />
      ) : null}
      {accountsQuery.data?.length ? (
        <section aria-label="Your loans" className="divide-y divide-border border-y border-border">
          {accountsQuery.data.map((account) => (
            <LoanAccountCard key={account.loanAccountId} account={account} />
          ))}
        </section>
      ) : null}
      {accountsQuery.isSuccess && accountsQuery.data?.length === 0 ? (
        <EmptyState
          icon={Landmark}
          title="No loans yet"
          description="Your loans will appear here after your loan funds have been sent."
        />
      ) : null}
    </div>
  )
}
