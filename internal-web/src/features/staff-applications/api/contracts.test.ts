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
  it('accepts seeded Identity users in assigned and lifecycle actor summaries, while retaining strict application IDs', () => {
    const seededActor = {
      userId: '00000000-0000-0000-0000-000000000302',
      displayName: 'Loan Officer Demo',
      email: 'loan.officer@meridian.local',
    }
    const caseResponse = {
      ...item,
      customerContext: null,
      collateralContext: null,
      customerReadiness: {
        active: true, profileComplete: true, hasPrimaryActiveBankAccount: true,
        verificationStatus: 'VERIFIED',
      },
      formalReviewRecorded: true,
      assignedLoanOfficer: seededActor,
      lifecycleHistory: [{
        fromStatus: 'UNDER_REVIEW', toStatus: 'RETURNED_FOR_REVISION',
        action: 'RETURN_TO_CUSTOMER_REVISION', actorType: 'STAFF', actor: seededActor,
        occurredAt: '2026-09-02T09:00:00',
      }],
    }

    const parsed = staffLoanApplicationCaseSchema.parse(caseResponse)
    expect(parsed.assignedLoanOfficer?.userId).toBe(seededActor.userId)
    expect(parsed.lifecycleHistory[0]?.actor?.userId).toBe(seededActor.userId)
    expect(staffLoanApplicationCaseSchema.safeParse({ ...caseResponse, lifecycleHistory: [{ ...caseResponse.lifecycleHistory[0], actor: { ...seededActor, userId: 'invalid' } }] }).success).toBe(false)
    expect(staffLoanApplicationCaseSchema.safeParse({
      ...caseResponse, loanApplicationId: seededActor.userId,
    }).success).toBe(false)
  })

  it('accepts exact index and case contracts while preserving unknown enum strings', () => {
    expect(staffLoanApplicationPageSchema.parse({
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      items: [{ ...item, status: 'FUTURE_APPLICATION_STATUS' }],
    }).items[0]?.status).toBe('FUTURE_APPLICATION_STATUS')

    const parsed = staffLoanApplicationCaseSchema.parse({
      customerContext: null,
      collateralContext: null,
      ...item,
      formalReviewRecorded: true,
      assignedLoanOfficer: {
        userId: '11111111-1111-4111-8111-111111111111',
        displayName: 'Deni Loan Officer',
        email: 'deni.loan.officer@meridian.local',
      },
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
        actor: null,
        occurredAt: '2026-09-02T08:00:00.123456',
      }],
    })
    expect(parsed.lifecycleHistory[0]?.action).toBe('FUTURE_TRANSITION_ACTION')
    expect(parsed.assignedLoanOfficer?.displayName).toBe('Deni Loan Officer')
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
