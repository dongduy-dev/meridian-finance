import { ArrowLeft, ClipboardList, Shapes } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import type { LoanProduct } from '@/features/loan-products/loan-product-api'
import { useLoanProductQuery } from '@/features/loan-products/loan-product-queries'
import {
  documentTypeLabel,
  evidenceRequirementPresentation,
  interestMethodLabel,
  productSlugToCode,
  repaymentMethodLabel,
} from '@/features/loan-products/loan-product-presentation'
import { SalaryAdvanceReadiness } from '@/features/salary-advance/components/SalaryAdvanceReadiness'
import { useSalaryAdvanceReadinessQuery } from '@/features/salary-advance/salary-advance-queries'
import { formatPercentage, formatTerms } from '@/lib/format/presentation'

function PolicyFact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0 rounded-md border border-border bg-background p-4">
      <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-2 min-w-0 break-words font-semibold text-foreground">{children}</dd>
    </div>
  )
}

function ProductPolicy({ product }: { product: LoanProduct }) {
  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(20rem,0.85fr)]">
      <div className="space-y-6">
        <Card>
          <CardHeader><CardTitle>Product policy</CardTitle></CardHeader>
          <CardContent>
            <dl className="grid gap-4 sm:grid-cols-2">
              <PolicyFact label="Minimum amount"><MoneyDisplay value={product.minAmount} /></PolicyFact>
              <PolicyFact label="Maximum amount"><MoneyDisplay value={product.maxAmount} /></PolicyFact>
              <PolicyFact label="Allowed terms">{formatTerms(product.policy.allowedTermsMonths)}</PolicyFact>
              <PolicyFact label="Monthly flat rate">{formatPercentage(product.policy.pricing.flatMonthlyInterestRate)}</PolicyFact>
              <PolicyFact label="Fee"><MoneyDisplay value={product.policy.pricing.feeAmount} /></PolicyFact>
              <PolicyFact label="Offer validity">{product.policy.offerValidityDays} {product.policy.offerValidityDays === 1 ? 'calendar day' : 'calendar days'}</PolicyFact>
              <PolicyFact label="Interest method">{interestMethodLabel(product.policy.interestCalculationMethod)}</PolicyFact>
              <PolicyFact label="Repayment method">{repaymentMethodLabel(product.policy.repaymentMethod)}</PolicyFact>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Submission evidence</CardTitle>
            <p className="text-sm leading-6 text-muted-foreground">Evidence requirements are returned by the active product policy.</p>
          </CardHeader>
          <CardContent>
            {product.policy.submissionEvidenceRequirements.length ? (
              <ul className="divide-y divide-border">
                {product.policy.submissionEvidenceRequirements.map((requirement, index) => (
                  <li key={`${requirement.documentType}-${index}`} className="flex min-w-0 flex-wrap items-center justify-between gap-3 py-4 first:pt-0 last:pb-0">
                    <span className="min-w-0 break-words font-medium">{documentTypeLabel(requirement.documentType)}</span>
                    <StatusBadge presentation={evidenceRequirementPresentation(requirement.requirementStatus)} />
                  </li>
                ))}
              </ul>
            ) : (
              <p className="rounded-md bg-background p-4 text-sm leading-6 text-muted-foreground">
                No submission evidence is listed for this product.
              </p>
            )}
          </CardContent>
        </Card>
      </div>

      <Card className="h-fit">
        <CardHeader>
          <CardTitle>Eligibility notes</CardTitle>
          <p className="text-sm leading-6 text-muted-foreground">These notes explain returned product prerequisites; they do not determine individual eligibility.</p>
        </CardHeader>
        <CardContent>
          {product.policy.eligibilityNotes.length ? (
            <ul className="space-y-4">
              {product.policy.eligibilityNotes.map((note, index) => (
                <li key={index} className="flex min-w-0 gap-3 text-sm leading-6">
                  <ClipboardList aria-hidden="true" className="mt-1 size-4 shrink-0 text-accent" />
                  <span className="min-w-0 break-words">{note}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm leading-6 text-muted-foreground">No eligibility notes are listed for this product.</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function ProductDetailContent({ productCode, applyPath }: { productCode: string; applyPath?: string }) {
  const productQuery = useLoanProductQuery(productCode)

  if (productQuery.isPending) {
    return (
      <div className="space-y-8">
        <PageHeader eyebrow="Product details" title="Product details" />
        <div role="status" aria-label="Loading product details">
          <div className="grid gap-6 xl:grid-cols-2"><Skeleton className="h-96" /><Skeleton className="h-80" /></div>
        </div>
      </div>
    )
  }

  if (productQuery.isError) {
    return (
      <div className="space-y-8">
        <PageHeader eyebrow="Product details" title="Product unavailable" />
        <QueryErrorFeedback
          error={productQuery.error}
          title="Product details could not be loaded"
          onRetry={() => void productQuery.refetch()}
        />
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Product details"
        title={productQuery.data.name}
        description={productQuery.data.description ?? undefined}
        actions={<div className="flex flex-wrap gap-3"><Button variant="secondary" asChild><Link to="/products"><ArrowLeft aria-hidden="true" />Back to products</Link></Button>{applyPath && productQuery.data.active ? <Button asChild><Link to={applyPath}>Apply now</Link></Button> : null}</div>}
      />
      <ProductPolicy product={productQuery.data} />
    </div>
  )
}

function SalaryAdvanceProductContent() {
  const productQuery = useLoanProductQuery('SALARY_ADVANCE')
  const readinessQuery = useSalaryAdvanceReadinessQuery()

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Product details"
        title={productQuery.data?.name ?? 'Salary Advance'}
        description={productQuery.data?.description ?? undefined}
        actions={<Button variant="secondary" asChild><Link to="/products"><ArrowLeft aria-hidden="true" />Back to products</Link></Button>}
      />
      {productQuery.isPending ? (
        <div role="status" aria-label="Loading Salary Advance product details">
          <div className="grid gap-6 xl:grid-cols-2"><Skeleton className="h-96" /><Skeleton className="h-80" /></div>
        </div>
      ) : null}
      {productQuery.isError ? (
        <QueryErrorFeedback
          error={productQuery.error}
          title="Salary Advance product details could not be loaded"
          onRetry={() => void productQuery.refetch()}
        />
      ) : null}
      {productQuery.data ? (
        <ProductPolicy product={productQuery.data} />
      ) : null}

      <section aria-labelledby="salary-advance-readiness-heading" className="space-y-5">
        <div>
          <h2 id="salary-advance-readiness-heading" className="text-2xl font-semibold tracking-tight">Your Salary Advance readiness</h2>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">Review the current backend readiness result, returned limit facts, and any Customer action Meridian requires.</p>
        </div>
        {readinessQuery.isPending ? (
          <div className="grid gap-6 xl:grid-cols-2" role="status" aria-label="Loading Salary Advance readiness">
            <Skeleton className="h-80" /><Skeleton className="h-80" />
          </div>
        ) : null}
        {readinessQuery.isError ? (
          <QueryErrorFeedback
            error={readinessQuery.error}
            title="Salary Advance readiness could not be loaded"
            onRetry={() => void readinessQuery.refetch()}
          />
        ) : null}
        {readinessQuery.data ? (
          <SalaryAdvanceReadiness readiness={readinessQuery.data} showApplyAction />
        ) : null}
      </section>
    </div>
  )
}

export function ProductDetailPage() {
  const { productSlug } = useParams()
  const productCode = productSlug && productSlug in productSlugToCode
    ? productSlugToCode[productSlug as keyof typeof productSlugToCode]
    : undefined

  if (!productCode) {
    return (
      <div className="space-y-8">
        <PageHeader eyebrow="Product details" title="Product not available" />
        <EmptyState
          icon={Shapes}
          title="Unknown product route"
          description="This product route is not part of the current Meridian catalogue experience."
          action={<Button variant="secondary" asChild><Link to="/products">Return to products</Link></Button>}
        />
      </div>
    )
  }

  if (productCode === 'SALARY_ADVANCE') {
    return <SalaryAdvanceProductContent />
  }

  return <ProductDetailContent productCode={productCode} applyPath={`/products/${productSlug}/apply`} />
}
