import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { ApiError } from '@/lib/api'
import { formatVnd } from '@/lib/format/presentation'
import { createPartnerCompanyInputSchema, type CreatePartnerCompanyInput } from '../api/contracts'
import { createPartnerCompany } from '../api/partner-admin-api'
import { partnerAdminKeys, partnerCompaniesQuery } from '../api/queries'
import { PartnerQueryErrorPanel } from '../components/PartnerQueryErrorPanel'

const knownStatuses = new Set(['ACTIVE', 'INACTIVE', 'SUSPENDED'])
const statusLabel = (value: string) => knownStatuses.has(value) ? value.replaceAll('_', ' ') : 'Unknown status'

export function PartnerCompanyListPage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const canManage = state.status === 'authenticated' && hasPermission(state.actor, 'partner:manage')
  const companies = useQuery(partnerCompaniesQuery(manager, state.status === 'authenticated'))
  const [commandError, setCommandError] = useState<Error>()
  const [submitting, setSubmitting] = useState(false)
  const form = useForm<CreatePartnerCompanyInput>({
    defaultValues: { companyCode: '', name: '', status: 'ACTIVE', salaryAdvancePolicyLimit: 0 },
  })

  const submit = form.handleSubmit(async (raw) => {
    const parsed = createPartnerCompanyInputSchema.safeParse(raw)
    if (!parsed.success) {
      setCommandError(new Error('Review the company fields and try again.'))
      return
    }
    setSubmitting(true)
    setCommandError(undefined)
    try {
      await createPartnerCompany(manager, parsed.data)
      form.reset()
      await queryClient.invalidateQueries({ queryKey: partnerAdminKeys.companies() })
    } catch (error) {
      setCommandError(error instanceof Error ? error : new Error('Company creation failed.'))
    } finally {
      setSubmitting(false)
    }
  })

  return <section className="mx-auto max-w-6xl space-y-6">
    <div><p className="text-sm font-semibold text-muted-foreground">PARTNER ADMINISTRATION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Partner Companies</h1><p className="mt-2 text-muted-foreground">Discover configured employers and open one company’s controlled administration workspace.</p></div>
    {canManage ? <Card><CardHeader><CardTitle>Create Partner Company</CardTitle></CardHeader><CardContent>
      <form className="grid gap-4 md:grid-cols-2" onSubmit={submit}>
        <label className="space-y-1 text-sm font-medium">Company code<Input {...form.register('companyCode')} maxLength={50} /></label>
        <label className="space-y-1 text-sm font-medium">Company name<Input {...form.register('name')} maxLength={200} /></label>
        <label className="space-y-1 text-sm font-medium">Initial status<select className="flex h-10 w-full rounded-md border bg-background px-3" {...form.register('status')}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="SUSPENDED">Suspended</option></select></label>
        <label className="space-y-1 text-sm font-medium">Salary Advance policy limit<Input type="number" min="0" step="0.01" {...form.register('salaryAdvancePolicyLimit', { valueAsNumber: true })} /></label>
        <div className="md:col-span-2"><Button type="submit" disabled={submitting}>{submitting ? 'Creating…' : 'Create company'}</Button></div>
      </form>
      {commandError ? <Alert variant="destructive" className="mt-4"><AlertTitle>Company was not created</AlertTitle><AlertDescription>{commandError instanceof ApiError ? commandError.message : commandError.message}</AlertDescription></Alert> : null}
    </CardContent></Card> : null}
    <Card><CardHeader><CardTitle>Configured companies</CardTitle></CardHeader><CardContent>
      {companies.isPending ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading Partner Companies…</div> : null}
      {companies.isError ? <PartnerQueryErrorPanel error={companies.error} onRetry={() => void companies.refetch()} /> : null}
      {companies.data?.length === 0 ? <p className="text-sm text-muted-foreground">No Partner Companies are configured.</p> : null}
      {companies.data?.length ? <div className="overflow-x-auto"><table className="w-full min-w-[42rem] text-left text-sm"><caption className="sr-only">Partner Companies</caption><thead><tr className="border-b"><th className="p-3">Code</th><th className="p-3">Name</th><th className="p-3">Status</th><th className="p-3">Policy limit</th><th className="p-3">Workspace</th></tr></thead><tbody>{companies.data.map((company) => <tr className="border-b" key={company.id}><td className="p-3 font-mono">{company.companyCode}</td><td className="p-3 font-medium">{company.name}</td><td className="p-3">{statusLabel(company.status)}</td><td className="p-3">{formatVnd(company.salaryAdvancePolicyLimit)}</td><td className="p-3"><Button asChild variant="outline"><Link to={`/admin/partners/${company.id}`}>Open</Link></Button></td></tr>)}</tbody></table></div> : null}
    </CardContent></Card>
  </section>
}
