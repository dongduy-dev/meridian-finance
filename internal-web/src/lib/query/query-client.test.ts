import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/lib/api'
import { createQueryClient, shouldRetryRead } from './query-client'

describe('query defaults', () => {
  it('retries one network or server read but not client errors', () => {
    expect(shouldRetryRead(0, new NetworkError())).toBe(true)
    expect(shouldRetryRead(0, new ApiError(503, 'UNAVAILABLE', 'no', '/x', 'now'))).toBe(true)
    expect(shouldRetryRead(1, new NetworkError())).toBe(false)
    expect(shouldRetryRead(0, new ApiError(409, 'CONFLICT', 'no', '/x', 'now'))).toBe(false)
  })

  it('never retries mutations', () => {
    expect(createQueryClient().getDefaultOptions().mutations?.retry).toBe(false)
  })
})
