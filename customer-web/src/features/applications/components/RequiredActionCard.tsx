import { ArrowRight, FileText } from 'lucide-react'
import { Link } from 'react-router-dom'

import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import type { CustomerApplicationSummary } from '@/features/applications/application-api'
import { requiredActionPresentation } from '@/features/applications/application-presentation'
import { productNameForCode } from '@/features/loan-products/loan-product-presentation'
import { cn } from '@/lib/cn'

export function RequiredActionCard({ application, presentation: layout = 'card' }: {
  application: CustomerApplicationSummary
  presentation?: 'card' | 'row'
}) {
  const presentation = requiredActionPresentation(application.requiredAction)
  if (!presentation) return null
  const Heading = layout === 'row' ? 'h3' : 'h2'

  return (
    <Card className={cn('min-w-0 [overflow-wrap:anywhere]', layout === 'row' ? 'border-0 bg-transparent py-6' : 'border-0 border-l-2 border-warning')}>
      <CardHeader className={cn('gap-3', layout === 'row' && 'p-0 sm:p-0')}>
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm leading-5 text-muted-foreground">
              {productNameForCode(application.productCode)}
            </p>
            <Heading className="type-section mt-2">{presentation.title}</Heading>
          </div>
          <StatusBadge presentation={presentation.status} />
        </div>
      </CardHeader>
      <CardContent className={cn('space-y-4', layout === 'row' && 'px-0 pb-0 pt-4 sm:px-0 sm:pb-0')}>
        <p className="text-sm leading-6 text-muted-foreground">{presentation.description}</p>
        <p className="flex min-w-0 items-center gap-2 text-sm font-medium">
          <FileText aria-hidden="true" className="size-4 shrink-0" />
          <span className="min-w-0 [overflow-wrap:anywhere]">Application {application.applicationNumber}</span>
        </p>
        {application.requiredAction === 'UPLOAD_DOCUMENTS' ? (
          <Button asChild>
            <Link to={`/applications/${application.loanApplicationId}/documents`}>Upload documents<ArrowRight aria-hidden="true" /></Link>
          </Button>
        ) : null}
        {application.requiredAction === 'COMPLETE_CORRECTIONS' ? (
          <Button asChild>
            <Link to={`/applications/${application.loanApplicationId}/corrections`}>Review requested changes<ArrowRight aria-hidden="true" /></Link>
          </Button>
        ) : null}
        {application.requiredAction === 'REVIEW_APPROVED_OFFER' ? (
          <Button asChild>
            <Link to={`/applications/${application.loanApplicationId}/offer`}>Review offer<ArrowRight aria-hidden="true" /></Link>
          </Button>
        ) : null}
        {application.requiredAction === 'ACKNOWLEDGE_CONTRACT' ? (
          <Button asChild>
            <Link to={`/applications/${application.loanApplicationId}/contract`}>Review contract<ArrowRight aria-hidden="true" /></Link>
          </Button>
        ) : null}
      </CardContent>
    </Card>
  )
}
