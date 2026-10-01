import type { StaffVerificationCase } from '@/features/staff-verification/api/contracts'

export function productAssessmentFixture(): Extract<StaffVerificationCase, { productCode: 'UNSECURED_CONSUMER_LOAN' }> {
  const cycle = {
    verificationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', verificationSequence: 2,
    sourceCorrectionRequestId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    productVerificationResult: 'VERIFIED', createdAt: '2026-09-05T08:00:00', reviewedAt: '2026-09-05T09:00:00',
    reviewedBy: { userId: '00000000-0000-0000-0000-000000000302', displayName: 'Assessment Officer', email: 'assessment@meridian.test' },
    assessmentNote: 'Synthetic product assessment evidence.',
  }
  return {
    loanApplicationId: '11111111-1111-4111-8111-111111111111', applicationNumber: 'UCL-20260905-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'UNSECURED', requestedAmount: 20_000_000,
    requestedTermMonths: 12, applicationStatus: 'SUBMITTED', submittedAt: '2026-09-05T07:00:00',
    documentReadiness: { uploadComplete: true, processingReady: true },
    actions: { startAvailable: false, completeAvailable: false }, correctionTargets: [],
    productVerification: { currentCycle: cycle, history: [cycle], collateral: null },
  }
}
