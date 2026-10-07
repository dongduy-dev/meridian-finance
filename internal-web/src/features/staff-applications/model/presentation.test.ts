import { describe, expect, it } from 'vitest'
import { applicationStatusLabel, humanizeKnownValue } from './presentation'
import { allocationComponentLabel } from '@/features/staff-servicing/model/presentation'

describe('Staff business labels', () => {
  it('keeps credit decisions, channel provenance, and repayment allocation distinct', () => {
    expect(applicationStatusLabel('APPROVAL_PENDING')).toBe('Awaiting credit decision')
    expect(applicationStatusLabel('VERIFICATION_PENDING')).toBe('Verification pending')
    expect(humanizeKnownValue('CUSTOMER_DIGITAL')).toBe('Customer digital origination')
    expect(humanizeKnownValue('STAFF_ASSISTED')).toBe('Staff-assisted origination')
    expect(allocationComponentLabel('PRINCIPAL')).toBe('Principal')
    expect(allocationComponentLabel('UNKNOWN_COMPONENT')).toBe('Allocation component unavailable')
    expect(applicationStatusLabel('__proto__')).toBe('Status unavailable')
  })

  it('explains known operational values without exposing their API spelling', () => {
    expect(humanizeKnownValue('UNSECURED_CONSUMER_LOAN')).toBe('Unsecured Consumer Loan')
    expect(humanizeKnownValue('REQUIRES_MORE_INFORMATION')).toBe('More information needed')
    expect(humanizeKnownValue('FLAT_ORIGINAL_PRINCIPAL')).toBe('Flat interest on original principal')
  })

  it('labels known product types and keeps unfamiliar types neutral', () => {
    expect(humanizeKnownValue('SECURED')).toBe('Secured')
    expect(humanizeKnownValue('UNSECURED')).toBe('Unsecured')
    expect(humanizeKnownValue('SALARY_BASED')).toBe('Salary-based')
    expect(humanizeKnownValue('FUTURE_PRODUCT_TYPE')).toBe('Information unavailable')
  })

  it('does not present an unfamiliar value as a known business state', () => {
    expect(humanizeKnownValue('FUTURE_REVIEW_RESULT')).toBe('Information unavailable')
  })
})
