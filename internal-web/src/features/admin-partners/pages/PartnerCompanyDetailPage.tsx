import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { ApiError, NetworkError } from '@/lib/api'
import { formatVnd } from '@/lib/format/presentation'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'
import {
  importPartnerEmployeesInputSchema,
  updatePartnerCompanyInputSchema,
  type ImportPartnerEmployeesInput,
  type PartnerImportResult,
  type UpdatePartnerCompanyInput,
} from '../api/contracts'
import { changePartnerCompanyStatus, importPartnerEmployees, updatePartnerCompany } from '../api/partner-admin-api'
import { partnerAdminKeys, partnerCompanyQuery, partnerEmployeesQuery, partnerImportBatchesQuery } from '../api/queries'
import { PartnerQueryErrorPanel } from '../components/PartnerQueryErrorPanel'

const companyStatuses = ['ACTIVE', 'INACTIVE', 'SUSPENDED'] as const
const employeeStatuses = ['ACTIVE', 'INACTIVE', 'TERMINATED', 'SUSPENDED'] as const
const batchStatuses = new Set(['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'])
const labelKnown = (value: string, known: ReadonlySet<string>) => known.has(value) ? value.replaceAll('_', ' ') : 'Unknown value'
type PendingImport = { requestId: string; input: ImportPartnerEmployeesInput }

const emptyRow = (): ImportPartnerEmployeesInput['rows'][number] => ({
  employeeCode: '', identityReference: '', salaryAmount: 0, salaryAdvanceLimit: 0,
  employmentStatus: 'ACTIVE', active: true,
})

