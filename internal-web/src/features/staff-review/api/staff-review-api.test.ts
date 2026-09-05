import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getStaffReviewCase, startReview } from './staff-review-api'

const applicationId = '11111111-1111-4111-8111-111111111111'

describe('staff review API', () => {
  it('uses the purpose-limited read and the existing review-start command without a synthetic idempotency key', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({
      loanApplicationId: applicationId, applicationNumber: 'UCL-20260905-000001',
      productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'PERSONAL', requestedAmount: 20_000_000,
      requestedTermMonths: 12, applicationStatus: 'VERIFIED', submittedAt: '2026-09-05T08:00:00',
      documentReadiness: { uploadComplete: true, processingReady: true },
      productReadiness: { productVerificationResult: 'VERIFIED', readyForReview: true },
      reviewStartAvailable: true, currentReviewCycle: null,
    })
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await getStaffReviewCase(manager, applicationId)
    await startReview(manager, applicationId)

    expect(protectedRequest).toHaveBeenNthCalledWith(1, `/staff/loan-applications/${applicationId}/review`)
    expect(protectedRequest).toHaveBeenNthCalledWith(2, `/loan-applications/${applicationId}/review/start`, { method: 'POST' })
  })
})
