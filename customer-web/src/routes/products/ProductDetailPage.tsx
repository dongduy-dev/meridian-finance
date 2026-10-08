import { ArrowLeft, ClipboardList, Shapes } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import type { LoanProduct } from '@/features/loan-products/loan-product-api'
import { useLoanProductQuery } from '@/features/loan-products/loan-product-queries'
import {
  documentTypeLabel,
  evidenceRequirementPresentation,
  eligibilityNoteLabel,
  interestMethodLabel,
  productSlugToCode,
  repaymentMethodLabel,
} from '@/features/loan-products/loan-product-presentation'
import { SalaryAdvanceReadiness } from '@/features/salary-advance/components/SalaryAdvanceReadiness'
import { useSalaryAdvanceReadinessQuery } from '@/features/salary-advance/salary-advance-queries'
import { formatPercentage, formatTerms } from '@/lib/format/presentation'

function PolicyFact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0 space-y-2 border-t border-border py-6 [overflow-wrap:anywhere]">
      <dt className="text-sm leading-5 text-muted-foreground">{label}</dt>
      <dd className="min-w-0 font-semibold text-foreground">{children}</dd>
    </div>
  )
}

function ProductPolicy({ product }: { product: LoanProduct }) {
  return (
    <div className="space-y-[var(--section-editorial)]">
      <section aria-labelledby="product-policy-heading" className="min-w-0 space-y-6">
        <h2 id="product-policy-heading" className="type-editorial-section">Amounts &amp; terms</h2>
        <dl className="grid gap-x-[var(--grid-gap)] sm:grid-cols-2 xl:grid-cols-4">
          <PolicyFact label="Minimum amount"><MoneyDisplay value={product.minAmount} emphasis="primary" /></PolicyFact>
          <PolicyFact label="Maximum amount"><MoneyDisplay value={product.maxAmount} emphasis="primary" /></PolicyFact>
          <PolicyFact label="Allowed terms">{formatTerms(product.policy.allowedTermsMonths)}</PolicyFact>
          <PolicyFact label="Monthly flat interest rate">{formatPercentage(product.policy.pricing.flatMonthlyInterestRate)}</PolicyFact>
          <PolicyFact label="Fee"><MoneyDisplay value={product.policy.pricing.feeAmount} /></PolicyFact>
          <PolicyFact label="Offer validity">{product.policy.offerValidityDays} {product.policy.offerValidityDays === 1 ? 'calendar day' : 'calendar days'}</PolicyFact>
          <PolicyFact label="Interest method">{interestMethodLabel(product.policy.interestCalculationMethod)}</PolicyFact>
          <PolicyFact label="Repayment method">{repaymentMethodLabel(product.policy.repaymentMethod)}</PolicyFact>
        </dl>
      </section>

      <div className="grid gap-[var(--section-transactional)] md:grid-cols-2">
        <section aria-labelledby="product-evidence-heading" className="min-w-0 space-y-6">
          <div className="space-y-2">
            <h2 id="product-evidence-heading" className="type-editorial-section">Required documents</h2>
            <p className="max-w-[70ch] text-base leading-6 text-muted-foreground">Review the documents that may be required with your application.</p>
          </div>
          {product.policy.submissionEvidenceRequirements.length ? (
            <ul className="divide-y divide-border border-y border-border">
              {product.policy.submissionEvidenceRequirements.map((requirement, index) => (
                <li key={`${requirement.documentType}-${index}`} className="flex min-w-0 flex-wrap items-center justify-between gap-3 py-6">
                  <span className="min-w-0 break-words font-medium">{documentTypeLabel(requirement.documentType)}</span>
                  <StatusBadge presentation={evidenceRequirementPresentation(requirement.requirementStatus)} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="border-y border-border py-6 text-sm leading-5 text-muted-foreground">
              No documents are listed for this product.
            </p>
          )}
        </section>

        <section aria-labelledby="product-eligibility-heading" className="min-w-0 space-y-6">
          <div className="space-y-2">
            <h2 id="product-eligibility-heading" className="type-editorial-section">Eligibility</h2>
            <p className="max-w-[70ch] text-base leading-6 text-muted-foreground">These notes explain the general requirements. Your information is checked when you apply.</p>
          </div>
          {product.policy.eligibilityNotes.length ? (
            <ul className="divide-y divide-border border-y border-border">
              {product.policy.eligibilityNotes.map((note, index) => (
                <li key={index} className="flex min-w-0 gap-3 py-6 text-sm leading-5">
                  <ClipboardList aria-hidden="true" className="size-5 shrink-0 text-muted-foreground" />
                  <span className="min-w-0 [overflow-wrap:anywhere]">{eligibilityNoteLabel(note)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="border-y border-border py-6 text-sm leading-5 text-muted-foreground">No additional eligibility information is listed for this product.</p>
          )}
        </section>
      </div>
    </div>
  )
}

function ProductDetailContent({ productCode, applyPath }: { productCode: string; applyPath?: string }) {
  const productQuery = useLoanProductQuery(productCode)

  if (productQuery.isPending) {
    return (
      <div className="space-y-[var(--section-editorial)]">
        <PageHeader headingRole="editorial" className="sm:flex-col sm:items-start [&_h1+p]:max-w-[var(--width-prose)] [&_h1+p]:text-[length:var(--type-intro)] [&_h1+p]:leading-[var(--line-intro)]" eyebrow="Product details" title="Product details" />
        <div role="status" aria-label="Loading product details">
          <div className="grid gap-6 xl:grid-cols-2"><Skeleton className="h-96" /><Skeleton className="h-80" /></div>
        </div>
      </div>
    )
  }

  if (productQuery.isError) {
    return (
      <div className="space-y-[var(--section-editorial)]">
        <PageHeader headingRole="editorial" className="sm:flex-col sm:items-start [&_h1+p]:max-w-[var(--width-prose)] [&_h1+p]:text-[length:var(--type-intro)] [&_h1+p]:leading-[var(--line-intro)]" eyebrow="Product details" title="Product unavailable" />
        <QueryErrorFeedback
          error={productQuery.error}
          title="Product details could not be loaded"
          onRetry={() => void productQuery.refetch()}
        />
      </div>
    )
  }

  return (
    <div className="space-y-[var(--section-editorial)]">
      <PageHeader headingRole="editorial" className="sm:flex-col sm:items-start [&_h1+p]:max-w-[var(--width-prose)] [&_h1+p]:text-[length:var(--type-intro)] [&_h1+p]:leading-[var(--line-intro)]"
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
    <div className="space-y-[var(--section-editorial)]">
      <PageHeader headingRole="editorial" className="sm:flex-col sm:items-start [&_h1+p]:max-w-[var(--width-prose)] [&_h1+p]:text-[length:var(--type-intro)] [&_h1+p]:leading-[var(--line-intro)]"
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

      <section aria-labelledby="salary-advance-readiness-heading" className="min-w-0 space-y-6">
        <div>
          <h2 id="salary-advance-readiness-heading" className="type-editorial-section">Before you apply</h2>
          <p className="mt-2 max-w-[70ch] text-base leading-6 text-muted-foreground">Check your application availability, current limit, and any next steps.</p>
        </div>
        {readinessQuery.isPending ? (
          <div className="grid gap-6 xl:grid-cols-2" role="status" aria-label="Loading application status">
            <Skeleton className="h-80" /><Skeleton className="h-80" />
          </div>
        ) : null}
        {readinessQuery.isError ? (
          <QueryErrorFeedback
            error={readinessQuery.error}
            title="Salary Advance application status could not be loaded"
            onRetry={() => void readinessQuery.refetch()}
          />
        ) : null}
        {readinessQuery.isSuccess && readinessQuery.data ? (
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
      <div className="space-y-[var(--section-editorial)]">
        <PageHeader headingRole="editorial" className="sm:flex-col sm:items-start [&_h1+p]:max-w-[var(--width-prose)] [&_h1+p]:text-[length:var(--type-intro)] [&_h1+p]:leading-[var(--line-intro)]" eyebrow="Product details" title="Product not available" />
        <EmptyState
          icon={Shapes}
          title="Product unavailable"
          description="This product is not available. Return to the loan catalogue to view your options."
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
