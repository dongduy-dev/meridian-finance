import { describe, expect, it } from 'vitest'
import { staffDecisionCaseSchema } from './contracts'

describe('Staff approval response contracts', () => {
  it('accepts seeded Identity Staff actors without loosening generated recommendation IDs', () => {
    const actor = {
      userId: '00000000-0000-0000-0000-000000000303',
      displayName: 'Approver Demo', email: 'approver@meridian.local',
    }
    const decision = {
      decisionId: '22222222-2222-4222-8222-222222222222',
      reviewRecommendationId: '33333333-3333-4333-8333-333333333333',
      action: 'APPROVE', reason: null, reasonCode: null, recordedBy: actor,
      decidedAt: '2026-09-06T09:00:00',
    }
    const response = {
      loanApplicationId: '11111111-1111-4111-8111-111111111111', applicationNumber: 'UCL-1',
      productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'UNSECURED',
      requestedAmount: 1, requestedTermMonths: 1, applicationStatus: 'APPROVED',
      submittedAt: '2026-09-06T08:00:00',
      evidence: { uploadComplete: true, processingReady: true, productVerificationResult: 'VERIFIED',
        readyForDecision: true, currentReviewCycle: null },
      recommendation: {
        recommendationId: decision.reviewRecommendationId,
        reviewCycleId: '44444444-4444-4444-8444-444444444444',
        action: 'RECOMMEND_APPROVAL', reason: null, reasonCode: null,
        recordedBy: actor, submittedAt: '2026-09-06T08:30:00',
      },
      makerCheckerEligible: true, decisionAvailable: false,
      latestDecision: decision, decisionHistory: [decision],
      correctionReasonCodes: [], correctionOptions: [],
    }

    expect(staffDecisionCaseSchema.parse(response).latestDecision?.recordedBy?.userId).toBe(actor.userId)
    expect(staffDecisionCaseSchema.safeParse({
      ...response, recommendation: { ...response.recommendation, recommendationId: actor.userId },
    }).success).toBe(false)
  })

  it('accepts future non-empty response values for neutral rendering', () => {
    const result = staffDecisionCaseSchema.safeParse({
      loanApplicationId: '11111111-1111-4111-8111-111111111111', applicationNumber: 'UCL-1',
      productCode: 'FUTURE_PRODUCT', productType: 'FUTURE_TYPE', requestedAmount: 1, requestedTermMonths: 1,
      applicationStatus: 'FUTURE_STATE', submittedAt: '2026-09-06T08:00:00',
      evidence: { uploadComplete: true, processingReady: true, productVerificationResult: 'FUTURE_RESULT',
        readyForDecision: false, currentReviewCycle: null },
      recommendation: null, makerCheckerEligible: false, decisionAvailable: false,
      latestDecision: null, decisionHistory: [], correctionReasonCodes: [], correctionOptions: [],
    })
    expect(result.success).toBe(true)
  })
})
