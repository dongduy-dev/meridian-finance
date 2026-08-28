import { useState } from 'react'
import { useForm, type FieldErrors } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { focusServerError, isAuthApiError, unexpectedAuthError } from '@/features/auth/auth-errors'
import { AuthCard } from '@/features/auth/components/AuthCard'
import { ErrorFeedback, RateLimitFeedback, ValidationSummary } from '@/features/auth/components/AuthFeedback'
import { FormField } from '@/features/auth/components/FormField'
import { fieldDescriptionIds } from '@/features/auth/field-description'
import { useRateLimitRecovery } from '@/features/auth/use-rate-limit'
import { displayNameSchema, emailSchema, newPasswordSchema, validateWith } from '@/features/auth/validation'
import { ApiError } from '@/lib/api'

interface RegistrationFormValues {
  displayName: string
  email: string
  password: string
}

export function RegisterPage() {
  const { manager } = useAuth()
  const navigate = useNavigate()
  const rateLimit = useRateLimitRecovery()
  const [serverError, setServerError] = useState<ReturnType<typeof unexpectedAuthError>>()
  const {
    register,
    handleSubmit,
    resetField,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<RegistrationFormValues>({
    defaultValues: { displayName: '', email: '', password: '' },
  })

  const onInvalid = (fieldErrors: FieldErrors<RegistrationFormValues>) => {
    setFocus(fieldErrors.displayName ? 'displayName' : fieldErrors.email ? 'email' : 'password')
  }

  const onSubmit = handleSubmit(async (values) => {
    setServerError(undefined)
    try {
      await manager.register({
        displayName: values.displayName.trim(),
        email: values.email.trim(),
        password: values.password,
      })
      navigate('/verify-email/pending', {
        state: { email: values.email.trim() },
      })
    } catch (error) {
      resetField('password')
      if (isAuthApiError(error, 409, 'EMAIL_ALREADY_REGISTERED')) {
        setServerError({
          title: 'An account already uses this email',
          description: 'Log in or use password recovery to continue safely.',
          requestId: error instanceof ApiError ? error.requestId : undefined,
        })
      } else if (
        error instanceof ApiError &&
        error.status === 429 &&
        error.errorCode === 'RATE_LIMIT_EXCEEDED'
      ) {
        rateLimit.start(error.retryAfter, error.requestId)
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
      eyebrow="Create an account"
      title="Start with Meridian"
      description="Create your Customer account, then confirm your email before logging in."
      footer={
        <div className="flex flex-wrap justify-center gap-x-5 gap-y-2 text-sm">
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" to="/login">Log in</Link>
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" to="/forgot-password">Forgot password?</Link>
        </div>
      }
    >
      <form className="space-y-5" noValidate onSubmit={onSubmit}>
        <ValidationSummary messages={validationMessages} />
        {serverError ? <ErrorFeedback {...serverError} /> : null}
        {rateLimit.isLimited ? <RateLimitFeedback remainingSeconds={rateLimit.remainingSeconds} requestId={rateLimit.requestId} /> : null}

        <FormField htmlFor="displayName" label="Display name" error={errors.displayName?.message}>
          <Input
            id="displayName"
            autoComplete="name"
            aria-invalid={Boolean(errors.displayName)}
            aria-describedby={fieldDescriptionIds('displayName', false, Boolean(errors.displayName))}
            {...register('displayName', { validate: validateWith(displayNameSchema) })}
          />
        </FormField>
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
        <FormField
          htmlFor="password"
          label="Password"
          description="Use 12 to 72 characters."
          error={errors.password?.message}
        >
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            aria-invalid={Boolean(errors.password)}
            aria-describedby={fieldDescriptionIds('password', true, Boolean(errors.password))}
            {...register('password', { validate: validateWith(newPasswordSchema) })}
          />
        </FormField>

        <p className="text-xs leading-5 text-muted-foreground">
          By creating an account, you agree to provide accurate information for your Meridian Customer access.
        </p>

        <Button className="w-full" type="submit" disabled={isSubmitting || rateLimit.isActive}>
          {isSubmitting ? <Spinner /> : null}
          {isSubmitting ? 'Creating account…' : rateLimit.isActive ? 'Try again shortly' : 'Create account'}
        </Button>
      </form>
    </AuthCard>
  )
}
