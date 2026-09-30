import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatVnd } from '@/lib/format/presentation'
import { humanizeKnownValue } from '../model/presentation'
import type { StaffLoanApplicationCase } from '../api/contracts'

type CollateralContext = NonNullable<StaffLoanApplicationCase['collateralContext']>

export function CollateralFactsCard({ collateral }: { collateral: CollateralContext }) {
  return <Card>
    <CardHeader><CardTitle>Submitted collateral facts</CardTitle><p className="text-sm text-muted-foreground">Compare these submitted asset details with the ownership document. Review the document version separately.</p></CardHeader>
    <CardContent><dl className="grid gap-4 sm:grid-cols-2">
      <div><dt className="text-sm text-muted-foreground">Collateral type</dt><dd className="mt-1 font-semibold">{humanizeKnownValue(collateral.collateralType)}</dd></div>
      <div><dt className="text-sm text-muted-foreground">Estimated value</dt><dd className="financial-value mt-1 font-semibold">{formatVnd(collateral.estimatedValue)}</dd></div>
      <div><dt className="text-sm text-muted-foreground">Description</dt><dd className="mt-1 break-words font-semibold">{collateral.description}</dd></div>
      <div><dt className="text-sm text-muted-foreground">Ownership status</dt><dd className="mt-1 break-words font-semibold">{collateral.ownershipStatus}</dd></div>
      <div><dt className="text-sm text-muted-foreground">Condition note</dt><dd className="mt-1 break-words font-semibold">{collateral.conditionNote}</dd></div>
    </dl></CardContent>
  </Card>
}
