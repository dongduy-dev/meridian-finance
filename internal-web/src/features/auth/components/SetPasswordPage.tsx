import { useLayoutEffect, useState, useSyncExternalStore, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError, apiRequest } from '@/lib/api'
import { AuthCard } from './AuthCard'
import { StaffSetupAttempt } from '../model/fragment-token'

function SetPasswordContent() {
  const navigate = useNavigate()
  const [attempt] = useState(() => new StaffSetupAttempt())
  const hasToken = useSyncExternalStore(attempt.subscribe, attempt.hasToken)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState(false)

  useLayoutEffect(() => attempt.mount(), [attempt])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasToken) return
    setError(undefined)
    if (password.length < 12 || password.length > 72) { setError('Use 12 to 72 characters.'); return }
    if (password !== confirmation) { setError('Passwords must match.'); return }
    const token = attempt.takeToken()
    if (!token) return
    setPending(true)
    try {
      await apiRequest<void>('/auth/password-reset/confirm', {
        method: 'POST', body: { token, newPassword: password },
      })
      setPassword('')
      setConfirmation('')
      if (attempt.isMounted()) navigate('/login', { replace: true })
    } catch (failure) {
      if (!attempt.isMounted()) return
      setPassword('')
      setConfirmation('')
      if (failure instanceof ApiError && failure.errorCode === 'INVALID_PASSWORD_RESET_TOKEN') {
        setError('This setup link is invalid, expired, or already used. Ask an administrator to send a new link.')
      } else {
        setError('The password result could not be confirmed. Try signing in with the password you chose, or ask an administrator for a new link. This page will not resend your password.')
      }
    } finally { setPending(false) }
  }

  return <AuthCard title="Set your Staff password" description="Choose the password for your Meridian internal account.">
    <form onSubmit={(event) => void submit(event)} className="space-y-5" noValidate>
      {!hasToken && !error && !pending ? <Alert variant="destructive">This setup link is incomplete. Ask an administrator to send a new link.</Alert> : null}
      {error ? <Alert variant="destructive">{error}</Alert> : null}
      {pending ? <p role="status">Setting password…</p> : null}
      {hasToken ? <>
        <label className="block space-y-2 text-sm font-medium">New password
          <Input type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        <label className="block space-y-2 text-sm font-medium">Confirm new password
          <Input type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} />
        </label>
        <p className="text-xs text-muted-foreground">Use 12 to 72 characters.</p>
        <Button type="submit" disabled={pending}>{pending ? 'Setting password…' : 'Set password'}</Button>
      </> : null}
      <p className="text-sm"><Link to="/login" className="underline">Back to Staff sign in</Link></p>
    </form>
  </AuthCard>
}

export function SetPasswordPage() {
  const location = useLocation()
  return <SetPasswordContent key={location.key} />
}
