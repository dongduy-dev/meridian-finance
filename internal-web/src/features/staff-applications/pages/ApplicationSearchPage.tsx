import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, ExternalLink, FilterX, Search } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { staffApplicationIndexQuery } from '../api/queries'
import { QueryErrorPanel } from '../components/QueryErrorPanel'
import { StatusBadge } from '../components/StatusBadge'
import {
  applicationStatusLabel,
  applicationStatusOptions,
  isSupportedApplicationStatus,
  isSupportedProduct,
  productLabel,
  productOptions,
} from '../model/presentation'

const PAGE_SIZE = 20

function parsePage(value: string | null): number | undefined {
  if (value === null) return 0
  if (!/^\d+$/.test(value)) return undefined
  const page = Number(value)
  return Number.isSafeInteger(page) ? page : undefined
}

export function ApplicationSearchPage() {
  const { manager, state } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const productCode = searchParams.get('productCode')
  const status = searchParams.get('status')
  const page = parsePage(searchParams.get('page'))
  const invalidFilters = !isSupportedProduct(productCode)
    || !isSupportedApplicationStatus(status)
    || page === undefined
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const filters = {
    productCode: productCode ?? undefined,
    status: status ?? undefined,
    page: page ?? 0,
    size: PAGE_SIZE,
  }
  const query = useQuery(staffApplicationIndexQuery(manager, filters, canRead && !invalidFilters))

  const updateFilter = (name: 'productCode' | 'status', value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(name, value)
    else next.delete(name)
    next.set('page', '0')
    setSearchParams(next)
  }
  const updatePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }
  const reset = () => setSearchParams(new URLSearchParams())

  const data = query.data
  const filtered = Boolean(productCode || status)

  return (
    <section className="mx-auto max-w-[90rem] space-y-6">
      <div>
        <p className="text-sm font-semibold text-muted-foreground">LENDING OPERATIONS</p>
        <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">
          Applications
        </h1>
        <p className="mt-2 max-w-3xl text-muted-foreground">
          Find lending applications by durable product and lifecycle status. Results contain no Customer profile or banking data.
        </p>
      </div>

      <div className="rounded-lg border bg-card p-4 shadow-soft sm:p-5" aria-label="Application filters">
        <div className="grid gap-4 sm:grid-cols-2 lg:max-w-3xl">
          <label className="grid gap-2 text-sm font-semibold">
            Product
            <select
              className="min-h-11 rounded-md border border-input bg-background px-3 font-normal"
              value={productCode ?? ''}
              onChange={(event) => updateFilter('productCode', event.target.value)}
            >
              <option value="">All products</option>
              {productOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label className="grid gap-2 text-sm font-semibold">
            Application status
            <select
              className="min-h-11 rounded-md border border-input bg-background px-3 font-normal"
              value={status ?? ''}
              onChange={(event) => updateFilter('status', event.target.value)}
            >
              <option value="">All statuses</option>
              {applicationStatusOptions.map((value) => (
                <option key={value} value={value}>{applicationStatusLabel(value)}</option>
              ))}
            </select>
          </label>
        </div>
        <Button className="mt-4" variant="outline" onClick={reset} disabled={!filtered && page === 0}>
          <FilterX aria-hidden="true" /> Reset filters
        </Button>
      </div>

      {invalidFilters ? (
        <Alert variant="warning">
          <Search aria-hidden="true" />
          <AlertTitle>Invalid application filters</AlertTitle>
          <AlertDescription>
            The URL contains a product, status, or page value this search does not support.
            <Button className="mt-3" variant="outline" onClick={reset}>Reset filters</Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {!invalidFilters && query.isPending ? (
        <div role="status" aria-live="polite" className="flex min-h-48 items-center justify-center gap-3 rounded-lg border bg-card text-sm text-muted-foreground">
          <Spinner className="size-5" /> Loading applications…
        </div>
      ) : null}

      {!invalidFilters && query.isError && !data ? (
        <QueryErrorPanel error={query.error} resource="index" onRetry={() => void query.refetch()} />
      ) : null}

      {data ? (
        <div className="space-y-4">
          {query.isError ? (
            <Alert variant="warning"><Search aria-hidden="true" /><AlertTitle>Refresh incomplete</AlertTitle><AlertDescription>The previous verified results remain visible.</AlertDescription></Alert>
          ) : null}
          {data.items.length === 0 ? (
            <div className="rounded-lg border bg-card p-8 text-center shadow-soft">
              <Search aria-hidden="true" className="mx-auto size-8 text-muted-foreground" />
              <h2 className="mt-3 text-lg font-semibold">{filtered ? 'No applications match these filters' : 'No applications are available'}</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {filtered ? 'Reset the supported filters to widen this operational search.' : 'There are no Staff-readable lending applications in this result set.'}
              </p>
              {filtered ? <Button className="mt-4" variant="outline" onClick={reset}>Reset filters</Button> : null}
            </div>
          ) : (
            <div className="overflow-x-auto rounded-lg border bg-card shadow-soft" tabIndex={0} aria-label="Staff application results">
              <table className="w-full min-w-[62rem] border-collapse text-left text-sm">
                <caption className="sr-only">Staff loan application search results</caption>
                <thead className="bg-muted/70 text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-4 py-3">Application</th>
                    <th scope="col" className="px-4 py-3">Product</th>
                    <th scope="col" className="px-4 py-3 text-right">Requested amount</th>
                    <th scope="col" className="px-4 py-3">Term</th>
                    <th scope="col" className="px-4 py-3">Status</th>
                    <th scope="col" className="px-4 py-3">Submitted</th>
                    <th scope="col" className="px-4 py-3"><span className="sr-only">Action</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {data.items.map((application) => (
                    <tr key={application.loanApplicationId} className="align-top hover:bg-muted/35">
                      <td className="px-4 py-4 font-semibold">{application.applicationNumber}</td>
                      <td className="px-4 py-4">{productLabel(application.productCode)}</td>
                      <td className="financial-value px-4 py-4 text-right font-medium">{formatVnd(application.requestedAmount)}</td>
                      <td className="px-4 py-4">{application.requestedTermMonths} months</td>
                      <td className="px-4 py-4"><StatusBadge status={application.status} /></td>
                      <td className="px-4 py-4 whitespace-nowrap">{formatTimestamp(application.submittedAt)}</td>
                      <td className="px-4 py-4 text-right">
                        <Button asChild variant="link">
                          <Link to={`/staff/applications/${application.loanApplicationId}`} aria-label={`Open case ${application.applicationNumber}`}>
                            Open case <ExternalLink aria-hidden="true" />
                          </Link>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex flex-col gap-3 rounded-lg border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-muted-foreground">
              {data.totalElements} {data.totalElements === 1 ? 'application' : 'applications'} · Page {data.totalPages === 0 ? 0 : data.page + 1} of {data.totalPages}
            </p>
            <nav aria-label="Application result pages" className="flex gap-2">
              <Button variant="outline" onClick={() => updatePage(data.page - 1)} disabled={data.page <= 0} aria-label="Previous application page">
                <ChevronLeft aria-hidden="true" /> Previous
              </Button>
              <Button variant="outline" onClick={() => updatePage(data.page + 1)} disabled={data.page + 1 >= data.totalPages} aria-label="Next application page">
                Next <ChevronRight aria-hidden="true" />
              </Button>
            </nav>
          </div>
        </div>
      ) : null}
    </section>
  )
}
