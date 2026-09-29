import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ZodError } from 'zod'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import type { StaffCustomer } from '@/features/staff-origination/api/contracts'
import { ApiError, NetworkError } from '@/lib/api'
import { enableDigitalAccess, getDigitalAccess, searchCustomer } from '../api/customer-access-api'

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.errorCode === 'EMAIL_ALREADY_REGISTERED') return 'An account with this email already exists.'
    if (error.errorCode === 'CUSTOMER_DIGITAL_ACCESS_OWNERSHIP_NOT_VERIFIED') return 'The identity reference did not match this Customer.'
    if (error.errorCode === 'CUSTOMER_DIGITAL_ACCESS_ALREADY_ENABLED') return 'Digital access is already enabled. Refresh its status.'
    if (error.errorCode === 'CUSTOMER_NOT_ACTIVE') return 'This Customer is not active.'
    if (error.errorCode === 'CUSTOMER_NOT_FOUND') return 'The Customer could not be found.'
  }
  return 'The request could not be completed. Refresh and try again.'
}

function unknownResult(error: unknown): boolean {
  return error instanceof NetworkError || error instanceof ZodError
    || (error instanceof ApiError && error.status >= 500)
}

export function CustomerDigitalAccessPage() {
  const { manager, state } = useAuth()
  const allowed = state.status === 'authenticated' && hasPermission(state.actor, 'customer:intake:manage')
  const [searchMode, setSearchMode] = useState<'number' | 'identity'>('number')
  const [searchValue, setSearchValue] = useState('')
  const [searchResult, setSearchResult] = useState<StaffCustomer>()
  const [selected, setSelected] = useState<StaffCustomer>()
  const [email, setEmail] = useState('')
  const [identityReference, setIdentityReference] = useState('')
  const [searching, setSearching] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [needsReconciliation, setNeedsReconciliation] = useState(false)
  const [feedback, setFeedback] = useState<string>()
  const [searchError, setSearchError] = useState<string>()
  const status = useQuery({
    queryKey: ['staff-customer-digital-access', selected?.customerId],
    queryFn: () => getDigitalAccess(manager, selected!.customerId),
    enabled: allowed && Boolean(selected), retry: false,
  })

  const submitSearch = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSearchResult(undefined)
    setSelected(undefined)
    setFeedback(undefined)
    setNeedsReconciliation(false)
    setSearchError(undefined)
    setSearching(true)
    try {
      const value = searchValue.trim()
      const found = await searchCustomer(manager, searchMode === 'number'
        ? { customerNumber: value } : { identityReference: value })
      setSearchResult(found)
      setSearchValue('')
    } catch (error) {
      setSearchError(errorMessage(error))
    } finally {
      setSearching(false)
    }
  }

  const submitActivation = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selected || !status.data || status.data.enabled || submitting) return
    setSubmitting(true)
    setFeedback(undefined)
    try {
      await enableDigitalAccess(manager, selected.customerId, {
        email: email.trim().toLowerCase(), identityReference,
      })
      setIdentityReference('')
      const result = await status.refetch()
      setNeedsReconciliation(!result.isSuccess || !result.data?.enabled)
      setFeedback(result.isSuccess && result.data?.enabled
        ? 'Digital access is enabled. The Customer must verify the email, then use Forgot password to choose a password.'
        : 'Activation completed, but the latest status could not be confirmed. Refresh status before taking further action.')
    } catch (error) {
      if (unknownResult(error)) {
        setNeedsReconciliation(true)
        const result = await status.refetch()
        if (result.isSuccess) setNeedsReconciliation(false)
        if (result.isSuccess && result.data?.enabled) setIdentityReference('')
        setFeedback(result.isSuccess && result.data?.enabled
          ? 'Digital access is enabled. The Customer must verify the email, then use Forgot password.'
          : 'The activation result is unknown. Check status again before any new request; this page did not resend activation.')
      } else {
        setFeedback(errorMessage(error))
        if (error instanceof ApiError && error.errorCode === 'CUSTOMER_DIGITAL_ACCESS_ALREADY_ENABLED') {
          setNeedsReconciliation(true)
          const result = await status.refetch()
          if (result.isSuccess) setNeedsReconciliation(false)
        }
      }
    } finally {
      setSubmitting(false)
    }
  }

  return <section className="mx-auto max-w-4xl space-y-6">
    <div><p className="text-sm font-semibold text-muted-foreground">CUSTOMER INTAKE</p>
      <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Customer digital access</h1>
      <p className="mt-2 max-w-3xl text-muted-foreground">Digital access links a Customer Web login to this existing Customer record. It does not create a new Customer or change existing Staff-assisted applications.</p>
    </div>
    <form onSubmit={(event) => void submitSearch(event)} className="space-y-4 rounded-lg border bg-card p-5">
      <h2 className="text-lg font-semibold">Find an existing Customer</h2>
      <label className="grid gap-1 text-sm font-medium">Exact search
        <select className="h-11 rounded-md border bg-background px-3" value={searchMode}
          onChange={(event) => { setSearchMode(event.target.value as 'number' | 'identity'); setSearchValue('') }}>
          <option value="number">Customer number</option><option value="identity">Identity reference</option>
        </select>
      </label>
      <label className="grid gap-1 text-sm font-medium">Exact value
        <Input value={searchValue} onChange={(event) => setSearchValue(event.target.value)} required autoComplete="off" />
      </label>
      <Button disabled={!allowed || searching}>{searching ? 'Searching…' : 'Search Customer'}</Button>
      {searchError ? <Alert variant="destructive">{searchError}</Alert> : null}
      {searchResult ? <div className="space-y-2 rounded-md border p-4"><p className="font-semibold">{searchResult.customerNumber} · {searchResult.profile?.fullName ?? 'Name unavailable'}</p>
        <p className="text-sm text-muted-foreground">Status: {searchResult.status}</p>
        <Button type="button" variant="outline" onClick={() => { setSelected(searchResult); setEmail(''); setIdentityReference(''); setFeedback(undefined); setNeedsReconciliation(false) }}>Select Customer</Button>
      </div> : null}
    </form>
    {selected ? <article className="space-y-4 rounded-lg border bg-card p-5">
      <h2 className="text-lg font-semibold">Digital access · {selected.customerNumber}</h2>
      {status.isPending ? <p role="status">Loading digital-access status…</p> : null}
      {status.isError ? <Alert variant="destructive">Status could not be refreshed. The last confirmed state remains visible. <Button type="button" variant="outline" onClick={() => void status.refetch()}>Retry status</Button></Alert> : null}
      {status.data ? <div className="space-y-2"><p>{status.data.enabled ? 'Enabled' : 'Not enabled'}</p>
        {status.data.enabled ? <><p>Email: {status.data.email}</p><p>Email verified: {status.data.emailVerified ? 'Yes' : 'No'}</p></> : null}
      </div> : null}
      {status.data && !status.data.enabled ? <form onSubmit={(event) => void submitActivation(event)} className="space-y-4">
        <p className="text-sm text-muted-foreground">Re-enter the Customer’s presented identity reference. The Customer receives an email verification invitation and then chooses a password through Forgot password.</p>
        <label className="grid gap-1 text-sm font-medium">Email
          <Input type="email" required maxLength={255} value={email} onChange={(event) => setEmail(event.target.value)} />
        </label>
        <label className="grid gap-1 text-sm font-medium">Identity reference
          <Input required autoComplete="off" value={identityReference} onChange={(event) => setIdentityReference(event.target.value)} />
        </label>
        <Button disabled={!allowed || submitting || status.isFetching || needsReconciliation}>{submitting ? 'Enabling…' : 'Enable digital access'}</Button>
      </form> : null}
      {feedback ? <Alert>{feedback}</Alert> : null}
      <Button type="button" variant="outline" disabled={status.isFetching} onClick={() => void status.refetch().then((result) => {
        if (result.isSuccess) setNeedsReconciliation(false)
      })}>Refresh status</Button>
    </article> : null}
  </section>
}
