import {
  ArrowRight,
  CheckCircle2,
  FileSearch,
  Landmark,
  Shapes,
} from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useOwnCustomerQuery, AccountReadinessCard } from '@/features/account'
import { ApplicationSummary } from '@/features/applications/components/ApplicationSummary'
import { RequiredActionCard } from '@/features/applications/components/RequiredActionCard'
import { useOwnApplicationsQuery } from '@/features/applications/application-queries'
import { LoanProductCard } from '@/features/loan-products/components/LoanProductCard'
import { useLoanProductsQuery } from '@/features/loan-products/loan-product-queries'
import { LoanAccountCard } from '@/features/loans/components/LoanAccountCard'
import { useOwnLoanAccountsQuery } from '@/features/loans/loan-queries'

function SectionHeading({ id, title, description }: { id: string; title: string; description: string }) {
  return (
    <div>
      <h2 id={id} className="text-xl font-semibold tracking-tight text-foreground">{title}</h2>
      <p className="mt-1 text-sm leading-6 text-muted-foreground">{description}</p>
    </div>
  )
}

function CardSkeletons({ count = 2 }: { count?: number }) {
  return (
    <div className="grid gap-4 md:grid-cols-2" role="status" aria-label="Loading section">
      {Array.from({ length: count }, (_, index) => (
        <Skeleton key={index} className="h-56 w-full" />
      ))}
    </div>
  )
}

export function DashboardPage() {
  const customerQuery = useOwnCustomerQuery()
  const applicationQuery = useOwnApplicationsQuery()
  const loanQuery = useOwnLoanAccountsQuery()
  const productQuery = useLoanProductsQuery()

  const requiredActions = applicationQuery.data?.filter(
    (application) => application.requiredAction !== 'NONE',
  )
  const activeApplications = applicationQuery.data?.filter(
    (application) => application.lifecycleActive,
  )
  const activeLoanAccounts = loanQuery.data?.filter((account) => account.servicingActive)

  return (
    <div className="space-y-10">
      <PageHeader
        eyebrow="Customer overview"
        title="Dashboard"
        description="Review account readiness, Customer work, active lending, and Meridian products from one calm overview."
      />

      <section aria-labelledby="account-readiness-heading" className="space-y-4">
        <SectionHeading
          id="account-readiness-heading"
          title="Customer account"
          description="Keep the Customer account facts needed by later lending journeys up to date."
        />
        {customerQuery.isPending ? (
          <Skeleton className="h-72 w-full" role="status" aria-label="Loading account readiness" />
        ) : null}
        {customerQuery.isError ? (
          <QueryErrorFeedback
            error={customerQuery.error}
            title="Account readiness could not be loaded"
            onRetry={() => void customerQuery.refetch()}
          />
        ) : null}
        {customerQuery.data ? <AccountReadinessCard customer={customerQuery.data} /> : null}
      </section>

      <section aria-labelledby="required-work-heading" className="space-y-4">
        <SectionHeading
          id="required-work-heading"
          title="Required Customer work"
          description="These summaries show Customer work reported by Meridian."
        />
        {applicationQuery.isPending ? <CardSkeletons /> : null}
        {applicationQuery.isError ? (
          <QueryErrorFeedback
            error={applicationQuery.error}
            title="Required Customer work could not be loaded"
            onRetry={() => void applicationQuery.refetch()}
          />
        ) : null}
        {requiredActions?.length ? (
          <div className="grid gap-4 lg:grid-cols-2">
            {requiredActions.map((application) => (
              <RequiredActionCard key={application.loanApplicationId} application={application} />
            ))}
          </div>
        ) : null}
        {requiredActions?.length === 0 ? (
          <EmptyState
            icon={CheckCircle2}
            title="You're up to date"
            description="Meridian has not reported any supported Customer action for your current applications."
            className="min-h-52"
          />
        ) : null}
      </section>

      <section aria-labelledby="active-applications-heading" className="space-y-4">
        <SectionHeading
          id="active-applications-heading"
          title="Active applications"
          description="Applications currently in an active origination lifecycle, as identified by Meridian."
        />
        {applicationQuery.isPending ? <CardSkeletons /> : null}
        {applicationQuery.isError ? (
          <QueryErrorFeedback
            error={applicationQuery.error}
            title="Active applications could not be loaded"
            onRetry={() => void applicationQuery.refetch()}
          />
        ) : null}
        {activeApplications?.length ? (
          <div className="grid gap-4">
            {activeApplications.slice(0, 3).map((application) => (
              <ApplicationSummary key={application.loanApplicationId} application={application} />
            ))}
          </div>
        ) : null}
        {activeApplications?.length === 0 ? (
          <EmptyState
            icon={FileSearch}
            title="No active applications"
            description="No application is currently in an active origination lifecycle. You can explore available products when you are ready."
            action={<Button variant="secondary" asChild><Link to="/products">Explore products</Link></Button>}
            className="min-h-52"
          />
        ) : null}
      </section>

      <section aria-labelledby="active-loans-heading" className="space-y-4">
        <SectionHeading
          id="active-loans-heading"
          title="Active LoanAccounts"
          description="Accounts Meridian currently identifies as active servicing work."
        />
        {loanQuery.isPending ? <CardSkeletons /> : null}
        {loanQuery.isError ? (
          <QueryErrorFeedback
            error={loanQuery.error}
            title="Active LoanAccounts could not be loaded"
            onRetry={() => void loanQuery.refetch()}
          />
        ) : null}
        {activeLoanAccounts?.length ? (
          <div className="grid gap-4">
            {activeLoanAccounts.slice(0, 3).map((account) => (
              <LoanAccountCard key={account.loanAccountId} account={account} />
            ))}
          </div>
        ) : null}
        {activeLoanAccounts?.length === 0 ? (
          <EmptyState
            icon={Landmark}
            title="No active LoanAccounts"
            description="No active or overdue LoanAccount is currently being serviced."
            className="min-h-52"
          />
        ) : null}
      </section>

      <section aria-labelledby="product-discovery-heading" className="space-y-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <SectionHeading
            id="product-discovery-heading"
            title="Explore Meridian products"
            description="Compare the current product policies returned by Meridian."
          />
          <Button variant="secondary" asChild>
            <Link to="/products">View all products <ArrowRight aria-hidden="true" /></Link>
          </Button>
        </div>
        {productQuery.isPending ? <CardSkeletons count={3} /> : null}
        {productQuery.isError ? (
          <QueryErrorFeedback
            error={productQuery.error}
            title="Products could not be loaded"
            onRetry={() => void productQuery.refetch()}
          />
        ) : null}
        {productQuery.data?.length ? (
          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {productQuery.data.slice(0, 3).map((product) => (
              <LoanProductCard key={product.productCode} product={product} />
            ))}
          </div>
        ) : null}
        {productQuery.data?.length === 0 ? (
          <EmptyState
            icon={Shapes}
            title="No products available"
            description="Meridian is not currently returning an active product catalogue."
            className="min-h-52"
          />
        ) : null}
      </section>
    </div>
  )
}
