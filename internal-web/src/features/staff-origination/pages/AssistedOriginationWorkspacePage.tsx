import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import {
  abandonIntake, addBankAccount, attachCustomer, bankAction, createCustomer,
  searchCustomer, updateCustomer, uploadEvidence,
} from '../api/staff-origination-api'
import { banksQuery, customerQuery, evidenceQuery, intakeCaseQuery, originationKeys } from '../api/queries'
import type { CustomerProfileInput, StaffCustomer } from '../api/contracts'

const profileFromForm = (form: HTMLFormElement): CustomerProfileInput & { identityReference?: string } => {
  const data = new FormData(form)
  return {
    fullName: String(data.get('fullName') ?? ''), identityReference: String(data.get('identityReference') ?? '') || undefined,
    phoneNumber: String(data.get('phoneNumber') ?? ''), residentialAddress: String(data.get('residentialAddress') ?? ''),
    employmentStatus: String(data.get('employmentStatus') ?? ''), employerName: String(data.get('employerName') ?? '') || undefined,
    termsConsentAccepted: data.get('termsConsentAccepted') === 'on', dataProcessingConsentAccepted: data.get('dataProcessingConsentAccepted') === 'on',
  }
}

function ProfileFields({ customer }: { customer?: StaffCustomer }) {
  const profile = customer?.profile
  return <div className="grid gap-3 sm:grid-cols-2">
    <label className="grid gap-1 text-sm font-medium">Full name<Input name="fullName" required defaultValue={profile?.fullName} /></label>
    <label className="grid gap-1 text-sm font-medium">Identity reference<Input name="identityReference" required={!customer} /></label>
    <label className="grid gap-1 text-sm font-medium">Phone<Input name="phoneNumber" required defaultValue={profile?.phoneNumber} /></label>
    <label className="grid gap-1 text-sm font-medium">Employment status<Input name="employmentStatus" required defaultValue={profile?.employmentStatus} /></label>
    <label className="grid gap-1 text-sm font-medium sm:col-span-2">Residential address<Input name="residentialAddress" required defaultValue={profile?.residentialAddress} /></label>
    <label className="grid gap-1 text-sm font-medium">Employer<Input name="employerName" defaultValue={profile?.employerName ?? ''} /></label>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" name="termsConsentAccepted" defaultChecked={profile?.termsConsentAccepted} /> Signed application consent recorded</label>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" name="dataProcessingConsentAccepted" defaultChecked={profile?.dataProcessingConsentAccepted} /> Data processing consent recorded</label>
  </div>
}

