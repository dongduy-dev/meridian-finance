import { AlertCircle, BadgeCheck, MailWarning } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { unexpectedAuthError } from '@/features/auth/auth-errors'
import { AuthCard } from '@/features/auth/components/AuthCard'
import { ErrorFeedback, SuccessFeedback } from '@/features/auth/components/AuthFeedback'
import { captureFragmentToken } from '@/features/auth/fragment-token'
import { isAuthApiError } from '@/features/auth/auth-errors'

type ConfirmationState =
  | { status: 'missing' }
  | { status: 'submitting' }
  | { status: 'success' }
  | { status: 'invalid' }
  | { status: 'error'; error: ReturnType<typeof unexpectedAuthError> }

function VerifyEmailContent({ locationKey }: { locationKey: string }) {
  const { manager } = useAuth()
  const [token] = useState(() => captureFragmentToken('email-verification', locationKey))
  const [state, setState] = useState<ConfirmationState>(
    token ? { status: 'submitting' } : { status: 'missing' },
  )

  useEffect(() => {
    if (!token) {
      return
    }

    let active = true
    void manager
      .confirmEmailVerificationOnce(`email-verification:${locationKey}`, token)
      .then(() => {
        if (active) setState({ status: 'success' })
      })
      .catch((error: unknown) => {
        if (!active) return
        setState(
          isAuthApiError(error, 401, 'INVALID_EMAIL_VERIFICATION_TOKEN')
            ? { status: 'invalid' }
            : { status: 'error', error: unexpectedAuthError(error) },
        )
      })

    return () => {
      active = false
    }
  }, [locationKey, manager, token])

  return (
    <AuthCard
      eyebrow="Email confirmation"
      title="Confirm your email"
      description="Meridian is securely checking the confirmation link."
      footer={
        <p className="text-center text-sm text-muted-foreground">
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" to="/login">Return to Login</Link>
        </p>
      }
    >
      <div className="space-y-5">
        {state.status === 'submitting' ? (
          <div className="flex min-h-36 flex-col items-center justify-center gap-3 text-center" role="status" aria-live="polite">
            <Spinner className="size-7 text-primary" />
            <p className="font-semibold">Confirming your email…</p>
            <p className="text-sm text-muted-foreground">Keep this page open for a moment.</p>
          </div>
        ) : null}
        {state.status === 'success' ? (
          <>
            <SuccessFeedback title="Email confirmed" description="Your email is ready. You can now log in to Customer Web." />
            <Button asChild className="w-full"><Link to="/login"><BadgeCheck />Continue to Login</Link></Button>
          </>
        ) : null}
        {state.status === 'missing' ? (
          <>
            <ErrorFeedback title="Confirmation token missing" description="This confirmation link is incomplete. Request another verification email." />
            <Button asChild variant="secondary" className="w-full"><Link to="/verify-email/pending"><MailWarning />Request another email</Link></Button>
          </>
        ) : null}
        {state.status === 'invalid' ? (
          <>
            <ErrorFeedback title="Confirmation link unavailable" description="This link is invalid or has expired. Request another verification email." />
            <Button asChild variant="secondary" className="w-full"><Link to="/verify-email/pending"><MailWarning />Request another email</Link></Button>
          </>
        ) : null}
        {state.status === 'error' ? (
          <>
            <ErrorFeedback {...state.error} />
            <p className="flex items-start gap-2 text-sm leading-6 text-muted-foreground"><AlertCircle aria-hidden="true" className="mt-1 size-4 shrink-0" />The confirmation link has not been shown or stored elsewhere.</p>
          </>
        ) : null}
      </div>
    </AuthCard>
  )
}

export function VerifyEmailPage() {
  const location = useLocation()
  const locationKey = `${location.key}:${location.hash}`
  return <VerifyEmailContent key={locationKey} locationKey={locationKey} />
}
