import { KeyRound } from 'lucide-react'
import { useState } from 'react'
import { useForm, type FieldErrors } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { focusServerError, isAuthApiError, unexpectedAuthError } from '@/features/auth/auth-errors'
import { AuthCard } from '@/features/auth/components/AuthCard'
import { ErrorFeedback, ValidationSummary } from '@/features/auth/components/AuthFeedback'
import { FormField } from '@/features/auth/components/FormField'
import { fieldDescriptionIds } from '@/features/auth/field-description'
import { captureFragmentToken } from '@/features/auth/fragment-token'
import { newPasswordSchema, validateWith } from '@/features/auth/validation'

interface ResetPasswordValues {
  password: string
  confirmPassword: string
}

function ResetPasswordContent({ locationKey }: { locationKey: string }) {
  const { manager } = useAuth()
  const navigate = useNavigate()
  const [token] = useState(() => captureFragmentToken('password-reset', locationKey))
  const [invalidToken, setInvalidToken] = useState(false)
  const [serverError, setServerError] = useState<ReturnType<typeof unexpectedAuthError>>()
  const {
    register,
    handleSubmit,
    getValues,
    resetField,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    defaultValues: { password: '', confirmPassword: '' },
  })

  const onInvalid = (fieldErrors: FieldErrors<ResetPasswordValues>) => {
    setFocus(fieldErrors.password ? 'password' : 'confirmPassword')
  }

  const onSubmit = handleSubmit(async (values) => {
    if (!token) return
    setServerError(undefined)
    try {
      await manager.confirmPasswordReset(token, values.password)
      navigate('/login', {
        replace: true,
        state: { notice: 'PASSWORD_RESET_SUCCESS' },
      })
    } catch (error) {
      resetField('password')
      resetField('confirmPassword')
      if (isAuthApiError(error, 401, 'INVALID_PASSWORD_RESET_TOKEN')) {
        setInvalidToken(true)
      } else {
        setServerError(unexpectedAuthError(error))
        focusServerError()
      }
    }
  }, onInvalid)

  const validationMessages = Object.values(errors)
    .map((error) => error?.message)
    .filter((message): message is string => typeof message === 'string')

  return (
    <AuthCard
      eyebrow="Account recovery"
      title="Choose a new password"
      description="Set a new password for your Meridian account."
      footer={
        <p className="text-center text-sm text-muted-foreground">
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" to="/login">Back to Login</Link>
        </p>
      }
    >
      {!token || invalidToken ? (
        <div className="space-y-5">
          <ErrorFeedback
            title={!token ? 'Reset token missing' : 'Reset link unavailable'}
            description={!token ? 'This password-reset link is incomplete.' : 'This password-reset link is invalid or has expired.'}
          />
          <Button asChild variant="secondary" className="w-full">
            <Link to="/forgot-password"><KeyRound />Request another reset link</Link>
          </Button>
        </div>
      ) : (
        <form className="space-y-5" noValidate onSubmit={onSubmit}>
          <ValidationSummary messages={validationMessages} />
          {serverError ? <ErrorFeedback {...serverError} /> : null}
          <FormField
            htmlFor="password"
            label="New password"
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
          <FormField htmlFor="confirmPassword" label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.confirmPassword)}
              aria-describedby={fieldDescriptionIds('confirmPassword', false, Boolean(errors.confirmPassword))}
              {...register('confirmPassword', {
                validate: (value) => value === getValues('password') || 'Passwords must match.',
              })}
            />
          </FormField>
          <Button className="w-full" type="submit" disabled={isSubmitting}>
            {isSubmitting ? <Spinner /> : null}
            {isSubmitting ? 'Updating password…' : 'Update password'}
          </Button>
        </form>
      )}
    </AuthCard>
  )
}

export function ResetPasswordPage() {
  const location = useLocation()
  const locationKey = `${location.key}:${location.hash}`
  return <ResetPasswordContent key={locationKey} locationKey={locationKey} />
}
