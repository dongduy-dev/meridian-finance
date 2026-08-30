import { ArrowRight, CalendarRange, Percent } from 'lucide-react'
import { Link } from 'react-router-dom'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import type { LoanProduct } from '@/features/loan-products/loan-product-api'
import { productSlug } from '@/features/loan-products/loan-product-presentation'
import { formatPercentage, formatTerms } from '@/lib/format/presentation'

export function LoanProductCard({ product }: { product: LoanProduct }) {
  const slug = productSlug(product.productCode)

  return (
    <Card className="flex min-w-0 flex-col overflow-hidden">
      <CardHeader className="border-b border-border/80 bg-card">
        <div className="mb-2 h-1 w-12 rounded-full bg-accent" aria-hidden="true" />
        <CardTitle className="break-words">{product.name}</CardTitle>
        {product.description ? (
          <p className="text-sm leading-6 text-muted-foreground">{product.description}</p>
        ) : null}
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-5 pt-6">
        <div>
          <p className="text-xs font-semibold tracking-[0.12em] text-muted-foreground uppercase">
            Amount range
          </p>
          <p className="mt-1 flex min-w-0 flex-wrap items-baseline gap-x-2 text-sm">
            <MoneyDisplay value={product.minAmount} />
            <span className="text-muted-foreground">to</span>
            <MoneyDisplay value={product.maxAmount} />
          </p>
        </div>
        <dl className="grid gap-3 text-sm">
          <div className="flex min-w-0 gap-3">
            <CalendarRange aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0">
              <dt className="font-medium">Available terms</dt>
              <dd className="mt-0.5 break-words text-muted-foreground">
                {formatTerms(product.policy.allowedTermsMonths)}
              </dd>
            </div>
          </div>
          <div className="flex min-w-0 gap-3">
            <Percent aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0">
              <dt className="font-medium">Monthly flat rate</dt>
              <dd className="mt-0.5 break-words text-muted-foreground">
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
          <p className="text-sm text-muted-foreground">Product details are not available in this version.</p>
        )}
      </CardFooter>
    </Card>
  )
}
