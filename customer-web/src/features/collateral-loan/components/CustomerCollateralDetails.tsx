import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

import { useOwnCollateralQuery } from '../collateral-loan-queries'

const collateralTypeLabels: Record<string, string> = {
  MOTORBIKE: 'Motorbike',
  CAR: 'Car',
  ELECTRONICS: 'Electronics',
  PROPERTY_DOCUMENT: 'Property document',
  OTHER: 'Other',
}

export function CustomerCollateralDetails({ loanApplicationId }: { loanApplicationId: string }) {
  const query = useOwnCollateralQuery(loanApplicationId)
  return (
    <Card className="min-w-0 border-0 border-t bg-transparent">
      <CardHeader className="px-0 pt-6 sm:px-0 sm:pt-6">
        <CardTitle>Collateral details</CardTitle>
        <CardDescription>These are the collateral details submitted with your application. The estimated value is the amount you provided, not a confirmed valuation.</CardDescription>
      </CardHeader>
      <CardContent className="px-0 pb-0 sm:px-0 sm:pb-0">
        {query.isPending ? <Skeleton className="h-48" role="status" aria-label="Loading collateral details" /> : null}
        {query.isError ? <QueryErrorFeedback error={query.error} title="Collateral details could not be loaded" onRetry={() => void query.refetch()} /> : null}
        {query.data ? (
          <dl className="min-w-0 divide-y divide-border border-y border-border">
            <CollateralFact label="Collateral type">{Object.hasOwn(collateralTypeLabels, query.data.collateralType) ? collateralTypeLabels[query.data.collateralType] : 'Type unavailable'}</CollateralFact>
            <CollateralFact label="Description">{query.data.description}</CollateralFact>
            <CollateralFact label="Estimated value"><MoneyDisplay value={query.data.estimatedValue} /></CollateralFact>
            <CollateralFact label="Ownership status">{query.data.ownershipStatus}</CollateralFact>
            <CollateralFact label="Condition note">{query.data.conditionNote}</CollateralFact>
          </dl>
        ) : null}
      </CardContent>
    </Card>
  )
}

function CollateralFact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="grid min-w-0 gap-2 py-4 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-6"><dt className="text-sm leading-5 text-muted-foreground">{label}</dt><dd className="min-w-0 whitespace-pre-wrap text-base leading-6 [overflow-wrap:anywhere]">{children}</dd></div>
}
