import { beforeEach, describe, expect, it } from 'vitest'
import { StaffSetupAttempt } from './fragment-token'

describe('Staff setup transient token lifecycle', () => {
  beforeEach(() => { window.history.replaceState({ key: 'setup-entry' }, '', '/set-password#token=opaque-value') })

  it('scrubs before use, survives synchronous effect replay, and clears on real departure', async () => {
    const attempt = new StaffSetupAttempt()
    const cleanup = attempt.mount()
    expect(window.location.hash).toBe('')
    expect(window.history.state).toEqual({ key: 'setup-entry' })
    cleanup()
    const finalCleanup = attempt.mount()
    await Promise.resolve()
    expect(attempt.hasToken()).toBe(true)
    finalCleanup()
    await Promise.resolve()
    expect(attempt.hasToken()).toBe(false)
    expect(attempt.takeToken()).toBeUndefined()
  })

  it('releases a token at most once and does not recover it from a scrubbed URL', () => {
    const attempt = new StaffSetupAttempt()
    const cleanup = attempt.mount()
    expect(attempt.takeToken()).toBe('opaque-value')
    expect(attempt.takeToken()).toBeUndefined()
    const later = new StaffSetupAttempt()
    const laterCleanup = later.mount()
    expect(later.hasToken()).toBe(false)
    cleanup()
    laterCleanup()
  })

  it('does not touch the browser during a discarded render initialization', () => {
    new StaffSetupAttempt()
    expect(window.location.hash).toBe('#token=opaque-value')
  })
})
