import type { StaffReviewHistory } from '@/features/staff-review-history/api/contracts'

export const historyApplicationId = '11111111-1111-4111-8111-111111111111'
const officer = { userId: '00000000-0000-0000-0000-000000000302', displayName: 'History Loan Officer', email: 'history.officer@meridian.local' }
const approver = { userId: '00000000-0000-0000-0000-000000000303', displayName: 'History Approver', email: 'history.approver@meridian.local' }
const cycleId = '33333333-3333-4333-8333-333333333333'
const recommendationId = '44444444-4444-4444-8444-444444444444'

export function reviewHistoryFixture(): StaffReviewHistory {
  return {
    loanApplicationId: historyApplicationId, applicationNumber: 'UCL-HISTORY', applicationStatus: 'UNDER_REVIEW',
    cycles: [{
      reviewCycleId: cycleId, cycleNumber: 1, assignedLoanOfficer: officer, status: 'COMPLETED',
      startedAt: '2026-09-05T08:00:00', endedAt: '2026-09-05T09:00:00',
      recommendation: { recommendationId, reviewCycleId: cycleId, action: 'RECOMMEND_APPROVAL',
        reason: 'Earlier recommendation rationale.', reasonCode: null, internalNoteReadable: true,
        internalNotes: 'Synthetic recommendation analysis.', recordedBy: officer, submittedAt: '2026-09-05T08:30:00' },
      decision: { decisionId: '55555555-5555-4555-8555-555555555555', reviewRecommendationId: recommendationId,
        action: 'RETURN_TO_LOAN_OFFICER_REVIEW', reason: 'Earlier decision rationale.', reasonCode: null,
        internalNoteReadable: true, internalNotes: 'Synthetic decision analysis.', recordedBy: approver, decidedAt: '2026-09-05T09:00:00' },
    }, {
      reviewCycleId: '66666666-6666-4666-8666-666666666666', cycleNumber: 2, assignedLoanOfficer: null, status: 'SUPERSEDED',
      startedAt: '2026-09-06T08:00:00', endedAt: '2026-09-06T09:00:00',
      recommendation: { recommendationId: '77777777-7777-4777-8777-777777777777',
        reviewCycleId: '66666666-6666-4666-8666-666666666666', action: 'RECOMMEND_APPROVAL',
        reason: null, reasonCode: null, internalNoteReadable: false, recordedBy: null, submittedAt: '2026-09-06T08:30:00' },
      decision: null,
    }, {
      reviewCycleId: '88888888-8888-4888-8888-888888888888', cycleNumber: 3, assignedLoanOfficer: officer, status: 'ACTIVE',
      startedAt: '2026-09-07T08:00:00', endedAt: null, recommendation: null, decision: null,
    }],
  }
}
