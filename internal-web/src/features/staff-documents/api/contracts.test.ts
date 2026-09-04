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
})
