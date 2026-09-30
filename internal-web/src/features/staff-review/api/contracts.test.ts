import { expect, it } from 'vitest'
import { staffReviewCaseSchema } from './contracts'

it('accepts a seeded Identity Loan Officer in review stewardship while keeping the review cycle ID strict', () => {
  const officer = {
    userId: '00000000-0000-0000-0000-000000000302',
    displayName: 'Loan Officer Demo',
    email: 'loan.officer@meridian.local',
  }
  const response = {
    loanApplicationId: '11111111-1111-4111-8111-111111111111',
    applicationNumber: 'UCL-20260905-000001', productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED', requestedAmount: 20_000_000, requestedTermMonths: 12,
    applicationStatus: 'UNDER_REVIEW', submittedAt: '2026-09-05T08:00:00',
    documentReadiness: { uploadComplete: true, processingReady: true },
    productReadiness: { productVerificationResult: 'VERIFIED', readyForReview: true },
    reviewStartAvailable: false, assignedLoanOfficer: officer,
    currentReviewCycle: {
      reviewCycleId: '33333333-3333-4333-8333-333333333333', cycleNumber: 1,
      assignedLoanOfficer: officer, status: 'ACTIVE',
      startedAt: '2026-09-05T08:10:00', endedAt: null,
    },
  }

  expect(staffReviewCaseSchema.parse(response).currentReviewCycle?.assignedLoanOfficer?.userId)
    .toBe(officer.userId)
  expect(staffReviewCaseSchema.safeParse({
    ...response,
    currentReviewCycle: { ...response.currentReviewCycle, reviewCycleId: officer.userId },
  }).success).toBe(false)
})
