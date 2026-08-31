import { ArrowRight, FileText } from 'lucide-react'
import { Link } from 'react-router-dom'

import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { CustomerApplicationSummary } from '@/features/applications/application-api'
import { requiredActionPresentation } from '@/features/applications/application-presentation'
import { productNameForCode } from '@/features/loan-products/loan-product-presentation'

export function RequiredActionCard({ application }: { application: CustomerApplicationSummary }) {
  const presentation = requiredActionPresentation(application.requiredAction)
  if (!presentation) return null

  return (
    <Card className="min-w-0 border-warning/25">
      <CardHeader className="gap-3">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-semibold tracking-[0.12em] text-muted-foreground uppercase">
              {productNameForCode(application.productCode)}
            </p>
            <CardTitle className="mt-1 break-words text-lg">{presentation.title}</CardTitle>
          </div>
          <StatusBadge presentation={presentation.status} />
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm leading-6 text-muted-foreground">{presentation.description}</p>
        <p className="flex min-w-0 items-center gap-2 text-sm font-medium">
          <FileText aria-hidden="true" className="size-4 shrink-0" />
          <span className="break-all">Application {application.applicationNumber}</span>
        </p>
        {application.requiredAction === 'UPLOAD_DOCUMENTS' ? (
          <Button asChild>
            <Link to={`/applications/${application.loanApplicationId}/documents`}>Upload documents<ArrowRight aria-hidden="true" /></Link>
          </Button>
        ) : null}
      </CardContent>
    </Card>
  )
}
