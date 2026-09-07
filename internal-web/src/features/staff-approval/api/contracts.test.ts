import { describe, expect, it } from 'vitest'
import { staffDecisionCaseSchema } from './contracts'

describe('Staff approval response contracts', () => {
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
