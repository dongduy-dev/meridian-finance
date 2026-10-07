import { operatorErrorMessage } from '@/lib/api/operator-error-message'
import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import { createInternalUser } from '../api/admin-users-api'
import type { AssignableInternalRole } from '../api/contracts'
import { adminUserKeys } from '../api/queries'

export function CreateInternalUserForm({ manager, roles, onClose }: {
  manager: AuthSessionManager
  roles: readonly AssignableInternalRole[]
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [roleCodes, setRoleCodes] = useState<string[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(undefined)
    if (!roleCodes.length) { setError('Choose at least one predefined Staff role.'); return }
    setPending(true)
    try {
      await createInternalUser(manager, { email: email.trim().toLowerCase(), displayName: displayName.trim(), roleCodes })
      await queryClient.invalidateQueries({ queryKey: adminUserKeys.list() })
      onClose()
    } catch (failure) {
      if (failure instanceof ApiError && failure.errorCode === 'EMAIL_ALREADY_REGISTERED') {
        setError('An account with this email already exists.')
      } else if (failure instanceof ApiError && failure.errorCode === 'INTERNAL_ROLE_NOT_FOUND') {
        setError('A selected role is no longer assignable. Refresh the role list before retrying.')
      } else if (failure instanceof ApiError) {
        setError(operatorErrorMessage(failure, 'Creation could not be confirmed. Refresh the Internal User list before trying again.'))
      } else if (failure instanceof NetworkError) {
        setError('Creation could not be confirmed. Refresh the Internal User list before trying again.')
        await queryClient.invalidateQueries({ queryKey: adminUserKeys.list() }).catch(() => undefined)
      } else {
        setError('Creation could not be confirmed. Refresh the Internal User list before trying again.')
      }
    } finally { setPending(false) }
  }

  return <form onSubmit={(event) => void submit(event)} className="space-y-4 rounded-md border bg-card p-5">
    <h2 className="text-lg font-semibold">Create Internal User</h2>
    <p className="text-sm text-muted-foreground">The Staff member receives a secure link to set their password.</p>
    <label className="block space-y-1 text-sm font-medium">Email
      <Input type="email" maxLength={255} required value={email} onChange={(event) => setEmail(event.target.value)} />
    </label>
    <label className="block space-y-1 text-sm font-medium">Display name
      <Input maxLength={150} required value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
    </label>
    <fieldset className="space-y-2"><legend className="text-sm font-medium">Predefined Staff roles</legend>
      {roles.map((role) => <label key={role.code} className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={roleCodes.includes(role.code)} onChange={(event) => setRoleCodes((current) =>
          event.target.checked ? [...current, role.code] : current.filter((code) => code !== role.code))} />
        {role.name}
      </label>)}
    </fieldset>
    {error ? <Alert variant="destructive">{error}</Alert> : null}
    <div className="flex gap-2"><Button type="submit" disabled={pending}>{pending ? 'Creating…' : 'Create Internal User'}</Button>
      <Button type="button" variant="outline" disabled={pending} onClick={onClose}>Cancel</Button></div>
  </form>
}
