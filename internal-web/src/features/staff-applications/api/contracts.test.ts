import { describe, expect, it } from 'vitest'
import {
  staffLoanApplicationCaseSchema,
  staffLoanApplicationPageSchema,
} from './contracts'

const item = {
  loanApplicationId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
  applicationNumber: 'UCL-20260902-000001',
  productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED',
  requestedAmount: 12_000_000,
  requestedTermMonths: 6,
  status: 'UNDER_REVIEW',
  submittedAt: '2026-09-02T08:00:00',
}

describe('Staff application response schemas', () => {
  it('accepts exact index and case contracts while preserving unknown enum strings', () => {
    expect(staffLoanApplicationPageSchema.parse({
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      items: [{ ...item, status: 'FUTURE_APPLICATION_STATUS' }],
    }).items[0]?.status).toBe('FUTURE_APPLICATION_STATUS')

    const parsed = staffLoanApplicationCaseSchema.parse({
      ...item,
      customerReadiness: {
        active: true,
        profileComplete: true,
        hasPrimaryActiveBankAccount: true,
        verificationStatus: 'FUTURE_VERIFICATION_STATUS',
      },
      lifecycleHistory: [{
        fromStatus: null,
        toStatus: 'FUTURE_APPLICATION_STATUS',
        action: 'FUTURE_TRANSITION_ACTION',
        actorType: 'SYSTEM',
        occurredAt: '2026-09-02T08:00:00.123456',
      }],
    })
    expect(parsed.lifecycleHistory[0]?.action).toBe('FUTURE_TRANSITION_ACTION')
  })

  it.each([
    ['UUID', { ...item, loanApplicationId: 'not-a-uuid' }],
    ['amount', { ...item, requestedAmount: '12000000' }],
    ['timestamp', { ...item, submittedAt: '2026-02-31T08:00:00' }],
  ])('rejects an invalid required %s', (_field, invalidItem) => {
    expect(() => staffLoanApplicationPageSchema.parse({
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      items: [invalidItem],
    })).toThrow()
  })

  it.each([
    { page: -1, size: 20, totalElements: 0, totalPages: 0, items: [] },
    { page: 0, size: 0, totalElements: 0, totalPages: 0, items: [] },
    { page: 0, size: 20, totalElements: -1, totalPages: 0, items: [] },
    { page: 0, size: 20, totalElements: 0, totalPages: 0, items: null },
  ])('rejects invalid page structure', (payload) => {
    expect(() => staffLoanApplicationPageSchema.parse(payload)).toThrow()
  })
})
