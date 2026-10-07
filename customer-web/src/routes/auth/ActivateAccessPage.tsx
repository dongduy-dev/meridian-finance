import { useEffect, useState } from 'react'
import { useForm, type FieldErrors } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { focusServerError, isAuthApiError, unexpectedAuthError } from '@/features/auth/auth-errors'
import { AuthCard } from '@/features/auth/components/AuthCard'
import { ErrorFeedback, SuccessFeedback, ValidationSummary } from '@/features/auth/components/AuthFeedback'
import { FormField } from '@/features/auth/components/FormField'
import { fieldDescriptionIds } from '@/features/auth/field-description'
import { captureActivationTokens, type ActivationTokens } from '@/features/auth/fragment-token'
import { newPasswordSchema, validateWith } from '@/features/auth/validation'

type Stage = 'missing' | 'verifying' | 'ready' | 'verification-invalid' | 'verification-error' | 'setup-invalid' | 'setup-unknown'
interface PasswordValues { password: string; confirmPassword: string }

class ActivationAttempt {
  readonly confirmationKey = `customer-activation:${crypto.randomUUID()}`
  private mounted = false
  private generation = 0
  private readonly tokens: ActivationTokens

  constructor(tokens: ActivationTokens) { this.tokens = tokens }

  hasTokens() { return Boolean(this.tokens.verificationToken && this.tokens.setupToken) }
  verificationToken() { return this.tokens.verificationToken }
  isMounted() { return this.mounted }
  clearVerification() { this.tokens.verificationToken = undefined }
  clear() { this.clearVerification(); this.tokens.setupToken = undefined }
  takeSetupToken() {
    const token = this.tokens.setupToken
    this.tokens.setupToken = undefined
    return token
  }
  mount(reset: () => void) {
    this.mounted = true
    const generation = ++this.generation
    return () => {
      this.mounted = false
      // Preserve synchronous Strict Mode effect replay; clear on a real departure.
      queueMicrotask(() => {
        if (this.generation === generation) { this.clear(); reset() }
      })
    }
  }
}

