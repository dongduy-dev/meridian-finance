import { ArrowRight, CalendarRange, Percent } from 'lucide-react'
import { Link } from 'react-router-dom'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card'
import type { LoanProduct } from '@/features/loan-products/loan-product-api'
import { productSlug } from '@/features/loan-products/loan-product-presentation'
import { formatPercentage, formatTerms } from '@/lib/format/presentation'

export function LoanProductCard({ product, headingLevel = 2 }: {
  product: LoanProduct
  headingLevel?: 2 | 3
}) {
  const slug = productSlug(product.productCode)
  const Heading = headingLevel === 3 ? 'h3' : 'h2'

  return (
    <Card className="flex min-w-0 flex-col [overflow-wrap:anywhere]">
      <CardHeader>
        <Heading className="type-section">{product.name}</Heading>
        {product.description ? (
          <p className="text-sm leading-6 text-muted-foreground">{product.description}</p>
        ) : null}
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-6">
        <div>
          <p className="text-sm leading-5 text-muted-foreground">
            Amount range
          </p>
          <p className="mt-2 flex min-w-0 flex-wrap items-baseline gap-x-2">
            <MoneyDisplay value={product.minAmount} />
            <span className="text-muted-foreground">to</span>
            <MoneyDisplay value={product.maxAmount} />
          </p>
        </div>
        <dl className="grid gap-4">
          <div className="flex min-w-0 gap-3">
            <CalendarRange aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0">
              <dt className="text-sm leading-5 text-muted-foreground">Available terms</dt>
              <dd className="mt-2 break-words">
                {formatTerms(product.policy.allowedTermsMonths)}
              </dd>
            </div>
          </div>
          <div className="flex min-w-0 gap-3">
            <Percent aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0">
              <dt className="text-sm leading-5 text-muted-foreground">Monthly flat interest rate</dt>
              <dd className="mt-2 break-words">
                {formatPercentage(product.policy.pricing.flatMonthlyInterestRate)}
              </dd>
            </div>
          </div>
        </dl>
      </CardContent>
      <CardFooter>
        {slug ? (
          <Button variant="secondary" className="w-full" asChild>
            <Link to={`/products/${slug}`}>
              View product details
              <ArrowRight aria-hidden="true" />
            </Link>
          </Button>
        ) : (
          <p className="text-sm text-muted-foreground">Loan details are not available right now.</p>
        )}
      </CardFooter>
    </Card>
  )
}
