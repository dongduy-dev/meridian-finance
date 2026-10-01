import { describe, expect, it } from 'vitest'
import { reviewHistoryFixture } from '@/test/staff-review-history-fixture'
import { staffReviewHistorySchema } from './contracts'

describe('linked review history contract', () => {
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
