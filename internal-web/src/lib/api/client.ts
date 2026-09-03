import { environment } from '@/app/config/environment'
import { NetworkError, toApiError } from './errors'

export type ApiRequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
  requestId?: string
  responseType?: 'json' | 'blob'
}

export type ApiBinaryResponse = {
  blob: Blob
  contentType: string
  contentLength?: number
  contentDisposition?: string
}

function resolveUrl(path: string): string {
  return `${environment.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', options.responseType === 'blob' ? 'application/pdf, image/jpeg, image/png' : 'application/json')
  const formData = options.body instanceof FormData
  if (options.body !== undefined && !formData) headers.set('Content-Type', 'application/json')
  if (options.requestId) headers.set('X-Request-ID', options.requestId)

  let response: Response
  try {
    const requestBody: BodyInit | undefined = options.body === undefined
      ? undefined
      : formData ? options.body as FormData : JSON.stringify(options.body)
    response = await fetch(resolveUrl(path), {
      ...options,
      body: requestBody,
      credentials: 'include',
      headers,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new NetworkError()
  }

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  if (options.responseType === 'blob') {
    const rawLength = response.headers.get('Content-Length')
    return {
      blob: await response.blob(),
      contentType: response.headers.get('Content-Type') ?? 'application/octet-stream',
      contentLength: rawLength === null ? undefined : Number(rawLength),
      contentDisposition: response.headers.get('Content-Disposition') ?? undefined,
    } as T
  }
  try {
    return (await response.json()) as T
  } catch {
    throw new NetworkError('The service returned unreadable data.')
  }
}
