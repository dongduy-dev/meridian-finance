import { environment } from '@/app/config/environment'
import { NetworkError, toApiError } from './errors'

export type ApiRequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
  requestId?: string
}

function resolveUrl(path: string): string {
  return `${environment.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (options.requestId) headers.set('X-Request-ID', options.requestId)

  let response: Response
  try {
    response = await fetch(resolveUrl(path), {
      ...options,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'include',
      headers,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new NetworkError()
  }

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  try {
    return (await response.json()) as T
  } catch {
    throw new NetworkError('The service returned unreadable data.')
  }
}
