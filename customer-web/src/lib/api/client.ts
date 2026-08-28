import { environment } from '@/app/config/environment'

import { ApiError } from './ApiError'
import { NetworkError } from './NetworkError'
import { parseMeridianErrorEnvelope } from './error-envelope'

const GENERIC_ERROR_MESSAGE = 'The request could not be completed.'

export interface ApiRequestOptions
  extends Omit<RequestInit, 'body' | 'credentials' | 'headers' | 'signal'> {
  body?: BodyInit | null
  credentials?: RequestCredentials
  headers?: HeadersInit
  json?: unknown
  requestId?: string
  signal?: AbortSignal
}

export interface ApiClientOptions {
  baseUrl?: string
  fetchImplementation?: typeof fetch
  getAccessToken?: () => string | undefined
}

export interface ApiClient {
  request<TResponse = unknown>(
    path: string,
    options?: ApiRequestOptions,
  ): Promise<TResponse>
}

function joinUrl(baseUrl: string, path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${baseUrl}${normalizedPath}`
}

function defaultFetch(input: RequestInfo | URL, init?: RequestInit) {
  return fetch(input, init)
}

function isAbortError(error: unknown) {
  return (
    typeof error === 'object' &&
    error !== null &&
    'name' in error &&
    error.name === 'AbortError'
  )
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) {
    return undefined
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    return undefined
  }
}

async function toApiError(response: Response) {
  const envelope = parseMeridianErrorEnvelope(await readJson(response))
  return new ApiError({
    status: response.status,
    errorCode: envelope?.errorCode ?? 'HTTP_ERROR',
    message: envelope?.message ?? GENERIC_ERROR_MESSAGE,
    path: envelope?.path,
    timestamp: envelope?.timestamp,
    requestId: response.headers.get('X-Request-ID') ?? undefined,
    retryAfter: response.headers.get('Retry-After') ?? undefined,
  })
}

export function createApiClient({
  baseUrl = environment.apiBaseUrl,
  fetchImplementation = defaultFetch,
  getAccessToken,
}: ApiClientOptions = {}): ApiClient {
  async function request<TResponse = unknown>(
    path: string,
    options: ApiRequestOptions = {},
  ): Promise<TResponse> {
    const {
      body,
      credentials = 'same-origin',
      headers: suppliedHeaders,
      json,
      requestId,
      signal,
      ...requestInit
    } = options

    if (body !== undefined && json !== undefined) {
      throw new TypeError('Use either body or json for one API request, not both.')
    }

    const headers = new Headers(suppliedHeaders)
    headers.set('Accept', 'application/json')

    if (json !== undefined) {
      headers.set('Content-Type', 'application/json')
    }
    if (requestId) {
      headers.set('X-Request-ID', requestId)
    }

    const accessToken = getAccessToken?.()
    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }

    let response: Response
    try {
      response = await fetchImplementation(joinUrl(baseUrl, path), {
        ...requestInit,
        body: json === undefined ? body : JSON.stringify(json),
        credentials,
        headers,
        signal,
      })
    } catch (error) {
      if (signal?.aborted || isAbortError(error) || !(error instanceof TypeError)) {
        throw error
      }
      throw new NetworkError(error)
    }

    if (!response.ok) {
      throw await toApiError(response)
    }

    if (response.status === 204) {
      return undefined as TResponse
    }

    return (await readJson(response)) as TResponse
  }

  return {
    request,
  }
}

export const apiClient = createApiClient()
