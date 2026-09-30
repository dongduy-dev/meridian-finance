import { describe, expect, it } from 'vitest'
import { humanizeKnownValue } from './presentation'

describe('Staff business labels', () => {
  it('explains known operational values without exposing their API spelling', () => {
    expect(humanizeKnownValue('UNSECURED_CONSUMER_LOAN')).toBe('Unsecured Consumer Loan')
    expect(humanizeKnownValue('REQUIRES_MORE_INFORMATION')).toBe('More information needed')
    expect(humanizeKnownValue('FLAT_ORIGINAL_PRINCIPAL')).toBe('Flat interest on original principal')
  })

  it('does not present an unfamiliar value as a known business state', () => {
    expect(humanizeKnownValue('FUTURE_REVIEW_RESULT')).toBe('Information unavailable')
  })
})
