import { AlertCircle, CheckCircle2 } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { ApiError } from '@/lib/api'

export function AccountErrorFeedback({ error, title }: { error: unknown; title: string }) {
  const description =
    error instanceof ApiError
      ? error.message
      : 'The request could not be completed. Check your connection and try again.'

  return (
    <Alert variant="destructive" tabIndex={-1} data-account-error>
      <AlertCircle aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>
        <p>{description}</p>
        {error instanceof ApiError && error.requestId ? (
          <p className="mt-2 break-all text-xs">Support reference: {error.requestId}</p>
        ) : null}
      </AlertDescription>
    </Alert>
  )
}

export function AccountSuccessFeedback({
  description,
  title,
}: {
  description: string
  title: string
}) {
  return (
    <Alert variant="success" aria-live="polite">
      <CheckCircle2 aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{description}</AlertDescription>
    </Alert>
  )
}
