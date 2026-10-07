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
    <div className="min-w-0 space-y-2 [overflow-wrap:anywhere]">
      <h2 id={id} className="type-editorial-section text-foreground">{title}</h2>
      <p className="max-w-[70ch] text-base leading-6 text-muted-foreground">{description}</p>
    </div>
  )
}

function SummarySkeletons({ count = 2, products = false }: { count?: number; products?: boolean }) {
  return (
    <div className={products ? 'grid gap-[var(--grid-gap)] md:grid-cols-2 xl:grid-cols-3' : 'space-y-6 border-t border-border pt-6'} role="status" aria-label="Loading section">
      {Array.from({ length: count }, (_, index) => (
        <Skeleton key={index} className={products ? 'h-96 w-full' : 'h-56 w-full'} />
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
    <div className="space-y-[var(--section-editorial)]">
      <PageHeader
        headingRole="editorial"
        eyebrow="Overview"
        title="Home"
        description="See what needs your attention, track applications, and review your loans."
      />

      <section aria-labelledby="account-readiness-heading" className="min-w-0 space-y-6">
        <SectionHeading
          id="account-readiness-heading"
          title="Account"
          description="Keep your profile and primary bank account up to date."
        />
        {customerQuery.isPending ? (
          <Skeleton className="h-72 w-full" role="status" aria-label="Loading account details" />
        ) : null}
        {customerQuery.isError ? (
          <QueryErrorFeedback
            error={customerQuery.error}
            title="Account status could not be loaded"
            onRetry={() => void customerQuery.refetch()}
          />
        ) : null}
        {customerQuery.data ? <AccountReadinessCard customer={customerQuery.data} presentation="section" /> : null}
      </section>

      <section aria-labelledby="required-work-heading" className="min-w-0 space-y-6">
        <SectionHeading
          id="required-work-heading"
          title="What needs your attention"
          description="Review the next steps for your applications."
        />
        {applicationQuery.isPending ? <SummarySkeletons /> : null}
        {applicationQuery.isError ? (
          <QueryErrorFeedback
            error={applicationQuery.error}
            title="Next steps could not be loaded"
            onRetry={() => void applicationQuery.refetch()}
          />
        ) : null}
        {requiredActions?.length ? (
          <div className="border-y border-border [&>div+div]:border-t">
            {requiredActions.map((application) => (
              <RequiredActionCard key={application.loanApplicationId} application={application} presentation="row" />
            ))}
          </div>
        ) : null}
        {applicationQuery.isSuccess && requiredActions?.length === 0 ? (
          <EmptyState
            icon={CheckCircle2}
            title="You're up to date online"
            description="There are no digital application tasks waiting for you here. Staff-assisted steps are coordinated with Meridian staff."
          />
        ) : null}
      </section>

      <section aria-labelledby="active-applications-heading" className="min-w-0 space-y-6">
        <SectionHeading
          id="active-applications-heading"
          title="Applications"
          description="Track applications that are still in progress."
        />
        {applicationQuery.isPending ? <SummarySkeletons /> : null}
        {applicationQuery.isError ? (
          <QueryErrorFeedback
            error={applicationQuery.error}
            title="Active applications could not be loaded"
            onRetry={() => void applicationQuery.refetch()}
          />
        ) : null}
        {activeApplications?.length ? (
          <div className="border-y border-border [&>div+div]:border-t">
            {activeApplications.slice(0, 3).map((application) => (
              <ApplicationSummary key={application.loanApplicationId} application={application} presentation="row" headingLevel={3} />
            ))}
          </div>
        ) : null}
        {applicationQuery.isSuccess && activeApplications?.length === 0 ? (
          <EmptyState
            icon={FileSearch}
            title="No active applications"
            description="You have no applications in progress. Explore available loans when you're ready."
            action={<Button variant="secondary" asChild><Link to="/products">Explore products</Link></Button>}
          />
        ) : null}
      </section>

      <section aria-labelledby="active-loans-heading" className="min-w-0 space-y-6">
        <SectionHeading
          id="active-loans-heading"
          title="Your loans"
          description="View loans with an active or overdue balance."
        />
        {loanQuery.isPending ? <SummarySkeletons /> : null}
        {loanQuery.isError ? (
          <QueryErrorFeedback
            error={loanQuery.error}
            title="Loans could not be loaded"
            onRetry={() => void loanQuery.refetch()}
          />
        ) : null}
        {activeLoanAccounts?.length ? (
          <div className="divide-y divide-border border-y border-border">
            {activeLoanAccounts.slice(0, 3).map((account) => (
              <LoanAccountCard key={account.loanAccountId} account={account} headingLevel={3} />
            ))}
          </div>
        ) : null}
        {loanQuery.isSuccess && activeLoanAccounts?.length === 0 ? (
          <EmptyState
            icon={Landmark}
            title="No active loans"
            description="You have no active or overdue loans."
          />
        ) : null}
      </section>

      <section aria-labelledby="product-discovery-heading" className="min-w-0 space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <SectionHeading
            id="product-discovery-heading"
            title="Explore loans"
            description="Compare available amounts, terms, interest, and fees."
          />
          <Button variant="secondary" asChild>
            <Link to="/products">View all products <ArrowRight aria-hidden="true" /></Link>
          </Button>
        </div>
        {productQuery.isPending ? <SummarySkeletons count={3} products /> : null}
        {productQuery.isError ? (
          <QueryErrorFeedback
            error={productQuery.error}
            title="Products could not be loaded"
            onRetry={() => void productQuery.refetch()}
          />
        ) : null}
        {productQuery.data?.length ? (
          <div className="grid gap-[var(--grid-gap)] md:grid-cols-2 xl:grid-cols-3">
            {productQuery.data.slice(0, 3).map((product) => (
              <LoanProductCard key={product.productCode} product={product} headingLevel={3} />
            ))}
          </div>
        ) : null}
        {productQuery.isSuccess && productQuery.data?.length === 0 ? (
          <EmptyState
            icon={Shapes}
            title="No products available"
            description="No lending products are available right now. Please check again later."
          />
        ) : null}
      </section>
    </div>
  )
}
