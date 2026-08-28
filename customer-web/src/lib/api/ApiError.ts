export interface ApiErrorOptions {
  status: number
  errorCode: string
  message: string
  path?: string
  timestamp?: string
  requestId?: string
  retryAfter?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly path?: string
  readonly timestamp?: string
  readonly requestId?: string
  readonly retryAfter?: string

  constructor(options: ApiErrorOptions) {
    super(options.message)
    this.name = 'ApiError'
    this.status = options.status
    this.errorCode = options.errorCode
    this.path = options.path
    this.timestamp = options.timestamp
    this.requestId = options.requestId
    this.retryAfter = options.retryAfter
  }
}
