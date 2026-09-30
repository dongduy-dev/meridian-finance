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
  resource: 'index' | 'case' | 'queue' | 'document queue' | 'document evidence' | 'correction queue' | 'correction evidence' | 'approval queue'
  onRetry: () => void
}) {
  const forbidden = error instanceof ApiError && error.status === 403
  const caseResource = resource === 'case' || resource.endsWith('evidence')
  const missing = caseResource && error instanceof ApiError && error.status === 404
  const title = forbidden ? 'Application access changed'
    : missing ? 'Application unavailable'
      : caseResource ? 'Case data unavailable' : 'Work queue unavailable'
  const description = forbidden
    ? 'You no longer have access to this workspace.'
    : missing
      ? 'This application cannot be opened from the current session.'
      : error instanceof ApiError
        ? 'Meridian could not load the latest information. Try again before taking action.'
        : 'Meridian could not read the response. Try again to load this information before taking action.'

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
