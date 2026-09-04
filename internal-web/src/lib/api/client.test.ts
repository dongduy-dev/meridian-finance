import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { ApiError, NetworkError } from './errors'

afterEach(() => vi.unstubAllGlobals())

describe('api client', () => {
  it('uses cookies, JSON headers, and preserves request correlation', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(apiRequest('/health', { method: 'POST', body: { value: 1 }, requestId: 'request-1' })).resolves.toEqual({ ok: true })
    const [, request] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(request.credentials).toBe('include')
    expect(new Headers(request.headers).get('X-Request-ID')).toBe('request-1')
    expect(request.body).toBe('{"value":1}')
  })

  it('sends FormData without overriding the browser multipart boundary', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const body = new FormData()
    body.set('uploadRequestId', '11111111-1111-4111-8111-111111111111')
    await apiRequest('/documents', { method: 'POST', body })
    const [, request] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(request.body).toBe(body)
    expect(new Headers(request.headers).has('Content-Type')).toBe(false)
  })

  it('returns Blob content with safe presentation headers', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('document', {
      status: 200,
      headers: {
        'Content-Type': 'application/pdf',
        'Content-Length': '8',
        'Content-Disposition': 'attachment; filename="proof.pdf"',
      },
    })))
    await expect(apiRequest('/content', { responseType: 'blob' })).resolves.toMatchObject({
      contentType: 'application/pdf',
      contentLength: 8,
      contentDisposition: 'attachment; filename="proof.pdf"',
    })
  })

  it('maps the safe backend envelope and response headers', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ timestamp: '2026-09-01T00:00:00Z', status: 429, errorCode: 'RATE_LIMIT_EXCEEDED', message: 'Too many requests.', path: '/api/v1/auth/login' }), { status: 429, headers: { 'X-Request-ID': 'req-2', 'Retry-After': '60' } })))
    const error = await apiRequest('/auth/login').catch((value: unknown) => value)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 429, errorCode: 'RATE_LIMIT_EXCEEDED', requestId: 'req-2', retryAfter: '60' })
  })

  it('does not leak raw fetch failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('socket detail')))
    await expect(apiRequest('/health')).rejects.toBeInstanceOf(NetworkError)
  })
})
