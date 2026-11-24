import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { ChevronLeft, ChevronRight, ExternalLink, FilterX, Search } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import { staffApplicationIndexQuery } from '../api/queries'
import { QueryErrorPanel } from '../components/QueryErrorPanel'
import { StatusBadge } from '../components/StatusBadge'
import {
  APPLICATION_NUMBER_MAX_LENGTH, discoveryParameterNames, isValidApplicationNumber,
  isValidSubmissionDate, normalizeApplicationNumber, parseApplicationPage, submissionDateMin, submissionDateMax,
} from '../model/discovery-filters'
import {
  applicationStatusLabel,
  applicationStatusOptions,
  isSupportedApplicationStatus,
  isSupportedProduct,
  productLabel,
  productOptions,
  originationChannelOptions,
  originationChannelLabel,
} from '../model/presentation'

const PAGE_SIZE = 20

export function ApplicationSearchPage() {
  const { manager, state } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const productCode = searchParams.get('productCode')
  const status = searchParams.get('status')
  const applicationNumber = searchParams.get('applicationNumber') ?? ''
  const originationChannel = searchParams.get('originationChannel') ?? ''
  const submittedFrom = searchParams.get('submittedFrom') ?? ''
  const submittedTo = searchParams.get('submittedTo') ?? ''
  const page = parseApplicationPage(searchParams.get('page'))
  const [draft, setDraft] = useState({ source: applicationNumber, value: applicationNumber })
  const [searchError, setSearchError] = useState(false)
  if (draft.source !== applicationNumber) {
    setDraft({ source: applicationNumber, value: applicationNumber })
    setSearchError(false)
  }
  const validDateRange = isValidSubmissionDate(submittedFrom) && isValidSubmissionDate(submittedTo)
    && (!submittedFrom || !submittedTo || submittedFrom <= submittedTo)
  const invalidFilters = !isSupportedProduct(productCode)
    || !isSupportedApplicationStatus(status)
    || !isValidApplicationNumber(applicationNumber)
    || (originationChannel !== '' && !originationChannelOptions.some(([value]) => value === originationChannel))
    || !validDateRange
    || discoveryParameterNames.some((name) => searchParams.getAll(name).length > 1)
    || page === undefined
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const filters = {
    productCode: invalidFilters ? undefined : productCode ?? undefined,
    status: invalidFilters ? undefined : status ?? undefined,
    applicationNumber: invalidFilters ? undefined : normalizeApplicationNumber(applicationNumber) || undefined,
    originationChannel: invalidFilters ? undefined : originationChannel || undefined,
    submittedFrom: invalidFilters ? undefined : submittedFrom || undefined,
    submittedTo: invalidFilters ? undefined : submittedTo || undefined,
    page: page ?? 0,
    size: PAGE_SIZE,
  }
  const query = useQuery(staffApplicationIndexQuery(manager, filters, canRead && !invalidFilters))

  const updateFilter = (name: typeof discoveryParameterNames[number], value: string) => {
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
  const reset = () => {
    setDraft({ source: '', value: '' })
    setSearchError(false)
    setSearchParams(new URLSearchParams())
  }

  const data = canRead && !invalidFilters ? query.data : undefined
  const filtered = Boolean(productCode || status || applicationNumber || originationChannel || submittedFrom || submittedTo)
  const outOfRange = data && data.page > 0 && data.page >= data.totalPages

  return (
    <section className="mx-auto max-w-[90rem] space-y-6">
      <div>
        <p className="text-sm font-semibold text-muted-foreground">LENDING OPERATIONS</p>
        <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">
          Applications
        </h1>
        <p className="mt-2 max-w-3xl text-muted-foreground">
          Search by exact application number or filter by product, status, channel, and submission date.
        </p>
      </div>

      <div className="rounded-lg border bg-card p-4 shadow-soft sm:p-5" aria-label="Application filters">
        <form className="mb-4 space-y-2" onSubmit={(event) => {
          event.preventDefault()
          if (!isValidApplicationNumber(draft.value)) { setSearchError(true); return }
          setSearchError(false)
          const normalized = normalizeApplicationNumber(draft.value)
          setDraft({ source: normalized, value: normalized })
          updateFilter('applicationNumber', normalized)
        }}>
          <label htmlFor="application-number-search" className="text-sm font-semibold">Search application number</label>
          <div className="flex flex-col gap-2 sm:max-w-xl sm:flex-row">
            <Input id="application-number-search" value={draft.value} maxLength={APPLICATION_NUMBER_MAX_LENGTH}
              autoComplete="off" spellCheck={false} placeholder="e.g. UCL-20260902-000001"
              aria-invalid={searchError} aria-describedby="application-number-help"
              onChange={(event) => { setDraft({ source: applicationNumber, value: event.target.value }); setSearchError(false) }} />
            <Button type="submit"><Search aria-hidden="true" /> Search</Button>
          </div>
          <p id="application-number-help" role={searchError ? 'alert' : undefined} className="text-sm text-muted-foreground">
            {searchError ? 'Enter a complete SA, UCL, or CL application number, such as UCL-20260902-000001.'
              : draft.value !== applicationNumber ? 'Select Search or press Enter to apply this number. Results still use the applied filters.'
                : 'Use the full business reference. Leave blank to search all application numbers.'}
          </p>
        </form>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
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
          <label className="grid gap-2 text-sm font-semibold">
            Origination channel
            <select className="min-h-11 rounded-md border border-input bg-background px-3 font-normal"
              value={originationChannel} onChange={(event) => updateFilter('originationChannel', event.target.value)}>
              <option value="">All channels</option>
              {originationChannelOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label className="grid gap-2 text-sm font-semibold">
            Submitted from
            <Input type="date" min={submissionDateMin} max={submissionDateMax} value={submittedFrom}
              aria-invalid={!validDateRange} aria-describedby="submission-date-help"
              onChange={(event) => updateFilter('submittedFrom', event.target.value)} />
          </label>
          <label className="grid gap-2 text-sm font-semibold">
            Submitted to
            <Input type="date" min={submissionDateMin} max={submissionDateMax} value={submittedTo}
              aria-invalid={!validDateRange} aria-describedby="submission-date-help"
              onChange={(event) => updateFilter('submittedTo', event.target.value)} />
          </label>
        </div>
        <p id="submission-date-help" className="mt-2 text-sm text-muted-foreground">Both dates include the whole day in Vietnam time (Asia/Ho_Chi_Minh).</p>
        <Button className="mt-4" variant="outline" onClick={reset} disabled={!invalidFilters && !filtered && page === 0 && !draft.value}>
          <FilterX aria-hidden="true" /> Reset filters
        </Button>
      </div>

      {invalidFilters ? (
        <Alert variant="warning">
          <Search aria-hidden="true" />
          <AlertTitle>Invalid application filters</AlertTitle>
          <AlertDescription>
            Use a complete application number, supported product, status and channel, valid dates with Submitted from on or before Submitted to, and a valid page. Correct the filters or reset them to search again.
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
            <Alert variant="warning"><Search aria-hidden="true" /><AlertTitle>Refresh incomplete</AlertTitle><AlertDescription>Previous results are shown. Refresh to check for updates.</AlertDescription></Alert>
          ) : null}
          {query.isFetching ? <p role="status" className="text-sm text-muted-foreground">Refreshing applications…</p> : null}
          {outOfRange ? (
            <Alert variant="warning"><AlertTitle>Application page unavailable</AlertTitle><AlertDescription>
              This page is beyond the available results. Return to the first page to review the current matches.
              <Button className="mt-3" variant="outline" onClick={() => updatePage(0)}>First application page</Button>
            </AlertDescription></Alert>
          ) : !query.isError && data.items.length === 0 ? (
            <div className="rounded-lg border bg-card p-8 text-center shadow-soft">
              <Search aria-hidden="true" className="mx-auto size-8 text-muted-foreground" />
              <h2 className="mt-3 text-lg font-semibold">{filtered ? 'No applications match these filters' : 'No applications are available'}</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {filtered ? 'Check the exact application number and selected filters, or reset filters to see more applications.' : 'No applications were found.'}
              </p>
              {filtered ? <Button className="mt-4" variant="outline" onClick={reset}>Reset filters</Button> : null}
            </div>
          ) : (
            <div className="overflow-x-auto rounded-lg border bg-card shadow-soft" tabIndex={0} aria-label="Staff application results">
              <table className="w-full min-w-[72rem] border-collapse text-left text-sm">
                <caption className="sr-only">Staff loan application search results</caption>
                <thead className="bg-muted/70 text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-4 py-3">Application</th>
                    <th scope="col" className="px-4 py-3">Product</th>
                    <th scope="col" className="px-4 py-3">Channel</th>
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
                      <td className="px-4 py-4 whitespace-nowrap text-muted-foreground">{originationChannelLabel(application.originationChannel)}</td>
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
              {data.totalElements} {data.totalElements === 1 ? 'application' : 'applications'}{outOfRange ? '' : ` · Page ${data.totalPages === 0 ? 0 : data.page + 1} of ${data.totalPages}`}
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
