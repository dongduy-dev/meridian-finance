import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/model/auth-context'
import { correctIdentityReference } from '../api/staff-origination-api'
import { originationKeys } from '../api/queries'
import type { AssistedOrigination, StaffCustomer } from '../api/contracts'

export function IntakeIdentityReferenceCorrection({ customerId, caseId }: { customerId: string; caseId: string }) {
  const { manager } = useAuth()
  const client = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const generation = useRef(0)
  useEffect(() => () => { generation.current++ }, [])
  const [uncertain, setUncertain] = useState(false)
  const [message, setMessage] = useState<string>()
  const correct = async (form: HTMLFormElement) => {
    const customer = client.getQueryState<StaffCustomer>(originationKeys.customer(customerId))
    const intake = client.getQueryState<AssistedOrigination>(originationKeys.case(caseId))
    if (pending.current || uncertain || customer?.status !== 'success' || customer.fetchStatus !== 'idle' || customer.isInvalidated
      || intake?.status !== 'success' || intake.fetchStatus !== 'idle' || intake.isInvalidated
      || intake.data?.status !== 'OPEN' || intake.data.customerId !== customerId
      || customer.data?.status !== 'ACTIVE' || customer.data.profileCompletionStatus !== 'COMPLETE'
      || !['UNVERIFIED', 'REJECTED'].includes(customer.data.verificationStatus)) return
    const activeGeneration = generation.current
    const reference = String(new FormData(form).get('replacementIdentityReference') ?? '').trim()
    if (!reference || reference.length > 100) return
    pending.current = true; setBusy(true); setMessage(undefined); form.reset()
    try {
      const result = await correctIdentityReference(manager, customerId, reference)
      if (generation.current !== activeGeneration) return
      client.setQueryData(originationKeys.customer(customerId), result)
      await client.invalidateQueries({ queryKey: ['customer-identity'] })
      setEditing(false); setMessage('Identity reference corrected. Manual identity verification is still required.')
    } catch {
      if (generation.current !== activeGeneration) return
      setUncertain(true)
      setMessage('Correction was not confirmed. Refresh the selected Customer before entering another correction.')
    } finally { pending.current = false; setBusy(false) }
  }
  return <article className="space-y-3 rounded-lg border bg-card p-5">
    <h2 className="text-lg font-semibold">Correct Customer identity reference</h2>
    <p className="text-sm">Identity reference: On file. Record only the reference supplied by the Customer. Existing evidence and history remain available for review.</p>
    {message ? <p role="status">{message}</p> : null}
    {!editing ? <Button variant="outline" onClick={() => setEditing(true)}>Correct identity reference</Button> : <form className="space-y-3" onSubmit={event => { event.preventDefault(); void correct(event.currentTarget) }}>
      <label className="grid gap-2 text-sm font-medium">Replacement identity reference<Input name="replacementIdentityReference" required autoComplete="off" maxLength={100} disabled={busy || uncertain} /></label>
      <div className="flex gap-2"><Button disabled={busy || uncertain}>{busy ? 'Correcting…' : 'Confirm correction'}</Button><Button type="button" variant="outline" disabled={busy} onClick={() => setEditing(false)}>Cancel</Button></div>
    </form>}
    {uncertain ? <Button variant="outline" disabled={busy} onClick={() => void client.refetchQueries({ queryKey: originationKeys.customer(customerId) }).then(() => {
      const state = client.getQueryState(originationKeys.customer(customerId))
      if (state?.status === 'success' && state.fetchStatus === 'idle' && !state.isInvalidated) setUncertain(false)
    })}>Refresh selected Customer</Button> : null}
  </article>
}
