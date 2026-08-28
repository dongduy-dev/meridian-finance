import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { focusServerError, unexpectedAuthError } from '@/features/auth/auth-errors'
import { AuthCard } from '@/features/auth/components/AuthCard'
import { ErrorFeedback, RateLimitFeedback, SuccessFeedback } from '@/features/auth/components/AuthFeedback'
import { FormField } from '@/features/auth/components/FormField'
import { fieldDescriptionIds } from '@/features/auth/field-description'
import { useRateLimitRecovery } from '@/features/auth/use-rate-limit'
import { emailSchema, validateWith } from '@/features/auth/validation'
import { ApiError } from '@/lib/api'

interface ForgotPasswordValues {
  email: string
}

export function ForgotPasswordPage() {
  const { manager } = useAuth()
  const rateLimit = useRateLimitRecovery()
  const [sent, setSent] = useState(false)
  const [serverError, setServerError] = useState<ReturnType<typeof unexpectedAuthError>>()
  const {
    register,
    handleSubmit,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordValues>({ defaultValues: { email: '' } })

  const onSubmit = handleSubmit(
    async (values) => {
      setServerError(undefined)
      try {
        await manager.requestPasswordReset(values.email.trim())
        setSent(true)
      } catch (error) {
        if (
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
    },
    () => setFocus('email'),
  )

  return (
    <AuthCard
      eyebrow="Account recovery"
      title="Reset your password"
      description="Enter your email to request a secure password-reset link."
      footer={
        <p className="text-center text-sm text-muted-foreground">
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" to="/login">Back to Login</Link>
        </p>
      }
    >
      <form className="space-y-5" noValidate onSubmit={onSubmit}>
        {sent ? <SuccessFeedback title="Request accepted" description="If this email is eligible, a password-reset email will be sent." /> : null}
        {serverError ? <ErrorFeedback {...serverError} /> : null}
        {rateLimit.isLimited ? <RateLimitFeedback remainingSeconds={rateLimit.remainingSeconds} requestId={rateLimit.requestId} /> : null}
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
        <Button className="w-full" type="submit" disabled={isSubmitting || rateLimit.isActive}>
          {isSubmitting ? <Spinner /> : null}
          {isSubmitting ? 'Requesting…' : rateLimit.isActive ? 'Try again shortly' : 'Send reset link'}
        </Button>
      </form>
    </AuthCard>
  )
}
