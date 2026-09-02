import { AlertTriangle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { ApiError } from '@/lib/api'

export function QueryErrorPanel({
  error,
  resource,
  onRetry,
}: {
  error: unknown
  resource: 'index' | 'case'
  onRetry: () => void
}) {
  const forbidden = error instanceof ApiError && error.status === 403
  const missing = resource === 'case' && error instanceof ApiError && error.status === 404
  const title = forbidden ? 'Application access changed'
    : missing ? 'Application unavailable'
      : resource === 'case' ? 'Case data unavailable' : 'Applications unavailable'
  const description = forbidden
    ? 'Your current session does not have permission to read Staff applications.'
    : missing
      ? 'This application cannot be opened from the current session.'
      : error instanceof ApiError
        ? 'Meridian could not load this operational read. Try again when the service is available.'
        : 'The returned data could not be verified. No unverified application facts are displayed.'

  return (
    <Alert variant={forbidden || missing ? 'warning' : 'destructive'}>
      <AlertTriangle aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>
        <p>{description}</p>
        {!missing && !forbidden ? <Button className="mt-3" variant="outline" onClick={onRetry}>Try again</Button> : null}
        {error instanceof ApiError && error.requestId ? <RequestCorrelation requestId={error.requestId} /> : null}
      </AlertDescription>
    </Alert>
  )
}
