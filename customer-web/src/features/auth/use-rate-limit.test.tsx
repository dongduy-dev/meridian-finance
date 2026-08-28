import { act, render, screen } from '@testing-library/react'
import { useEffect } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useRateLimitRecovery } from './use-rate-limit'

function RateLimitHarness({ retryAfter }: { retryAfter?: string }) {
  const rateLimit = useRateLimitRecovery()
  const start = rateLimit.start

  useEffect(() => {
    start(retryAfter)
  }, [retryAfter, start])

  return (
    <button disabled={rateLimit.isActive}>
      {rateLimit.isActive ? `Retry in ${rateLimit.remainingSeconds}` : 'Retry now'}
    </button>
  )
}

afterEach(() => vi.useRealTimers())

describe('auth rate-limit recovery', () => {
  it('uses a valid Retry-After countdown and restores the action', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-28T15:00:00Z'))
    render(<RateLimitHarness retryAfter="3" />)

    expect(screen.getByRole('button')).toBeDisabled()
    expect(screen.getByRole('button')).toHaveTextContent('Retry in 3')

    act(() => vi.advanceTimersByTime(3_000))

    expect(screen.getByRole('button')).toBeEnabled()
    expect(screen.getByRole('button')).toHaveTextContent('Retry now')
  })

  it('does not invent a countdown for an invalid Retry-After value', () => {
    render(<RateLimitHarness retryAfter="not-a-window" />)
    expect(screen.getByRole('button')).toBeEnabled()
  })
})
