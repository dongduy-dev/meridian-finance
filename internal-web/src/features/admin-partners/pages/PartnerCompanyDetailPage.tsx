import { ConfirmationDialog, ConfirmationDialogCancel, ConfirmationDialogTitle, ConfirmationDialogDescription, ConfirmationDialogFooter } from '@/components/ui/confirmation-dialog'
import { knownLabel } from '@/lib/format/presentation'
import { employeeStatusLabel, importRejectionLabel } from '../model/presentation'
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
import { partnerAdminKeys, partnerCompanyQuery, currentPartnerEmployeeSnapshotQuery, partnerImportBatchesQuery } from '../api/queries'
import { PartnerEmployeeCsvImport } from '../components/PartnerEmployeeCsvImport'
import { PartnerQueryErrorPanel } from '../components/PartnerQueryErrorPanel'

const companyStatuses = ['ACTIVE', 'INACTIVE', 'SUSPENDED'] as const
type CompanyStatus = (typeof companyStatuses)[number]
const employeeStatuses = ['ACTIVE', 'INACTIVE', 'TERMINATED', 'SUSPENDED'] as const
const companyStatusLabels: Record<string, string> = { ACTIVE: 'Active', INACTIVE: 'Inactive', SUSPENDED: 'Suspended' }
const batchStatusLabels: Record<string, string> = { PENDING: 'Pending', PROCESSING: 'Processing', COMPLETED: 'Completed', FAILED: 'Failed' }
const companyStatusLabel = (value: string) => knownLabel(companyStatusLabels, value, 'Company status unavailable')
const batchStatusLabel = (value: string) => knownLabel(batchStatusLabels, value, 'Import status unavailable')
const changeErrorMessage = (error: Error) =>
  error instanceof ApiError && error.errorCode === 'IDEMPOTENCY_KEY_REUSED'
    ? 'The saved import cannot be retried. Refresh the current employee list and import history before starting another import.'
    : 'Meridian could not confirm the change. Refresh and review the current details before trying again.'
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
  const snapshot = useQuery(currentPartnerEmployeeSnapshotQuery(manager, partnerCompanyId, enabled))
  const batches = useQuery(partnerImportBatchesQuery(manager, partnerCompanyId, enabled))
  const edit = useForm<UpdatePartnerCompanyInput>({ defaultValues: { name: '', salaryAdvancePolicyLimit: 0 } })
  const importer = useForm<ImportPartnerEmployeesInput>({ defaultValues: { effectiveMonth: '', rows: [emptyRow()] } })
  const rowFields = useFieldArray({ control: importer.control, name: 'rows' })
  const [commandError, setCommandError] = useState<Error>()
  const [commandMessage, setCommandMessage] = useState<string>()
  const [importResult, setImportResult] = useState<PartnerImportResult>()
  const [pendingImport, setPendingImport] = useState<PendingImport>()
  const [importMethod, setImportMethod] = useState<'csv' | 'manual'>('csv')
  const [csvResetKey, setCsvResetKey] = useState(0)
  const [busy, setBusy] = useState(false)
  const [statusConfirmation, setStatusConfirmation] = useState<Exclude<CompanyStatus, 'ACTIVE'>>()

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

  const setStatus = async (status: CompanyStatus) => {
    setBusy(true); setCommandError(undefined); setCommandMessage(undefined)
    try { await changePartnerCompanyStatus(manager, partnerCompanyId, status); await refreshCompany(); setCommandMessage('Company status updated.') }
    catch (error) { setCommandError(error instanceof Error ? error : new Error('Status change failed.')) }
    finally { setBusy(false) }
  }

  const executeImport = async (operation: PendingImport) => {
    setBusy(true); setCommandError(undefined); setCommandMessage(undefined); setImportResult(undefined)
    try {
      const result = await importPartnerEmployees(manager, partnerCompanyId, operation.requestId, operation.input)
      setImportResult(result); setPendingImport(undefined)
      if (result.status === 'COMPLETED') {
        setCsvResetKey((value) => value + 1)
        importer.reset({ effectiveMonth: operation.input.effectiveMonth, rows: [emptyRow()] })
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: partnerAdminKeys.currentEmployees(partnerCompanyId) }),
        queryClient.invalidateQueries({ queryKey: partnerAdminKeys.employees(partnerCompanyId) }),
        queryClient.invalidateQueries({ queryKey: partnerAdminKeys.importBatches(partnerCompanyId) }),
      ])
      if (result.status === 'COMPLETED') setCommandMessage('Employee import completed.')
    } catch (error) {
      if (error instanceof NetworkError || (error instanceof ApiError && error.status >= 500)) setPendingImport(operation)
      else setPendingImport(undefined)
      setCommandError(error instanceof Error ? error : new Error('Import failed.'))
    } finally { setBusy(false) }
  }

  const submitRows = async (rows: ImportPartnerEmployeesInput['rows']) => {
    const parsed = importPartnerEmployeesInputSchema.safeParse({
      effectiveMonth: importer.getValues('effectiveMonth'),
      rows,
    })
    if (!parsed.success) { setCommandError(new Error('Review the effective month and employee rows.')); return }
    await executeImport({ requestId: crypto.randomUUID(), input: parsed.data })
  }

  const submitManualImport = importer.handleSubmit(async (raw) => submitRows(raw.rows))

  if (!validId) return <section><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Partner Company unavailable</h1><p className="mt-2 text-muted-foreground">The Partner Company identifier is invalid.</p></section>
  if (company.isPending || snapshot.isPending || batches.isPending) return <div className="flex items-center gap-2"><Spinner /> Loading Partner workspace…</div>
  if (company.isError) return <PartnerQueryErrorPanel error={company.error} onRetry={() => void company.refetch()} />

  return <section className="mx-auto max-w-7xl space-y-6">
    <div><Link className="text-sm font-medium text-primary underline" to="/admin/partners">Back to Partner Companies</Link><h1 data-route-heading tabIndex={-1} className="mt-2 text-2xl font-semibold tracking-tight sm:text-3xl">{company.data.name}</h1><p className="mt-1 text-muted-foreground">{company.data.companyCode} · {companyStatusLabel(company.data.status)}</p></div>
    {commandMessage ? <Alert variant="success"><AlertTitle>Change confirmed</AlertTitle><AlertDescription>{commandMessage}</AlertDescription></Alert> : null}
    {commandError ? <Alert variant="destructive"><AlertTitle>{pendingImport ? 'We could not confirm the import' : 'Change was not confirmed'}</AlertTitle><AlertDescription>{pendingImport ? 'Meridian has not confirmed the result. Use Retry this import to check the same submission. Keep this page open until the result is confirmed; leaving or reloading does not mean the import failed.' : changeErrorMessage(commandError)}{commandError instanceof ApiError && commandError.requestId ? ` Support reference: ${commandError.requestId}.` : ''}</AlertDescription>{pendingImport ? <div className="mt-3 flex flex-wrap gap-2"><Button variant="outline" disabled={busy} onClick={() => void executeImport(pendingImport)}>Retry this import</Button></div> : null}</Alert> : null}

    <div className="grid gap-6 xl:grid-cols-2">
      <Card><CardHeader><CardTitle>General details and policy limit</CardTitle></CardHeader><CardContent className="space-y-4"><dl className="grid gap-4 sm:grid-cols-2"><div><dt className="text-xs uppercase text-muted-foreground">Company code</dt><dd className="font-mono font-semibold">{company.data.companyCode}</dd></div><div><dt className="text-xs uppercase text-muted-foreground">Status</dt><dd className="font-semibold">{companyStatusLabel(company.data.status)}</dd></div><div><dt className="text-xs uppercase text-muted-foreground">Policy limit</dt><dd className="font-semibold">{formatVnd(company.data.salaryAdvancePolicyLimit)}</dd></div></dl>
        {canManage ? <form className="space-y-3 border-t pt-4" onSubmit={submitEdit}><label className="block space-y-1 text-sm font-medium">Company name<Input {...edit.register('name')} maxLength={200} /></label><label className="block space-y-1 text-sm font-medium">Salary Advance policy limit<Input type="number" min="0" step="0.01" {...edit.register('salaryAdvancePolicyLimit', { valueAsNumber: true })} /></label><Button disabled={busy} type="submit">Save details</Button></form> : null}
      </CardContent></Card>
      <Card><CardHeader><CardTitle>Company status</CardTitle></CardHeader><CardContent><p className="text-sm text-muted-foreground">Status controls future Salary Advance eligibility. Historical lending records are unchanged.</p>{canManage && companyStatuses.includes(company.data.status as CompanyStatus) ? <div className="mt-4 flex flex-wrap gap-2">{companyStatuses.filter((status) => status !== company.data.status).map((status) => <Button id={`partner-status-${status.toLowerCase()}-trigger`} key={status} variant="outline" disabled={busy} onClick={() => status === 'ACTIVE' ? void setStatus(status) : setStatusConfirmation(status)}>Set {status.toLowerCase()}</Button>)}</div> : null}</CardContent></Card>
    </div>

    <Card><CardHeader><CardTitle>Current employee import</CardTitle></CardHeader><CardContent className="space-y-4">{snapshot.isError ? <PartnerQueryErrorPanel error={snapshot.error} onRetry={() => void snapshot.refetch()} /> : <><dl className="grid gap-4 text-sm sm:grid-cols-2"><div><dt className="text-muted-foreground">Effective month</dt><dd className="font-semibold">{snapshot.data?.effectiveMonth}</dd></div><div><dt className="text-muted-foreground">Employees in current import</dt><dd className="font-semibold">{snapshot.data?.employees.length}</dd></div><div><dt className="text-muted-foreground">Current import reference</dt><dd className="break-all font-mono">{snapshot.data?.authoritativeBatchId ?? 'None'}</dd></div></dl>{snapshot.data?.authoritativeBatchId === null ? <p className="text-sm text-muted-foreground">No completed employee import is available for {snapshot.data.effectiveMonth}. Complete an employee import for the current effective month.</p> : <><p className="text-sm text-muted-foreground">Complete employee roster from the latest valid import for this effective month. Older imports remain available as history.</p>{snapshot.data?.employees.length === 0 ? <p className="text-sm text-muted-foreground">The current import has no employee rows.</p> : <div className="overflow-x-auto"><table className="w-full min-w-[65rem] text-left text-sm"><caption className="sr-only">Current Partner employees</caption><thead><tr className="border-b"><th className="p-2">Employee code</th><th className="p-2">Masked identity reference</th><th className="p-2">Salary</th><th className="p-2">Advance limit</th><th className="p-2">Employment</th><th className="p-2">Active</th></tr></thead><tbody>{snapshot.data?.employees.map((employee) => <tr className="border-b" key={employee.id}><td className="p-2">{employee.employeeCode}</td><td className="p-2">{employee.maskedIdentityReference}</td><td className="p-2">{formatVnd(employee.salaryAmount)}</td><td className="p-2">{formatVnd(employee.salaryAdvanceLimit)}</td><td className="p-2">{employeeStatusLabel(employee.employmentStatus)}</td><td className="p-2">{employee.active ? 'Yes' : 'No'}</td></tr>)}</tbody></table></div>}</>}</>}</CardContent></Card>

    <Card><CardHeader><CardTitle>Employee import history</CardTitle></CardHeader><CardContent>{batches.isError ? <PartnerQueryErrorPanel error={batches.error} onRetry={() => void batches.refetch()} /> : batches.data?.length === 0 ? <p className="text-sm text-muted-foreground">No employee imports have been recorded.</p> : <div className="overflow-x-auto"><table className="w-full min-w-[42rem] text-left text-sm"><caption className="sr-only">Employee import history</caption><thead><tr className="border-b"><th className="p-2">Effective month</th><th className="p-2">Import reference</th><th className="p-2">Used for eligibility</th><th className="p-2">Status</th><th className="p-2">Valid rows</th><th className="p-2">Invalid rows</th></tr></thead><tbody>{batches.data?.map((batch) => <tr className="border-b" key={batch.id}><td className="p-2">{batch.effectiveMonth}</td><td className="break-all p-2 font-mono">{batch.id}</td><td className="p-2">{batch.id === snapshot.data?.authoritativeBatchId ? 'Current' : '—'}</td><td className="p-2">{batchStatusLabel(batch.status)}</td><td className="p-2">{batch.validRowCount}</td><td className="p-2">{batch.invalidRowCount}</td></tr>)}</tbody></table></div>}</CardContent></Card>

    {canManage ? <Card><CardHeader><CardTitle>Replace monthly employee roster</CardTitle></CardHeader><CardContent><p className="mb-4 text-sm text-muted-foreground">A valid import replaces the complete employee roster for the selected month. Include every employee record Meridian should use for eligibility. Any invalid row prevents replacement; the previous valid roster continues to apply.</p><form className="space-y-5" onSubmit={importMethod === 'manual' ? submitManualImport : (event) => event.preventDefault()}><fieldset className="space-y-5" disabled={busy || Boolean(pendingImport)}><label className="block max-w-xs space-y-1 text-sm font-medium">Effective month<Input type="month" {...importer.register('effectiveMonth')} /></label><fieldset className="space-y-2"><legend className="text-sm font-semibold">Import method</legend><div className="flex flex-wrap gap-4"><label className="flex items-center gap-2 text-sm"><input checked={importMethod === 'csv'} name="import-method" type="radio" onChange={() => setImportMethod('csv')} /> Upload CSV</label><label className="flex items-center gap-2 text-sm"><input checked={importMethod === 'manual'} name="import-method" type="radio" onChange={() => setImportMethod('manual')} /> Enter manually</label></div></fieldset>{importMethod === 'csv' ? <PartnerEmployeeCsvImport key={csvResetKey} disabled={busy || Boolean(pendingImport)} onImport={submitRows} /> : <div className="space-y-5">{rowFields.fields.map((field, index) => <fieldset className="grid gap-3 rounded-md border p-4 md:grid-cols-3" key={field.id}><legend className="px-1 text-sm font-semibold">Employee row {index + 1}</legend><label className="space-y-1 text-sm">Employee code<Input {...importer.register(`rows.${index}.employeeCode`)} /></label><label className="space-y-1 text-sm">Identity reference<Input {...importer.register(`rows.${index}.identityReference`)} /></label><label className="space-y-1 text-sm">Employment status<select className="flex h-10 w-full rounded-md border bg-background px-3" {...importer.register(`rows.${index}.employmentStatus`)}>{employeeStatuses.map((status) => <option key={status} value={status}>{employeeStatusLabel(status)}</option>)}</select></label><label className="space-y-1 text-sm">Salary amount<Input type="number" min="0" step="0.01" {...importer.register(`rows.${index}.salaryAmount`, { valueAsNumber: true })} /></label><label className="space-y-1 text-sm">Salary Advance limit<Input type="number" min="0" step="0.01" {...importer.register(`rows.${index}.salaryAdvanceLimit`, { valueAsNumber: true })} /></label><label className="flex items-center gap-2 self-end text-sm"><input type="checkbox" {...importer.register(`rows.${index}.active`)} /> Active employee record</label><div className="md:col-span-3"><Button type="button" variant="outline" disabled={rowFields.fields.length === 1} onClick={() => rowFields.remove(index)}>Remove row</Button></div></fieldset>)}<div className="flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => rowFields.append(emptyRow())}>Add employee row</Button><Button type="submit">{busy ? 'Importing…' : 'Import employees'}</Button></div></div>}</fieldset></form>
      {importResult ? <Alert variant={importResult.status === 'COMPLETED' ? 'success' : 'destructive'} className="mt-5"><AlertTitle>{importResult.status === 'COMPLETED' ? 'Employee roster replaced' : 'Employee roster was not replaced'}</AlertTitle><AlertDescription>{importResult.validRowCount} valid row(s), {importResult.invalidRowCount} invalid row(s).{importResult.status !== 'COMPLETED' ? ' No employee rows were imported. Correct the rejected rows and submit the complete roster again.' : ''}{importResult.rejections.length ? <ul className="mt-2 list-disc pl-5">{importResult.rejections.slice(0, 25).map((rejection) => <li key={`${rejection.rowIndex}-${rejection.errorCode}`}>Row {rejection.rowIndex}: {importRejectionLabel(rejection.errorCode)}</li>)}</ul> : null}{importResult.rejections.length > 25 ? <p>Showing the first 25 of {importResult.rejections.length} rejected rows.</p> : null}</AlertDescription></Alert> : null}
    </CardContent></Card> : null}
    {statusConfirmation ? <ConfirmationDialog onDismiss={() => { const target = statusConfirmation; setStatusConfirmation(undefined); setTimeout(() => document.getElementById(`partner-status-${target.toLowerCase()}-trigger`)?.focus(), 0) }}><ConfirmationDialogTitle className="text-xl font-semibold">Confirm Partner Company status</ConfirmationDialogTitle><dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-muted-foreground">Partner Company</dt><dd className="font-semibold">{company.data.name} ({company.data.companyCode})</dd></div><div><dt className="text-muted-foreground">Status change</dt><dd className="font-semibold">{companyStatusLabel(company.data.status)} → {companyStatusLabel(statusConfirmation)}</dd></div></dl><ConfirmationDialogDescription className="text-sm text-muted-foreground">This status reduces future Salary Advance eligibility. Historical lending records remain unchanged.</ConfirmationDialogDescription><ConfirmationDialogFooter><ConfirmationDialogCancel asChild><Button variant="outline">Cancel</Button></ConfirmationDialogCancel><Button autoFocus variant="destructive" disabled={busy} onClick={() => { const target = statusConfirmation; setStatusConfirmation(undefined); void setStatus(target) }}>Set {statusConfirmation.toLowerCase()}</Button></ConfirmationDialogFooter></ConfirmationDialog> : null}
  </section>
}
