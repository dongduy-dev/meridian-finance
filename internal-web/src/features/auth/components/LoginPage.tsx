import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { ApiError } from '@/lib/api'
import { AuthCard } from './AuthCard'
import { useAuth } from '../model/auth-context'
import { InternalAccessRequiredError } from '../model/auth-session'
import { useRateLimitRecovery } from '../model/use-rate-limit'
import { loginInputSchema, type LoginInput } from '../model/login-input'

export function LoginPage() {
  const { manager, state } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [feedback, setFeedback] = useState<string>()
  const [requestId, setRequestId] = useState<string>()
  const rateLimit = useRateLimitRecovery()
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<LoginInput>()

  const displayedFeedback = feedback ?? (state.status === 'anonymous' && state.reason === 'INTERNAL_ACCESS_REQUIRED'
    ? 'This workspace is available only to Meridian staff accounts.'
    : undefined)

  const submit = handleSubmit(async (fields) => {
    const validated = loginInputSchema.safeParse(fields)
    if (!validated.success) {
      for (const issue of validated.error.issues) {
        const field = issue.path[0]
        if (field === 'email' || field === 'password') setError(field, { message: issue.message }, { shouldFocus: true })
      }
      return
    }
    const { email, password } = validated.data
    setFeedback(undefined)
    setRequestId(undefined)
    try {
      await manager.login(email, password)
      const requested = (location.state as { from?: string } | null)?.from
      navigate(requested?.startsWith('/staff') ? requested : '/staff', { replace: true })
    } catch (error) {
      if (error instanceof InternalAccessRequiredError) setFeedback(error.message)
      else if (error instanceof ApiError && error.status === 429) {
        rateLimit.start(error.retryAfter)
        setRequestId(error.requestId)
        setFeedback('Too many sign-in attempts. Wait a moment and try again.')
      }
      else if (error instanceof ApiError && error.errorCode === 'INVALID_CREDENTIALS') setFeedback('Email or password is incorrect.')
      else {
        if (error instanceof ApiError) setRequestId(error.requestId)
        setFeedback('Sign-in could not be completed. Try again.')
      }
    }
  })

  return (
    <AuthCard>
      <form className="space-y-5" onSubmit={submit} noValidate>
        {displayedFeedback && <Alert variant="destructive"><div><p>{displayedFeedback}</p>{rateLimit.isActive ? <p className="mt-1">Try again in {rateLimit.remainingSeconds} second{rateLimit.remainingSeconds === 1 ? '' : 's'}.</p> : null}{requestId ? <RequestCorrelation requestId={requestId} /> : null}</div></Alert>}
        <div className="space-y-2">
          <label className="text-sm font-medium" htmlFor="email">Email</label>
          <Input id="email" type="email" autoComplete="username" aria-invalid={Boolean(errors.email)} {...register('email')} />
          {errors.email && <p className="text-sm text-danger">{errors.email.message}</p>}
        </div>
        <div className="space-y-2">
          <label className="text-sm font-medium" htmlFor="password">Password</label>
          <Input id="password" type="password" autoComplete="current-password" aria-invalid={Boolean(errors.password)} {...register('password')} />
          {errors.password && <p className="text-sm text-danger">{errors.password.message}</p>}
        </div>
        <Button className="w-full" size="lg" disabled={isSubmitting || rateLimit.isActive} type="submit">
          {isSubmitting ? 'Signing in…' : rateLimit.isActive ? `Retry in ${rateLimit.remainingSeconds}s` : 'Sign in'}
        </Button>
        <p className="text-center text-xs text-muted-foreground">Authorized internal use only</p>
      </form>
    </AuthCard>
  )
}
