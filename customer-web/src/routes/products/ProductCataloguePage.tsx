import { Shapes } from 'lucide-react'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { Skeleton } from '@/components/ui/skeleton'
import { AccountReadinessPrompt, useOwnCustomerQuery } from '@/features/account'
import { LoanProductCard } from '@/features/loan-products/components/LoanProductCard'
import { useLoanProductsQuery } from '@/features/loan-products/loan-product-queries'

export function ProductCataloguePage() {
  const customerQuery = useOwnCustomerQuery()
  const productQuery = useLoanProductsQuery()

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Borrowing options"
        title="Products"
        description="Compare active Meridian products using the current amount, term, pricing, evidence, and eligibility policy returned by the platform."
      />

      {customerQuery.isPending ? (
        <Skeleton className="h-32 w-full" role="status" aria-label="Loading account readiness" />
      ) : null}
      {customerQuery.isError ? (
        <QueryErrorFeedback
          error={customerQuery.error}
          title="Account readiness could not be loaded"
          onRetry={() => void customerQuery.refetch()}
        />
      ) : null}
      {customerQuery.data ? <AccountReadinessPrompt customer={customerQuery.data} /> : null}

      {productQuery.isPending ? (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3" role="status" aria-label="Loading products">
          {Array.from({ length: 3 }, (_, index) => <Skeleton key={index} className="h-96 w-full" />)}
        </div>
      ) : null}
      {productQuery.isError ? (
        <QueryErrorFeedback
          error={productQuery.error}
          title="Product catalogue could not be loaded"
          onRetry={() => void productQuery.refetch()}
        />
      ) : null}
      {productQuery.data?.length ? (
        <section aria-label="Available products" className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {productQuery.data.map((product) => (
            <LoanProductCard key={product.productCode} product={product} />
          ))}
        </section>
      ) : null}
      {productQuery.data?.length === 0 ? (
        <EmptyState
          icon={Shapes}
          title="No products available"
          description="Meridian is not currently returning any active lending products. Please check again later."
        />
      ) : null}
    </div>
  )
}
