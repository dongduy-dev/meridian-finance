import { AlertTriangle } from 'lucide-react'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'

export function PartnerQueryErrorPanel({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const forbidden = error instanceof ApiError && error.status === 403
  const missing = error instanceof ApiError && error.status === 404
  return <Alert variant={forbidden || missing ? 'warning' : 'destructive'}>
    <AlertTriangle aria-hidden="true" /><AlertTitle>{forbidden ? 'Partner access changed' : missing ? 'Partner unavailable' : 'Partner data unavailable'}</AlertTitle>
    <AlertDescription><p>{forbidden ? 'Your session no longer has partner:read. Leave this route or sign in again after access is restored.' : missing ? 'This Partner Company is not available.' : 'Meridian could not verify the Partner response. Try again when the service is available.'}</p>{!forbidden && !missing ? <Button className="mt-3" variant="outline" onClick={onRetry}>Try again</Button> : null}{error instanceof ApiError && error.requestId ? <RequestCorrelation requestId={error.requestId} /> : null}</AlertDescription>
  </Alert>
}
