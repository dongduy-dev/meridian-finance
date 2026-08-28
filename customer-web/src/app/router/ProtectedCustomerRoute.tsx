import { AlertTriangle, ShieldCheck } from 'lucide-react'
import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { MeridianLogo } from '@/components/common/MeridianLogo'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { ErrorFeedback, RateLimitFeedback } from '@/features/auth/components/AuthFeedback'
import { useRateLimitRecovery } from '@/features/auth/use-rate-limit'

function SessionCheckFailure() {
  const { manager, state } = useAuth()
  const rateLimit = useRateLimitRecovery()
  const startRateLimit = rateLimit.start
  const error = state.status === 'checking' ? state.error : undefined

  useEffect(() => {
    if (error?.rateLimited) {
      startRateLimit(error.retryAfter, error.requestId)
    }
  }, [error?.rateLimited, error?.requestId, error?.retryAfter, startRateLimit])

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10">
      <Card className="w-full max-w-lg">
        <CardHeader className="items-center text-center">
          <MeridianLogo variant="primary" className="mb-5 w-36" />
          <h1 id="page-heading" tabIndex={-1} className="text-2xl font-semibold outline-none">
            We could not check your session
          </h1>
          <p className="max-w-md text-sm leading-6 text-muted-foreground">
            Meridian has not signed you out. Check your connection and try the secure session check again.
          </p>
        </CardHeader>
        <CardContent className="space-y-5">
          {error?.rateLimited ? (
            <RateLimitFeedback remainingSeconds={rateLimit.remainingSeconds} requestId={rateLimit.requestId} />
          ) : (
            <ErrorFeedback
              title="Session check interrupted"
              description="Your Customer session could not be confirmed safely."
              requestId={error?.requestId}
            />
          )}
          <Button
            className="w-full"
            disabled={rateLimit.isActive}
            onClick={() => void manager.retryBootstrap()}
          >
            <ShieldCheck />
            {rateLimit.isActive ? 'Try again shortly' : 'Retry session check'}
          </Button>
        </CardContent>
      </Card>
    </main>
  )
}

export function ProtectedCustomerRoute() {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'checking' && state.error) {
    return <SessionCheckFailure />
  }

  if (state.status === 'checking') {
    return (
      <main className="flex min-h-svh items-center justify-center bg-primary text-primary-foreground">
        <div className="flex flex-col items-center gap-4 text-center" role="status" aria-live="polite">
          <MeridianLogo variant="primary" className="w-40" />
          <Spinner className="size-7 text-accent" />
          <p className="text-sm text-primary-foreground/75">Checking your secure session…</p>
        </div>
      </main>
    )
  }

  if (state.status === 'anonymous') {
    return (
      <Navigate
        replace
        to="/login"
        state={{ from: `${location.pathname}${location.search}` }}
      />
    )
  }

  if (state.actor.userType !== 'CUSTOMER' || !state.actor.customerId) {
    return (
      <main className="flex min-h-svh items-center justify-center bg-background px-4">
        <ErrorFeedback
          title="Customer access required"
          description="This Customer Web route is not available for the current account."
        />
        <AlertTriangle className="sr-only" />
      </main>
    )
  }

  return <Outlet />
}