export function PartnerCompanyDetailPage() {
  const { partnerCompanyId = '' } = useParams()
  const validId = meridianUuidSchema.safeParse(partnerCompanyId).success
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const enabled = validId && state.status === 'authenticated'
  const canManage = state.status === 'authenticated' && hasPermission(state.actor, 'partner:manage')
  const company = useQuery(partnerCompanyQuery(manager, partnerCompanyId, enabled))
  const employees = useQuery(partnerEmployeesQuery(manager, partnerCompanyId, enabled))
  const batches = useQuery(partnerImportBatchesQuery(manager, partnerCompanyId, enabled))
  const edit = useForm<UpdatePartnerCompanyInput>({ defaultValues: { name: '', salaryAdvancePolicyLimit: 0 } })
  const importer = useForm<ImportPartnerEmployeesInput>({ defaultValues: { effectiveMonth: '', rows: [emptyRow()] } })
  const rowFields = useFieldArray({ control: importer.control, name: 'rows' })
  const [commandError, setCommandError] = useState<Error>()
  const [commandMessage, setCommandMessage] = useState<string>()
  const [importResult, setImportResult] = useState<PartnerImportResult>()
  const [pendingImport, setPendingImport] = useState<PendingImport>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (company.data) edit.reset({ name: company.data.name, salaryAdvancePolicyLimit: company.data.salaryAdvancePolicyLimit })
  }, [company.data, edit])

  const refreshCompany = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: partnerAdminKeys.company(partnerCompanyId) }),
    queryClient.invalidateQueries({ queryKey: partnerAdminKeys.companies() }),
  ])

  const submitEdit = edit.handleSubmit(async (raw) => {
    const parsed = updatePartnerCompanyInputSchema.safeParse(raw)
    if (!parsed.success) { setCommandError(new Error('Review the company details and try again.')); return }
    setBusy(true); setCommandError(undefined); setCommandMessage(undefined)
    try { await updatePartnerCompany(manager, partnerCompanyId, parsed.data); await refreshCompany(); setCommandMessage('Company details updated.') }
    catch (error) { setCommandError(error instanceof Error ? error : new Error('Update failed.')) }
    finally { setBusy(false) }
  })

  const setStatus = async (status: string) => {
    setBusy(true); setCommandError(undefined); setCommandMessage(undefined)
    try { await changePartnerCompanyStatus(manager, partnerCompanyId, status); await refreshCompany(); setCommandMessage('Company status updated.') }
    catch (error) { setCommandError(error instanceof Error ? error : new Error('Status change failed.')) }
    finally { setBusy(false) }
  }

  const executeImport = async (operation: PendingImport) => {
    setBusy(true); setCommandError(undefined); setCommandMessage(undefined)
    try {
      const result = await importPartnerEmployees(manager, partnerCompanyId, operation.requestId, operation.input)
      setImportResult(result); setPendingImport(undefined)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: partnerAdminKeys.employees(partnerCompanyId) }),
        queryClient.invalidateQueries({ queryKey: partnerAdminKeys.importBatches(partnerCompanyId) }),
      ])
      setCommandMessage('Employee import completed.')
    } catch (error) {
      if (error instanceof NetworkError || (error instanceof ApiError && error.status >= 500)) setPendingImport(operation)
      setCommandError(error instanceof Error ? error : new Error('Import failed.'))
    } finally { setBusy(false) }
  }

  const submitImport = importer.handleSubmit(async (raw) => {
    const parsed = importPartnerEmployeesInputSchema.safeParse(raw)
    if (!parsed.success) { setCommandError(new Error('Review the effective month and employee rows.')); return }
    await executeImport({ requestId: crypto.randomUUID(), input: parsed.data })
  })

  if (!validId) return <section><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Partner Company unavailable</h1><p className="mt-2 text-muted-foreground">The Partner Company identifier is invalid.</p></section>
  if (company.isPending || employees.isPending || batches.isPending) return <div className="flex items-center gap-2"><Spinner /> Loading Partner workspace…</div>
  if (company.isError) return <PartnerQueryErrorPanel error={company.error} onRetry={() => void company.refetch()} />

  return <section className="mx-auto max-w-7xl space-y-6">
    <div><Link className="text-sm font-medium text-primary underline" to="/admin/partners">Back to Partner Companies</Link><h1 data-route-heading tabIndex={-1} className="mt-2 text-2xl font-semibold tracking-tight sm:text-3xl">{company.data.name}</h1><p className="mt-1 text-muted-foreground">{company.data.companyCode} · {labelKnown(company.data.status, new Set(companyStatuses))}</p></div>
    {commandMessage ? <Alert variant="success"><AlertTitle>Command confirmed</AlertTitle><AlertDescription>{commandMessage}</AlertDescription></Alert> : null}
    {commandError ? <Alert variant="destructive"><AlertTitle>{pendingImport ? 'Import result unknown' : 'Command was not completed'}</AlertTitle><AlertDescription>{pendingImport ? 'The response was not confirmed. Retry the exact request ID and unchanged in-memory payload.' : commandError.message}{commandError instanceof ApiError && commandError.requestId ? ` Support reference: ${commandError.requestId}.` : ''}</AlertDescription>{pendingImport ? <div className="mt-3 flex flex-wrap gap-2"><Button variant="outline" disabled={busy} onClick={() => void executeImport(pendingImport)}>Retry exact import</Button><Button variant="outline" disabled={busy} onClick={() => { setPendingImport(undefined); setCommandError(undefined) }}>Discard and start a new import</Button></div> : null}</Alert> : null}

    <div className="grid gap-6 xl:grid-cols-2">
      <Card><CardHeader><CardTitle>General details and policy limit</CardTitle></CardHeader><CardContent className="space-y-4"><dl className="grid gap-4 sm:grid-cols-2"><div><dt className="text-xs uppercase text-muted-foreground">Company code</dt><dd className="font-mono font-semibold">{company.data.companyCode}</dd></div><div><dt className="text-xs uppercase text-muted-foreground">Status</dt><dd className="font-semibold">{labelKnown(company.data.status, new Set(companyStatuses))}</dd></div><div><dt className="text-xs uppercase text-muted-foreground">Policy limit</dt><dd className="font-semibold">{formatVnd(company.data.salaryAdvancePolicyLimit)}</dd></div></dl>
        {canManage ? <form className="space-y-3 border-t pt-4" onSubmit={submitEdit}><label className="block space-y-1 text-sm font-medium">Company name<Input {...edit.register('name')} maxLength={200} /></label><label className="block space-y-1 text-sm font-medium">Salary Advance policy limit<Input type="number" min="0" step="0.01" {...edit.register('salaryAdvancePolicyLimit', { valueAsNumber: true })} /></label><Button disabled={busy} type="submit">Save details</Button></form> : null}
      </CardContent></Card>
      <Card><CardHeader><CardTitle>Company status</CardTitle></CardHeader><CardContent><p className="text-sm text-muted-foreground">Status controls future Salary Advance eligibility. Historical lending records are unchanged.</p>{canManage && companyStatuses.includes(company.data.status as typeof companyStatuses[number]) ? <div className="mt-4 flex flex-wrap gap-2">{companyStatuses.filter((status) => status !== company.data.status).map((status) => <Button key={status} variant="outline" disabled={busy} onClick={() => void setStatus(status)}>Set {status.toLowerCase()}</Button>)}</div> : null}</CardContent></Card>
    </div>

    <Card><CardHeader><CardTitle>Partner Employees</CardTitle></CardHeader><CardContent>{employees.isError ? <PartnerQueryErrorPanel error={employees.error} onRetry={() => void employees.refetch()} /> : employees.data?.length === 0 ? <p className="text-sm text-muted-foreground">No employee source rows are available.</p> : <div className="overflow-x-auto"><table className="w-full min-w-[65rem] text-left text-sm"><caption className="sr-only">Partner Employee source rows</caption><thead><tr className="border-b"><th className="p-2">Employee code</th><th className="p-2">Identity reference</th><th className="p-2">Salary</th><th className="p-2">Advance limit</th><th className="p-2">Employment</th><th className="p-2">Active</th></tr></thead><tbody>{employees.data?.map((employee) => <tr className="border-b" key={employee.id}><td className="p-2">{employee.employeeCode}</td><td className="p-2">{employee.identityReference}</td><td className="p-2">{formatVnd(employee.salaryAmount)}</td><td className="p-2">{formatVnd(employee.salaryAdvanceLimit)}</td><td className="p-2">{labelKnown(employee.employmentStatus, new Set(employeeStatuses))}</td><td className="p-2">{employee.active ? 'Yes' : 'No'}</td></tr>)}</tbody></table></div>}</CardContent></Card>

    <Card><CardHeader><CardTitle>Employee import history</CardTitle></CardHeader><CardContent>{batches.isError ? <PartnerQueryErrorPanel error={batches.error} onRetry={() => void batches.refetch()} /> : batches.data?.length === 0 ? <p className="text-sm text-muted-foreground">No import batches are available.</p> : <div className="overflow-x-auto"><table className="w-full min-w-[36rem] text-left text-sm"><caption className="sr-only">Employee import history</caption><thead><tr className="border-b"><th className="p-2">Effective month</th><th className="p-2">Status</th><th className="p-2">Valid rows</th><th className="p-2">Invalid rows</th></tr></thead><tbody>{batches.data?.map((batch) => <tr className="border-b" key={batch.id}><td className="p-2">{batch.effectiveMonth}</td><td className="p-2">{labelKnown(batch.status, batchStatuses)}</td><td className="p-2">{batch.validRowCount}</td><td className="p-2">{batch.invalidRowCount}</td></tr>)}</tbody></table></div>}</CardContent></Card>

    {canManage ? <Card><CardHeader><CardTitle>Import effective-month employees</CardTitle></CardHeader><CardContent><form className="space-y-5" onSubmit={submitImport}><label className="block max-w-xs space-y-1 text-sm font-medium">Effective month<Input type="month" {...importer.register('effectiveMonth')} /></label>{rowFields.fields.map((field, index) => <fieldset className="grid gap-3 rounded-md border p-4 md:grid-cols-3" key={field.id}><legend className="px-1 text-sm font-semibold">Employee row {index + 1}</legend><label className="space-y-1 text-sm">Employee code<Input {...importer.register(`rows.${index}.employeeCode`)} /></label><label className="space-y-1 text-sm">Identity reference<Input {...importer.register(`rows.${index}.identityReference`)} /></label><label className="space-y-1 text-sm">Employment status<select className="flex h-10 w-full rounded-md border bg-background px-3" {...importer.register(`rows.${index}.employmentStatus`)}>{employeeStatuses.map((status) => <option key={status} value={status}>{status}</option>)}</select></label><label className="space-y-1 text-sm">Salary amount<Input type="number" min="0" step="0.01" {...importer.register(`rows.${index}.salaryAmount`, { valueAsNumber: true })} /></label><label className="space-y-1 text-sm">Salary Advance limit<Input type="number" min="0" step="0.01" {...importer.register(`rows.${index}.salaryAdvanceLimit`, { valueAsNumber: true })} /></label><label className="flex items-center gap-2 self-end text-sm"><input type="checkbox" {...importer.register(`rows.${index}.active`)} /> Active source row</label><div className="md:col-span-3"><Button type="button" variant="outline" disabled={rowFields.fields.length === 1 || Boolean(pendingImport)} onClick={() => rowFields.remove(index)}>Remove row</Button></div></fieldset>)}<div className="flex flex-wrap gap-2"><Button type="button" variant="outline" disabled={Boolean(pendingImport)} onClick={() => rowFields.append(emptyRow())}>Add employee row</Button><Button type="submit" disabled={busy || Boolean(pendingImport)}>{busy ? 'Importing…' : 'Import employees'}</Button></div></form>
      {importResult ? <Alert variant="success" className="mt-5"><AlertTitle>Import {importResult.status.toLowerCase()}</AlertTitle><AlertDescription>{importResult.validRowCount} valid row(s), {importResult.invalidRowCount} invalid row(s).{importResult.rejections.length ? <ul className="mt-2 list-disc pl-5">{importResult.rejections.map((rejection) => <li key={`${rejection.rowIndex}-${rejection.errorCode}`}>Row {rejection.rowIndex}: {rejection.reason}</li>)}</ul> : null}</AlertDescription></Alert> : null}
    </CardContent></Card> : null}
  </section>
}
