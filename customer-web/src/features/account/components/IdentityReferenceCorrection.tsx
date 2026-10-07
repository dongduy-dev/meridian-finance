import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { createAccountApi, type Customer } from '@/features/account/account-api'
import { accountKeys } from '@/features/account/account-queries'
import { useAuth } from '@/features/auth/auth-context'
import { AccountErrorFeedback } from './AccountFeedback'

export function IdentityReferenceCorrection() {
  const { manager } = useAuth()
  const client = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const generation = useRef(0)
  useEffect(() => () => { generation.current++ }, [])
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const [uncertain, setUncertain] = useState(false)
  const correct = async (form: HTMLFormElement) => {
    const state = client.getQueryState<Customer>(accountKeys.customer())
    if (pending.current || uncertain || state?.status !== 'success' || state.fetchStatus !== 'idle' || state.isInvalidated
      || state.data?.status !== 'ACTIVE' || state.data.profileCompletionStatus !== 'COMPLETE'
      || !['UNVERIFIED', 'REJECTED'].includes(state.data.verificationStatus)) return
    const activeGeneration = generation.current
    const reference = String(new FormData(form).get('replacementIdentityReference') ?? '').trim()
    if (!reference || reference.length > 100) return
    pending.current = true; setBusy(true); setError(undefined); setMessage(undefined)
    form.reset()
    try {
      const customer = await createAccountApi(manager).correctOwnIdentityReference(reference)
      if (generation.current !== activeGeneration) return
      client.setQueryData(accountKeys.customer(), customer)
      await Promise.all([
        client.invalidateQueries({ queryKey: ['account', 'identity-verifications'] }),
        client.invalidateQueries({ queryKey: ['salary-advance'] }),
      ])
      setEditing(false)
      setMessage('Identity reference corrected. Identity verification is still required.')
    } catch (failure) {
      if (generation.current !== activeGeneration) return
      setError(failure); setUncertain(true)
      setMessage('Correction was not confirmed. Refresh your profile before entering another correction.')
      await client.invalidateQueries({ queryKey: accountKeys.customer() })
    } finally { pending.current = false; setBusy(false) }
  }
  return <div className="min-w-0 max-w-[var(--width-flow)] space-y-6 border-t border-border pt-6 [overflow-wrap:anywhere]">
    {message ? <p role="status">{message}</p> : null}
    {error ? <AccountErrorFeedback error={error} title="Identity reference correction was not confirmed" /> : null}
    {!editing ? <Button variant="secondary" onClick={() => setEditing(true)}>Correct identity reference</Button> : <form className="space-y-6" onSubmit={event => { event.preventDefault(); void correct(event.currentTarget) }}>
      <p className="text-sm text-muted-foreground">Enter the reference shown on your identity document. Your previous documents and verification history will be kept for review.</p>
      <label className="grid min-w-0 gap-2 text-sm font-semibold">Replacement identity reference<Input name="replacementIdentityReference" autoComplete="off" required maxLength={100} disabled={busy || uncertain} /></label>
      <div className="flex flex-wrap gap-3"><Button size="lg" type="submit" disabled={busy || uncertain}>{busy ? 'Correcting…' : 'Confirm correction'}</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => { setEditing(false); setError(undefined) }}>Cancel</Button></div>
    </form>}
    {uncertain ? <Button variant="secondary" disabled={busy} onClick={() => void client.refetchQueries({ queryKey: accountKeys.customer() }).then(() => {
      const state = client.getQueryState<Customer>(accountKeys.customer())
      if (state?.status === 'success' && state.fetchStatus === 'idle' && !state.isInvalidated) { setUncertain(false); setError(undefined) }
    })}>Refresh profile</Button> : null}
  </div>
}