function ActivateAccessContent({ locationKey }: { locationKey: string }) {
  const { manager } = useAuth()
  const navigate = useNavigate()
  const [attempt] = useState(() => new ActivationAttempt(captureActivationTokens(locationKey)))
  const [stage, setStage] = useState<Stage>(attempt.hasTokens() ? 'verifying' : 'missing')
  const [serverError, setServerError] = useState<ReturnType<typeof unexpectedAuthError>>()
  const { register, handleSubmit, getValues, reset, setFocus, formState: { errors, isSubmitting } } = useForm<PasswordValues>({
    defaultValues: { password: '', confirmPassword: '' },
  })

  useEffect(() => {
    const cleanup = attempt.mount(reset)
    const verificationToken = attempt.verificationToken()
    if (verificationToken && attempt.hasTokens()) {
      void manager.confirmEmailVerificationOnce(attempt.confirmationKey, verificationToken)
        .then(() => {
          if (attempt.isMounted()) { attempt.clearVerification(); setStage('ready') }
        })
        .catch((error: unknown) => {
          if (!attempt.isMounted()) return
          attempt.clear()
          if (isAuthApiError(error, 401, 'INVALID_EMAIL_VERIFICATION_TOKEN')) setStage('verification-invalid')
          else { setServerError(unexpectedAuthError(error)); setStage('verification-error') }
        })
    }
    return cleanup
  }, [manager, reset, attempt])

  useEffect(() => {
    if (stage === 'ready') setFocus('password')
    else if (stage !== 'verifying') focusServerError()
  }, [setFocus, stage])

  const onInvalid = (fieldErrors: FieldErrors<PasswordValues>) => setFocus(fieldErrors.password ? 'password' : 'confirmPassword')
  const onSubmit = handleSubmit(async (values) => {
    if (stage !== 'ready') return
    const token = attempt.takeSetupToken()
    if (!token) return
    try {
      await manager.confirmPasswordReset(token, values.password)
      reset()
      if (attempt.isMounted()) navigate('/login', { replace: true, state: { notice: 'PASSWORD_SETUP_SUCCESS' } })
    } catch (error) {
      reset()
      if (!attempt.isMounted()) return
      if (isAuthApiError(error, 401, 'INVALID_PASSWORD_RESET_TOKEN')) setStage('setup-invalid')
      else { setServerError(unexpectedAuthError(error)); setStage('setup-unknown') }
    }
  }, onInvalid)
  const validationMessages = Object.values(errors).map((error) => error?.message)
    .filter((message): message is string => typeof message === 'string')
  const verificationRecovery = ['missing', 'verification-invalid', 'verification-error'].includes(stage)

  return <AuthCard eyebrow="Account activation" title={stage === 'ready' ? 'Set your password' : 'Activate your access'}
    description="Verify your email and set your first password to access your existing Meridian account online."
    footer={<p className="text-sm leading-5 text-muted-foreground"><Link className="font-semibold text-primary underline underline-offset-4" to="/login">Continue to log in</Link></p>}>
    <div className="space-y-6">
      {stage === 'verifying' ? <div role="status" aria-live="polite" className="flex items-center gap-3"><Spinner />Verifying your email…</div> : null}
      {stage === 'ready' ? <>
        <SuccessFeedback title="Email verified" description="Set your first password to finish activating online access." />
        <form className="space-y-6" noValidate onSubmit={onSubmit}>
          <ValidationSummary messages={validationMessages} />
          <FormField htmlFor="password" label="New password" description="Use 12 to 72 characters." error={errors.password?.message}>
            <Input id="password" type="password" autoComplete="new-password" aria-invalid={Boolean(errors.password)}
              aria-describedby={fieldDescriptionIds('password', true, Boolean(errors.password))}
              {...register('password', { validate: validateWith(newPasswordSchema) })} />
          </FormField>
          <FormField htmlFor="confirmPassword" label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input id="confirmPassword" type="password" autoComplete="new-password" aria-invalid={Boolean(errors.confirmPassword)}
              aria-describedby={fieldDescriptionIds('confirmPassword', false, Boolean(errors.confirmPassword))}
              {...register('confirmPassword', { validate: (value) => value === getValues('password') || 'Passwords must match.' })} />
          </FormField>
          <Button size="lg" type="submit" className="w-full" disabled={isSubmitting}>{isSubmitting ? <Spinner /> : null}{isSubmitting ? 'Setting password…' : 'Set password'}</Button>
        </form>
      </> : null}
      {stage === 'missing' ? <ErrorFeedback title="Activation information missing" description="This activation link is incomplete. Open the complete link from your invitation." /> : null}
      {stage === 'verification-invalid' ? <ErrorFeedback title="Verification link unavailable" description="This verification link is invalid or has expired. Request another verification email." /> : null}
      {stage === 'verification-error' || stage === 'setup-unknown' ? <ErrorFeedback {...serverError!} /> : null}
      {verificationRecovery ? <>
        <p className="text-sm text-muted-foreground">If needed, request another verification email and follow its link. Once your email is confirmed, use Forgot password to obtain a fresh password link. Do not register another account.</p>
        <Button asChild variant="secondary" className="w-full"><Link to="/verify-email/pending">Request verification email</Link></Button>
        <Link className="block text-sm font-semibold text-primary underline underline-offset-4" to="/forgot-password">Already verified? Recover password setup</Link>
      </> : null}
      {stage === 'setup-invalid' ? <ErrorFeedback title="Password setup link unavailable" description="Your email is verified, but this password setup link is invalid or has expired." /> : null}
      {stage === 'setup-unknown' ? <p className="text-sm text-muted-foreground">We could not confirm whether your password was set. Try signing in with the password you chose, or request a fresh password link. This page will not resend your password.</p> : null}
      {stage === 'setup-invalid' || stage === 'setup-unknown' ? <Button asChild variant="secondary" className="w-full"><Link to="/forgot-password">Recover password setup</Link></Button> : null}
    </div>
  </AuthCard>
}

export function ActivateAccessPage() {
  const location = useLocation()
  return <ActivateAccessContent key={location.key} locationKey={location.key} />
}
