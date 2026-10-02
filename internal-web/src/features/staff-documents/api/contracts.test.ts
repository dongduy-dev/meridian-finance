import { describe, expect, it } from 'vitest'
import { staffDocumentChecklistSchema } from './contracts'

const valid = {
  loanApplicationId: '11111111-1111-4111-8111-111111111111',
  applicationStatus: 'SUBMITTED',
  checklistStage: 'SUBMISSION',
  uploadComplete: true,
  processingReady: false,
  items: [{
    checklistItemId: '22222222-2222-4222-8222-222222222222',
    documentType: 'BANK_STATEMENT',
    requirementStatus: 'REQUIRED',
    evidenceStatus: 'FUTURE_STATUS',
    uploadComplete: true,
    processingReady: false,
    currentVersion: null,
    versionHistory: [],
    reviewHistory: [],
  }],
}

describe('Staff document contracts', () => {
  it('accepts structurally valid future status values', () => {
    expect(staffDocumentChecklistSchema.parse(valid).items[0]?.evidenceStatus).toBe('FUTURE_STATUS')
  })

  it('fails closed for malformed structural evidence', () => {
    expect(staffDocumentChecklistSchema.safeParse({ ...valid, loanApplicationId: 'unsafe' }).success).toBe(false)
  })

  it('accepts seeded Staff reviewer IDs but rejects them as runtime document IDs and rejects unauthorized notes', () => {
    const review = {
      reviewDecisionId: '33333333-3333-4333-8333-333333333333', documentVersionId: '44444444-4444-4444-8444-444444444444',
      outcome: 'FUTURE_OUTCOME', waiverReasonCode: null, correctionReasonCode: null, customerInstruction: null,
      reviewer: { userId: '00000000-0000-0000-0000-000000000302', displayName: 'Staff reviewer', email: 'reviewer@meridian.local' },
      decidedAt: '2026-10-02T10:00:00', restrictedStaffNoteReadable: true, restrictedStaffNotes: 'Restricted assessment',
    }
    const payload = { ...valid, items: [{ ...valid.items[0], reviewHistory: [review] }] }
    expect(staffDocumentChecklistSchema.parse(payload).items[0]?.reviewHistory[0]?.reviewer?.userId).toBe(review.reviewer.userId)
    expect(staffDocumentChecklistSchema.safeParse({ ...payload, loanApplicationId: review.reviewer.userId }).success).toBe(false)
    expect(staffDocumentChecklistSchema.safeParse({ ...valid, items: [{ ...valid.items[0], reviewHistory: [{ ...review, restrictedStaffNoteReadable: false }] }] }).success).toBe(false)
  })
})