export function AssistedOriginationWorkspacePage() {
  const { assistedOriginationCaseId = '' } = useParams(); const { manager, state } = useAuth(); const client = useQueryClient(); const navigate = useNavigate()
  const actor = state.status === 'authenticated' ? state.actor : undefined
  const canOriginate = Boolean(actor && hasPermission(actor, 'loan:originate:staff'))
  const canCustomer = Boolean(actor && hasPermission(actor, 'customer:intake:manage') && hasPermission(actor, 'customer:read'))
  const canEvidence = Boolean(actor && hasPermission(actor, 'document:upload:intake'))
  const intake = useQuery(intakeCaseQuery(manager, assistedOriginationCaseId, canOriginate && Boolean(assistedOriginationCaseId)))
  const customerId = intake.data?.customerId ?? ''
  const customer = useQuery(customerQuery(manager, customerId, canCustomer && Boolean(customerId)))
  const banks = useQuery(banksQuery(manager, customerId, canCustomer && Boolean(customerId)))
  const evidence = useQuery(evidenceQuery(manager, assistedOriginationCaseId, canEvidence && Boolean(assistedOriginationCaseId)))
  const [searchResult, setSearchResult] = useState<StaffCustomer>()
  const refresh = async () => { await Promise.all([client.invalidateQueries({ queryKey: originationKeys.case(assistedOriginationCaseId) }), client.invalidateQueries({ queryKey: originationKeys.customer(customerId) }), client.invalidateQueries({ queryKey: originationKeys.banks(customerId) }), client.invalidateQueries({ queryKey: originationKeys.evidence(assistedOriginationCaseId) })]) }
  const command = useMutation({ mutationFn: async (task: () => Promise<unknown>) => task(), onSuccess: refresh })

  if (intake.isPending) return <p className="flex items-center gap-2"><Spinner /> Loading intake workspace…</p>
  if (intake.isError || !intake.data) return <section><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Intake unavailable</h1><Button className="mt-4" asChild variant="outline"><Link to="/staff/origination">Back to intake</Link></Button></section>
  const open = intake.data.status === 'OPEN'
  const selectedEvidenceType = intake.data.productCode === 'UNSECURED_CONSUMER_LOAN' ? 'UCL_PAPER_APPLICATION' : 'COLLATERAL_PAPER_APPLICATION'

  const submitSearch = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const data = new FormData(event.currentTarget); const mode = String(data.get('mode')); const value = String(data.get('value')); command.mutate(() => searchCustomer(manager, mode === 'number' ? { customerNumber: value } : { identityReference: value }).then((result) => { setSearchResult(result); return result })) }
  const submitCreate = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const input = profileFromForm(event.currentTarget); if (!input.identityReference) return; command.mutate(() => createCustomer(manager, input as CustomerProfileInput & { identityReference: string }).then(async (created) => { await attachCustomer(manager, assistedOriginationCaseId, created.customerId); return created })) }
  const submitProfile = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); command.mutate(() => updateCustomer(manager, customerId, profileFromForm(event.currentTarget))) }
  const submitBank = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const data = Object.fromEntries(new FormData(event.currentTarget)) as Record<string, string>; command.mutate(() => addBankAccount(manager, customerId, data)); event.currentTarget.reset() }
  const submitEvidence = (evidenceType: string) => (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const data = new FormData(event.currentTarget); const file = data.get('file'); if (!(file instanceof File) || file.size === 0) return; const current = evidence.data?.find((item) => item.evidenceType === evidenceType)?.currentVersionId ?? undefined; command.mutate(() => uploadEvidence(manager, assistedOriginationCaseId, evidenceType, file, crypto.randomUUID(), current).then(refresh)); event.currentTarget.reset() }

  return <section className="mx-auto max-w-6xl space-y-6">
    <div><Button asChild variant="link"><Link to="/staff/origination">← Paper intake</Link></Button><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold sm:text-3xl">{intake.data.productCode === 'UNSECURED_CONSUMER_LOAN' ? 'UCL' : 'Collateral Loan'} intake</h1><p className="mt-1 text-muted-foreground">Status: {intake.data.status} · No LoanApplication exists for this intake.</p></div>
    {!open ? <div className="rounded-lg border border-warning/40 bg-warning/10 p-4">This intake is terminal. Customer association and evidence upload are disabled.</div> : null}

    {canCustomer ? <div className="grid gap-6 lg:grid-cols-2">
      <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Find Customer</h2><form onSubmit={submitSearch} className="space-y-3"><label className="grid gap-1 text-sm font-medium">Exact search<select name="mode" className="h-11 rounded-md border bg-background px-3"><option value="number">Customer number</option><option value="identity">Identity reference</option></select></label><label className="grid gap-1 text-sm font-medium">Exact value<Input name="value" required autoComplete="off" /></label><Button disabled={!open || command.isPending}>Search</Button></form>{searchResult ? <div className="rounded-md border p-3"><p className="font-semibold">{searchResult.customerNumber} · {searchResult.profile?.fullName}</p><Button className="mt-2" disabled={!open} onClick={() => command.mutate(() => attachCustomer(manager, assistedOriginationCaseId, searchResult.customerId))}>Select Customer</Button></div> : null}</article>
      {!customerId ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Create Customer</h2><form onSubmit={submitCreate} className="space-y-4"><ProfileFields /><Button disabled={!open || command.isPending}>Create and select Customer</Button></form></article> : null}
    </div> : <p className="rounded-lg border p-4 text-sm">Customer intake permissions are required to find or maintain the selected Customer.</p>}

    {customer.isPending && customerId && canCustomer ? <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading selected Customer…</p> : null}
    {customer.isError ? <div className="rounded-lg border border-danger/30 p-4"><p role="alert">The selected Customer could not be loaded.</p><Button className="mt-3" variant="outline" onClick={() => void customer.refetch()}>Retry Customer</Button></div> : null}
    {customer.data ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Selected Customer · {customer.data.customerNumber}</h2><form onSubmit={submitProfile} className="space-y-4"><ProfileFields customer={customer.data} /><Button disabled={!open || command.isPending}>Save profile</Button></form></article> : null}

    {customer.data ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Bank accounts</h2>{banks.isPending ? <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading bank accounts…</p> : null}{banks.isError ? <div><p role="alert">Bank accounts could not be loaded.</p><Button className="mt-2" variant="outline" onClick={() => void banks.refetch()}>Retry bank accounts</Button></div> : null}{banks.data?.length === 0 ? <p className="text-sm text-muted-foreground">No bank accounts have been recorded.</p> : null}{banks.data?.map((bank) => <div key={bank.customerBankAccountId} className="flex flex-wrap items-center justify-between gap-3 rounded-md border p-3"><span>{bank.bankNameSnapshot} · {bank.maskedAccountNumber} · {bank.status}{bank.primaryAccount ? ' · Primary' : ''}</span><div className="flex gap-2">{bank.status === 'ACTIVE' && !bank.primaryAccount ? <Button size="sm" variant="outline" disabled={!open} onClick={() => command.mutate(() => bankAction(manager, customerId, bank.customerBankAccountId, 'make-primary'))}>Make primary</Button> : null}<Button size="sm" variant="outline" disabled={!open || bank.status !== 'ACTIVE'} onClick={() => command.mutate(() => bankAction(manager, customerId, bank.customerBankAccountId, 'deactivate'))}>Deactivate</Button></div></div>)}<form onSubmit={submitBank} className="grid gap-3 sm:grid-cols-2"><Input name="bankCode" required aria-label="Bank code" placeholder="Bank code" /><Input name="bankNameSnapshot" required aria-label="Bank name" placeholder="Bank name" /><Input name="accountHolderName" required aria-label="Account holder" placeholder="Account holder" /><Input name="accountNumber" required aria-label="Account number" placeholder="Account number" autoComplete="off" /><Button disabled={!open || command.isPending}>Add bank account</Button></form></article> : null}

    {canEvidence ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Paper intake evidence</h2>{evidence.isPending ? <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading evidence metadata…</p> : null}{evidence.isError ? <div><p role="alert">Evidence metadata could not be loaded.</p><Button className="mt-2" variant="outline" onClick={() => void evidence.refetch()}>Retry evidence</Button></div> : null}{evidence.isSuccess ? ['CUSTOMER_IDENTITY', selectedEvidenceType].map((type) => { const item = evidence.data.find((candidate) => candidate.evidenceType === type); const label = type === 'CUSTOMER_IDENTITY' ? 'Customer identity / CCCD' : 'Signed paper application'; return <div key={type} className="rounded-md border p-3"><h3 className="font-medium">{label}</h3><p className="text-sm text-muted-foreground">{item ? `${item.versions.length} version(s); current ${item.currentVersionId}` : 'No evidence uploaded'}</p><form className="mt-3 flex flex-col gap-2 sm:flex-row" onSubmit={submitEvidence(type)}><Input aria-label={`${label} file`} type="file" name="file" required accept="application/pdf,image/jpeg,image/png" /><Button disabled={!open || command.isPending}>{item ? 'Replace evidence' : 'Upload evidence'}</Button></form></div> }) : null}</article> : null}

    {command.isError ? <p role="alert" className="rounded-md border border-danger/30 p-3 text-danger">The operation failed. The server did not confirm a change; review the current state and retry.</p> : null}
    {open ? <div className="rounded-lg border border-danger/30 p-4"><h2 className="font-semibold">Abandon intake</h2><p className="mt-1 text-sm text-muted-foreground">Abandonment is terminal and blocks further Customer association and paper-evidence changes.</p><Button className="mt-3" variant="destructive" disabled={command.isPending} onClick={() => { if (window.confirm('Abandon this intake permanently?')) command.mutate(() => abandonIntake(manager, assistedOriginationCaseId).then(() => navigate('/staff/origination'))) }}>Abandon intake</Button></div> : null}
  </section>
}
