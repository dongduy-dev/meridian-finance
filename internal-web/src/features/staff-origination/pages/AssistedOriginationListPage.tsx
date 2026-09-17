import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { NetworkError } from '@/lib/api'
import { createIntake, listOpenIntakes } from '../api/staff-origination-api'
import { intakeListQuery, originationKeys } from '../api/queries'

export function AssistedOriginationListPage() {
  const { manager, state } = useAuth(); const navigate = useNavigate(); const client = useQueryClient()
  const enabled = state.status === 'authenticated' && hasPermission(state.actor, 'loan:originate:staff')
  const cases = useQuery(intakeListQuery(manager, enabled))
  const [createStatus, setCreateStatus] = useState<OperationStatus>('DRAFT')
  const start = async (productCode: string) => {
    setCreateStatus('IN_FLIGHT')
    try {
      const value = await createIntake(manager, productCode)
      try { await client.invalidateQueries({ queryKey: originationKeys.list() }) } catch { /* The returned case remains authoritative. */ }
      setCreateStatus('RESOLVED')
      navigate(`/staff/origination/${value.assistedOriginationCaseId}`)
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setCreateStatus('BLOCKED')
        return
      }
      setCreateStatus('RECONCILING')
      try {
        client.setQueryData(originationKeys.list(), await listOpenIntakes(manager))
      } catch { /* The result remains unresolved. */ }
      setCreateStatus('RESULT_UNKNOWN')
    }
  }
  const createLocked = createStatus === 'IN_FLIGHT' || createStatus === 'RECONCILING' || createStatus === 'RESULT_UNKNOWN'
  return <section className="mx-auto max-w-6xl space-y-6">
    <div><p className="text-sm font-semibold text-muted-foreground">STAFF-ASSISTED ORIGINATION</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">Paper intake</h1><p className="mt-2 text-muted-foreground">Open and continue UCL or Collateral Loan intake before a LoanApplication exists.</p></div>
    <div className="flex flex-wrap gap-3 rounded-lg border bg-card p-4">
      <Button disabled={createLocked} onClick={() => void start('UNSECURED_CONSUMER_LOAN')}>Start UCL intake</Button>
      <Button disabled={createLocked} variant="outline" onClick={() => void start('COLLATERAL_LOAN')}>Start Collateral intake</Button>
      {createStatus !== 'DRAFT' ? <div className="w-full space-y-2"><OperationStatusPanel status={createStatus} />{createStatus === 'RESULT_UNKNOWN' ? <p role="alert" className="text-sm text-muted-foreground">The create result is unresolved. Open intake was refreshed, and no second create command was sent.</p> : null}</div> : null}
    </div>
    {cases.isPending ? <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading open intake…</p> : null}
    {cases.isError ? <div className="rounded-lg border border-danger/30 p-4"><p role="alert">Open intake could not be loaded.</p><Button className="mt-3" variant="outline" onClick={() => void cases.refetch()}>Retry</Button></div> : null}
    {cases.data?.length === 0 ? <p className="rounded-lg border bg-card p-6 text-sm text-muted-foreground">No assisted origination cases are open.</p> : null}
    {cases.data?.length ? <div className="grid gap-3">{cases.data.map((item) => <article key={item.assistedOriginationCaseId} className="flex flex-col gap-3 rounded-lg border bg-card p-4 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="font-semibold">{item.productCode === 'UNSECURED_CONSUMER_LOAN' ? 'Unsecured Consumer Loan' : 'Collateral Loan'}</h2><p className="text-sm text-muted-foreground">{item.customerId ? 'Customer selected' : 'Customer not selected'} · Updated {new Date(item.updatedAt).toLocaleString()}</p></div><Button asChild variant="outline"><Link to={`/staff/origination/${item.assistedOriginationCaseId}`}>Open intake</Link></Button></article>)}</div> : null}
  </section>
}
