import { describe, expect, it, vi } from 'vitest'

import { createApiClient } from './client'
import { parseMeridianErrorEnvelope } from './error-envelope'

function createFetchMock() {
  return vi.fn<
    (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
  >()
}

describe('Meridian API client', () => {
  it('returns undefined for a successful no-content response', async () => {
    const fetchMock = createFetchMock()
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    const client = createApiClient({
      baseUrl: 'http://localhost:8080/api/v1',
      fetchImplementation: fetchMock as typeof fetch,
    })

    await expect(client.request<void>('/health')).resolves.toBeUndefined()
  })

  it('parses the safe Meridian error envelope and response recovery headers', async () => {
    const fetchMock = createFetchMock()
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          timestamp: '2026-08-28T10:00:00Z',
          status: 429,
          errorCode: 'RATE_LIMIT_EXCEEDED',
          message: 'Too many requests.',
          path: '/api/v1/auth/login',
        }),
        {
          status: 429,
          headers: {
            'Content-Type': 'application/json',
            'Retry-After': '45',
            'X-Request-ID': 'request-correlation-id',
          },
        },
      ),
    )
    const client = createApiClient({
      baseUrl: 'http://localhost:8080/api/v1',
      fetchImplementation: fetchMock as typeof fetch,
    })

    const request = client.request('/auth/login')

    await expect(request).rejects.toEqual(
      expect.objectContaining({
        status: 429,
        errorCode: 'RATE_LIMIT_EXCEEDED',
        message: 'Too many requests.',
        path: '/api/v1/auth/login',
        retryAfter: '45',
        requestId: 'request-correlation-id',
      }),
    )
  })

  it('sends JSON, credentials, correlation, abort, and an optional access token centrally', async () => {
    const fetchMock = createFetchMock()
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ accepted: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const controller = new AbortController()
    const client = createApiClient({
      baseUrl: 'http://localhost:8080/api/v1',
      fetchImplementation: fetchMock as typeof fetch,
      getAccessToken: () => 'in-memory-token',
    })

    await client.request('/foundation', {
      method: 'POST',
      credentials: 'include',
      json: { foundation: true },
      requestId: 'request-id',
      signal: controller.signal,
    })

    const [url, init] = fetchMock.mock.calls[0] ?? []
    const headers = new Headers(init?.headers)
    expect(url).toBe('http://localhost:8080/api/v1/foundation')
    expect(init?.credentials).toBe('include')
    expect(init?.signal).toBe(controller.signal)
    expect(init?.body).toBe(JSON.stringify({ foundation: true }))
    expect(headers.get('Authorization')).toBe('Bearer in-memory-token')
    expect(headers.get('X-Request-ID')).toBe('request-id')
  })
})

describe('Meridian error envelope parser', () => {
  it('rejects an untrusted or partial error body', () => {
    expect(parseMeridianErrorEnvelope({ status: 500, message: 'Internal detail' })).toBeUndefined()
  })
})
