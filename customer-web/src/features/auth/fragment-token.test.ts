import { afterEach, describe, expect, it } from 'vitest'

import { captureFragmentToken } from './fragment-token'

afterEach(() => {
  window.history.replaceState(null, '', '/')
})
describe('fragment token capture', () => {
  it('captures an email token in memory and immediately removes the fragment', () => {
    window.history.replaceState(null, '', '/verify-email#token=opaque-email-token')

    const token = captureFragmentToken('email-verification', 'verification-location')

    expect(token).toBe('opaque-email-token')
    expect(window.location.href).not.toContain('#')
    expect(window.location.search).toBe('')
  })

  it('returns the same in-memory token across a Strict Mode remount key', () => {
    window.history.replaceState(null, '', '/reset-password#token=opaque-reset-token')

    expect(captureFragmentToken('password-reset', 'reset-location')).toBe(
      'opaque-reset-token',
    )
    expect(captureFragmentToken('password-reset', 'reset-location')).toBe(
      'opaque-reset-token',
    )
    expect(window.location.hash).toBe('')
  })

  it('handles a missing token without inventing one', () => {
    window.history.replaceState(null, '', '/verify-email')
    expect(captureFragmentToken('email-verification', 'missing-location')).toBeUndefined()
  })
})
