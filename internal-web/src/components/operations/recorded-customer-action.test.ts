import { z } from 'zod'
import { describe, expect, it } from 'vitest'
import { recordedCustomerActionSchema } from './recorded-customer-action'
import { uuidSchema } from '@/features/staff-applications/api/contracts'

const schema = recordedCustomerActionSchema(z.object({ documentVersionId: uuidSchema }))
const value = {
  action: 'FUTURE_CUSTOMER_DECISION',
  recordedBy: { userId: '00000000-0000-0000-0000-000000000302', displayName: 'Deni Loan Officer', email: 'deni@meridian.local' },
  recordedAt: '2026-10-01T08:00:00',
  evidence: { documentVersionId: '11111111-1111-4111-8111-111111111111' },
}

describe('Completed Customer action contract', () => {
  it('accepts seeded Staff entity IDs and future response-only decisions', () => {
    expect(schema.parse(value).action).toBe('FUTURE_CUSTOMER_DECISION')
    expect(schema.parse(value).recordedBy.userId).toBe(value.recordedBy.userId)
  })
  it('rejects malformed Staff IDs and non-runtime evidence UUIDs', () => {
    expect(schema.safeParse({ ...value, recordedBy: { ...value.recordedBy, userId: 'invalid' } }).success).toBe(false)
    expect(schema.safeParse({ ...value, evidence: { documentVersionId: value.recordedBy.userId } }).success).toBe(false)
  })
})
