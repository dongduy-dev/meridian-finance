import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError, apiRequest } from '@/lib/api'
import { AuthCard } from './AuthCard'
import { captureSetupToken } from '../model/fragment-token'

function SetPasswordContent({ locationKey }: { locationKey: string }) {
  const navigate = useNavigate()
  const [token, setToken] = useState(() => captureSetupToken(locationKey))
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState(false)

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!token) return
    setError(undefined)
    if (password.length < 12 || password.length > 72) { setError('Use 12 to 72 characters.'); return }
    if (password !== confirmation) { setError('Passwords must match.'); return }
    setPending(true)
    try {
      await apiRequest<void>('/auth/password-reset/confirm', {
        method: 'POST', body: { token, newPassword: password },
      })
      setToken(undefined)
      setPassword('')
      setConfirmation('')
      navigate('/login', { replace: true })
    } catch (failure) {
      setPassword('')
      setConfirmation('')
      if (failure instanceof ApiError && failure.errorCode === 'INVALID_PASSWORD_RESET_TOKEN') {
        setToken(undefined)
        setError('This setup link is invalid, expired, or already used. Ask an administrator to send a new link.')
      } else {
        setError('Password setup could not be confirmed. Review the link before trying again.')
      }
    } finally { setPending(false) }
  }

  return <AuthCard title="Set your Staff password" description="Choose the password for your Meridian internal account.">
    <form onSubmit={(event) => void submit(event)} className="space-y-5" noValidate>
      {!token && !error ? <Alert variant="destructive">This setup link is incomplete. Ask an administrator to send a new link.</Alert> : null}
      {error ? <Alert variant="destructive">{error}</Alert> : null}
      {token ? <>
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
  const locationKey = `${location.key}:${location.hash}`
  return <SetPasswordContent key={locationKey} locationKey={locationKey} />
}
