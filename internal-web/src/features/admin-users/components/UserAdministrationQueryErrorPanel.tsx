import { AlertTriangle } from 'lucide-react'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'

export function UserAdministrationQueryErrorPanel({ error, onRetry }: {
  error: unknown
  onRetry: () => void
}) {
  const forbidden = error instanceof ApiError && error.status === 403
  return <Alert variant={forbidden ? 'warning' : 'destructive'}>
    <AlertTriangle aria-hidden="true" />
    <AlertTitle>{forbidden ? 'Internal User access changed' : 'Internal Users unavailable'}</AlertTitle>
    <AlertDescription>
      <p>{forbidden
        ? 'Your session no longer has identity:user:manage. This workspace cannot load or change User authority.'
        : 'Meridian could not verify the protected Internal User response. Try again when the service is available.'}</p>
      {!forbidden ? <Button className="mt-3" variant="outline" onClick={onRetry}>Try again</Button> : null}
      {error instanceof ApiError && error.requestId ? <RequestCorrelation requestId={error.requestId} /> : null}
    </AlertDescription>
  </Alert>
}
