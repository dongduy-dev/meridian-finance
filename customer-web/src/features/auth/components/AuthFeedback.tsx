import { AlertCircle, CheckCircle2, Clock3 } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

export function ErrorFeedback({
  title,
  description,
  requestId,
}: {
  title: string
  description: string
  requestId?: string
}) {
  return (
    <Alert variant="destructive" tabIndex={-1} data-server-error>
      <AlertCircle aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>
        <p>{description}</p>
        {requestId ? (
          <p className="mt-2 break-all text-xs">Support reference: {requestId}</p>
        ) : null}
      </AlertDescription>
    </Alert>
  )
}

export function SuccessFeedback({ title, description }: { title: string; description: string }) {
  return (
    <Alert variant="success" aria-live="polite">
      <CheckCircle2 aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{description}</AlertDescription>
    </Alert>
  )
}

export function RateLimitFeedback({
  remainingSeconds,
  requestId,
}: {
  remainingSeconds: number
  requestId?: string
}) {
  return (
    <Alert variant="warning" aria-live="polite">
      <Clock3 aria-hidden="true" />
      <AlertTitle>Please wait before trying again</AlertTitle>
      <AlertDescription>
        <p>
          {remainingSeconds > 0
            ? `You can try again in ${remainingSeconds} second${remainingSeconds === 1 ? '' : 's'}.`
            : 'The service is receiving too many requests. Try again shortly.'}
        </p>
        {requestId ? <p className="mt-2 break-all text-xs">Support reference: {requestId}</p> : null}
      </AlertDescription>
    </Alert>
  )
}

export function ValidationSummary({ messages }: { messages: string[] }) {
  if (messages.length < 2) {
    return null
  }

  return (
    <Alert variant="destructive">
      <AlertCircle aria-hidden="true" />
      <AlertTitle>Check the highlighted fields</AlertTitle>
      <AlertDescription>
        <ul className="list-disc space-y-1 pl-5">
          {messages.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      </AlertDescription>
    </Alert>
  )
}
