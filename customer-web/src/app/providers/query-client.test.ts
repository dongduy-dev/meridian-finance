import { describe, expect, it } from 'vitest'

import { ApiError } from '@/lib/api/ApiError'
import { NetworkError } from '@/lib/api/NetworkError'

import { shouldRetryQuery } from './query-client'

describe('query retry policy', () => {
  it.each([
    ['a server failure', new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Unavailable' })],
    ['a network failure', new NetworkError(new TypeError('Failed to fetch'))],
  ])('retries %s at most once', (_label, error) => {
    expect(shouldRetryQuery(0, error)).toBe(true)
    expect(shouldRetryQuery(1, error)).toBe(false)
  })

  it.each([
    ['a client API failure', new ApiError({ status: 409, errorCode: 'CONFLICT', message: 'Conflict' })],
    ['an aborted request', new DOMException('Aborted', 'AbortError')],
    ['a parsing failure', new SyntaxError('Unexpected token')],
    ['an application failure', new Error('Programming error')],
  ])('does not retry %s', (_label, error) => {
    expect(shouldRetryQuery(0, error)).toBe(false)
  })
})
