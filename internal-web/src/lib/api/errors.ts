import { z } from 'zod'

const errorEnvelopeSchema = z.object({
  timestamp: z.string(),
  status: z.number(),
  errorCode: z.string(),
  message: z.string(),
  path: z.string(),
})

export type ErrorEnvelope = z.infer<typeof errorEnvelopeSchema>

export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly path: string
  readonly timestamp: string
  readonly requestId?: string
  readonly retryAfter?: string

  constructor(
    status: number,
    errorCode: string,
    message: string,
    path: string,
    timestamp: string,
    requestId?: string,
    retryAfter?: string,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.path = path
    this.timestamp = timestamp
    this.requestId = requestId
    this.retryAfter = retryAfter
  }
}

export class NetworkError extends Error {
  constructor(message = 'The Meridian service could not be reached.') {
    super(message)
    this.name = 'NetworkError'
  }
}

export async function toApiError(response: Response): Promise<ApiError> {
  const requestId = response.headers.get('X-Request-ID') ?? undefined
  const retryAfter = response.headers.get('Retry-After') ?? undefined
  let payload: unknown
  try {
    payload = await response.json()
  } catch {
    payload = undefined
  }
  const parsed = errorEnvelopeSchema.safeParse(payload)
  if (parsed.success) {
    return new ApiError(
      parsed.data.status,
      parsed.data.errorCode,
      parsed.data.message,
      parsed.data.path,
      parsed.data.timestamp,
      requestId,
      retryAfter,
    )
  }
  return new ApiError(
    response.status,
    'UNEXPECTED_RESPONSE',
    'The service returned an unexpected response.',
    response.url,
    new Date().toISOString(),
    requestId,
    retryAfter,
  )
}
