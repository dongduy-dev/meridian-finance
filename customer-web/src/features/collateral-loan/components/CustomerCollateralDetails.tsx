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
    <Card className="min-w-0">
      <CardHeader>
        <CardTitle>Collateral details</CardTitle>
        <CardDescription>The immutable collateral facts submitted with this application. Estimated value is the submitted estimate.</CardDescription>
      </CardHeader>
      <CardContent>
        {query.isPending ? <Skeleton className="h-48" role="status" aria-label="Loading collateral details" /> : null}
        {query.isError ? <QueryErrorFeedback error={query.error} title="Collateral details could not be loaded" onRetry={() => void query.refetch()} /> : null}
        {query.data ? (
          <dl className="grid min-w-0 gap-5 sm:grid-cols-2">
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
  return <div className="min-w-0"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="mt-1 whitespace-pre-wrap [overflow-wrap:anywhere]">{children}</dd></div>
}
