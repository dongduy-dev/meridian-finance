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
    <div className="space-y-8">
      <PageHeader
        eyebrow="Loan servicing"
        title="Your LoanAccounts"
        description="Review every activated LoanAccount and open its backend-authoritative servicing details."
      />
      {accountsQuery.isPending ? (
        <div className="space-y-4" role="status" aria-label="Loading LoanAccounts">
          <Skeleton className="h-72" />
          <Skeleton className="h-72" />
        </div>
      ) : null}
      {accountsQuery.isError ? (
        <QueryErrorFeedback
          error={accountsQuery.error}
          title="LoanAccounts could not be loaded"
          onRetry={() => void accountsQuery.refetch()}
        />
      ) : null}
      {accountsQuery.data?.length ? (
        <div className="grid gap-5">
          {accountsQuery.data.map((account) => (
            <LoanAccountCard key={account.loanAccountId} account={account} />
          ))}
        </div>
      ) : null}
      {accountsQuery.data?.length === 0 ? (
        <EmptyState
          icon={Landmark}
          title="No LoanAccounts yet"
          description="Meridian has not returned an activated LoanAccount for this Customer."
        />
      ) : null}
    </div>
  )
}
