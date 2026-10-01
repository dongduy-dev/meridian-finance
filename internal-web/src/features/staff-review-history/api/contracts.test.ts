import { describe, expect, it } from 'vitest'
import { reviewHistoryFixture } from '@/test/staff-review-history-fixture'
import { staffReviewHistorySchema } from './contracts'

describe('linked review history contract', () => {
  it('accepts deterministic Meridian Staff IDs for assignments and recorded actors', () => {
    const first = staffReviewHistorySchema.parse(reviewHistoryFixture()).cycles[0]!
    expect(first.assignedLoanOfficer?.userId).toBe('00000000-0000-0000-0000-000000000302')
    expect(first.recommendation?.recordedBy?.userId).toBe('00000000-0000-0000-0000-000000000302')
    expect(first.decision?.recordedBy?.userId).toBe('00000000-0000-0000-0000-000000000303')
  })

  it.each([
    '00000000-0000-0000-0000-00000000030g',
    '00000000-0000-0000-0000-00000000030',
    '00000000000000000000000000000302',
    ' 00000000-0000-0000-0000-000000000302',
  ])('rejects malformed canonical Staff UUID text %s', (userId) => {
    for (const actor of ['assignment', 'recommendation', 'decision']) {
      const value = structuredClone(reviewHistoryFixture())
      const first = value.cycles[0]!
      const target = actor === 'assignment' ? first.assignedLoanOfficer
        : actor === 'recommendation' ? first.recommendation!.recordedBy : first.decision!.recordedBy
      target!.userId = userId
      expect(staffReviewHistorySchema.safeParse(value).success).toBe(false)
    }
  })

  it.each(['loanApplicationId', 'reviewCycleId', 'recommendationId', 'decisionId', 'reviewRecommendationId'])(
    'keeps generated %s strict when Staff IDs are canonical', (field) => {
      const value = reviewHistoryFixture()
      const first = value.cycles[0]!
      const seededId = '00000000-0000-0000-0000-000000000302'
      if (field === 'loanApplicationId') value.loanApplicationId = seededId
      if (field === 'reviewCycleId') {
        first.reviewCycleId = seededId
        first.recommendation!.reviewCycleId = seededId
      }
      if (field === 'recommendationId' || field === 'reviewRecommendationId') {
        first.recommendation!.recommendationId = seededId
        first.decision!.reviewRecommendationId = seededId
      }
      if (field === 'decisionId') first.decision!.decisionId = seededId
      const result = staffReviewHistorySchema.safeParse(value)
      expect(result.success).toBe(false)
      if (!result.success) expect(result.error.issues.some((issue) => issue.path.at(-1) === field)).toBe(true)
    },
  )

  it('preserves actual numbering, partial cycles, restricted fields and unavailable actors', () => {
    const value = reviewHistoryFixture()
    value.cycles[2]!.cycleNumber = 5
    expect(staffReviewHistorySchema.parse(value)).toEqual(value)
  })

  it.each(['order', 'cycle-link', 'recommendation-link', 'duplicate-cycle', 'denied-note'])(
    'rejects inconsistent %s evidence instead of rendering or guessing', (kind) => {
      const value = reviewHistoryFixture()
      if (kind === 'order') value.cycles.reverse()
      if (kind === 'cycle-link') value.cycles[0]!.recommendation!.reviewCycleId = value.cycles[1]!.reviewCycleId
      if (kind === 'recommendation-link') value.cycles[0]!.decision!.reviewRecommendationId = value.cycles[1]!.recommendation!.recommendationId
      if (kind === 'duplicate-cycle') value.cycles[1]!.reviewCycleId = value.cycles[0]!.reviewCycleId
      if (kind === 'denied-note') value.cycles[0]!.recommendation!.internalNoteReadable = false
      expect(staffReviewHistorySchema.safeParse(value).success).toBe(false)
    },
  )
})
