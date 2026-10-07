import { afterEach, describe, expect, it } from 'vitest'

import { captureActivationTokens, captureFragmentToken } from './fragment-token'

afterEach(() => {
  window.history.replaceState(null, '', '/')
})
describe('fragment token capture', () => {
  it('captures both activation secrets across synchronous Strict Mode initialization only', async () => {
    window.history.replaceState(null, '', '/activate-access#verificationToken=verify%2B%26&setupToken=setup%2B%26')
    const tokens = captureActivationTokens('activation-location')
    expect(tokens).toEqual({ verificationToken: 'verify+&', setupToken: 'setup+&' })
    expect(window.location.hash).toBe('')
    expect(window.location.search).toBe('')
    expect(captureActivationTokens('activation-location')).toBe(tokens)
    await Promise.resolve()
    expect(captureActivationTokens('activation-location')).toEqual({})
  })
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
