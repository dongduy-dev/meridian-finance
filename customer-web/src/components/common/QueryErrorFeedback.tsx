import { AlertCircle, RefreshCw } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'

export function QueryErrorFeedback({
  error,
  title,
  onRetry,
}: {
  error: unknown
  title: string
  onRetry: () => void
}) {
  const description =
    error instanceof ApiError
      ? error.message
      : 'The request could not be completed. Check your connection and try again.'

  return (
    <div className="space-y-3">
      <Alert variant="destructive">
        <AlertCircle aria-hidden="true" />
        <AlertTitle>{title}</AlertTitle>
        <AlertDescription>
          <p>{description}</p>
          {error instanceof ApiError && error.requestId ? (
            <p className="mt-2 break-all text-xs">Support reference: {error.requestId}</p>
          ) : null}
        </AlertDescription>
      </Alert>
      <Button variant="secondary" size="sm" onClick={onRetry}>
        <RefreshCw aria-hidden="true" />
        Try again
      </Button>
    </div>
  )
}
