import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { adminProductsQuery } from '../api/queries'
import { LoanProductCard } from '../components/LoanProductCard'
import { ProductQueryErrorPanel } from '../components/ProductQueryErrorPanel'

export function LoanProductAdministrationPage() {
  const { manager, state } = useAuth()
  const enabled = state.status === 'authenticated' && hasPermission(state.actor, 'loan:product:manage')
  const products = useQuery(adminProductsQuery(manager, enabled))

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <p className="text-sm font-semibold text-muted-foreground">LOAN PRODUCT ADMINISTRATION</p>
        <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Loan Products</h1>
        <p className="mt-2 max-w-3xl text-muted-foreground">Manage future product availability and amount ranges. Product identity, policy, and historical lending evidence remain read-only.</p>
      </div>
      <Button variant="outline" disabled={products.isFetching} onClick={() => void products.refetch()}>
        {products.isFetching && !products.isPending ? 'Refreshing…' : 'Refresh products'}
      </Button>
    </div>
    {products.isPending ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading Loan Products…</div> : null}
    {products.isError && !products.data ? <ProductQueryErrorPanel error={products.error} onRetry={() => void products.refetch()} /> : null}
    {products.data?.length === 0 ? <p className="rounded-md border p-5 text-sm text-muted-foreground">No Loan Products are configured. Product creation is not available in this workspace.</p> : null}
    {products.data?.length ? <div className="grid gap-5">{products.data.map((product) =>
      <LoanProductCard key={product.productCode} product={product} manager={manager} />
    )}</div> : null}
  </section>
}
