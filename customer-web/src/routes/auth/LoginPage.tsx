import { useState } from 'react'
import { useForm, type FieldErrors } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { CustomerSessionRequiredError } from '@/features/auth/auth-session'
import { focusServerError, isAuthApiError, unexpectedAuthError } from '@/features/auth/auth-errors'
import { AuthCard } from '@/features/auth/components/AuthCard'
import {
  ErrorFeedback,
  RateLimitFeedback,
  SuccessFeedback,
  ValidationSummary,
} from '@/features/auth/components/AuthFeedback'
import { FormField } from '@/features/auth/components/FormField'
import { fieldDescriptionIds } from '@/features/auth/field-description'
import { useRateLimitRecovery } from '@/features/auth/use-rate-limit'
import { emailSchema, requiredPasswordSchema, validateWith } from '@/features/auth/validation'
import { ApiError } from '@/lib/api'

interface LoginFormValues {
  email: string
  password: string
}

interface LoginLocationState {
  from?: string
  notice?: 'PASSWORD_RESET_SUCCESS'
}

function intendedCustomerPath(value: unknown) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/'
  }

  return ['/', '/products', '/applications', '/loans', '/account'].some(
    (path) => value === path || (path !== '/' && value.startsWith(`${path}/`)),
  )
    ? value
    : '/'
}

export function LoginPage() {
  const { manager } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const locationState = (location.state ?? {}) as LoginLocationState
  const rateLimit = useRateLimitRecovery()
  const [serverError, setServerError] = useState<ReturnType<typeof unexpectedAuthError>>()
  const {
    register,
    handleSubmit,
    resetField,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ defaultValues: { email: '', password: '' } })

  const onInvalid = (fieldErrors: FieldErrors<LoginFormValues>) => {
    setFocus(fieldErrors.email ? 'email' : 'password')
  }

  const onSubmit = handleSubmit(async (values) => {
    setServerError(undefined)
    try {
      await manager.login({ email: values.email.trim(), password: values.password })
      navigate(intendedCustomerPath(locationState.from), { replace: true })
    } catch (error) {
      resetField('password')
      if (isAuthApiError(error, 401, 'EMAIL_VERIFICATION_REQUIRED')) {
        navigate('/verify-email/pending', {
          replace: true,
          state: { email: values.email.trim() },
        })
        return
      }
      if (isAuthApiError(error, 401, 'INVALID_CREDENTIALS')) {
        setServerError({
          title: 'Login was not successful',
          description: 'The email or password could not be verified.',
          requestId: undefined,
        })
      } else if (
        error instanceof ApiError &&
        error.status === 429 &&
        error.errorCode === 'RATE_LIMIT_EXCEEDED'
      ) {
        rateLimit.start(error.retryAfter, error.requestId)
      } else if (error instanceof CustomerSessionRequiredError) {
        setServerError({
          title: 'Customer account required',
          description: 'Use a Customer account to continue in Meridian Customer Web.',
          requestId: undefined,
        })
      } else {
        setServerError(unexpectedAuthError(error))
      }
      focusServerError()
    }
  }, onInvalid)

  const validationMessages = Object.values(errors)
    .map((error) => error?.message)
    .filter((message): message is string => typeof message === 'string')

  return (
    <AuthCard
      eyebrow="Customer access"
      title="Welcome back"
      description="Log in to continue securely to Meridian Customer Web."
      footer={
        <p className="text-center text-sm text-muted-foreground">
          New to Meridian?{' '}
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" to="/register">
            Create an account
          </Link>
        </p>
      }
    >
      <form className="space-y-5" noValidate onSubmit={onSubmit}>
        {locationState.notice === 'PASSWORD_RESET_SUCCESS' ? (
          <SuccessFeedback
            title="Password updated"
            description="Log in with your new password to continue."
          />
        ) : null}
        <ValidationSummary messages={validationMessages} />
        {serverError ? <ErrorFeedback {...serverError} /> : null}
        {rateLimit.isLimited ? (
          <RateLimitFeedback remainingSeconds={rateLimit.remainingSeconds} requestId={rateLimit.requestId} />
        ) : null}

        <FormField htmlFor="email" label="Email" error={errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            aria-invalid={Boolean(errors.email)}
            aria-describedby={fieldDescriptionIds('email', false, Boolean(errors.email))}
            {...register('email', { validate: validateWith(emailSchema) })}
          />
        </FormField>

        <FormField htmlFor="password" label="Password" error={errors.password?.message}>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={Boolean(errors.password)}
            aria-describedby={fieldDescriptionIds('password', false, Boolean(errors.password))}
            {...register('password', { validate: validateWith(requiredPasswordSchema) })}
          />
        </FormField>

        <div className="flex items-center justify-end">
          <Link className="text-sm font-semibold text-primary underline-offset-4 hover:underline" to="/forgot-password">
            Forgot password?
          </Link>
        </div>

        <Button className="w-full" type="submit" disabled={isSubmitting || rateLimit.isActive}>
          {isSubmitting ? <Spinner /> : null}
          {isSubmitting ? 'Logging in…' : rateLimit.isActive ? 'Try again shortly' : 'Log in'}
        </Button>
      </form>
    </AuthCard>
  )
}
