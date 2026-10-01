import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

const token = z.string().trim().min(1)
const actor = z.object({ userId: meridianUuidSchema, displayName: token, email: z.email() })
const creditContext = {
  action: token,
  reason: z.string().nullable(),
  reasonCode: token.nullable(),
  internalNoteReadable: z.boolean(),
  internalNotes: z.string().optional(),
  recordedBy: actor.nullable(),
}

export const staffReviewHistorySchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: token,
  applicationStatus: token,
  cycles: z.array(z.object({
    reviewCycleId: uuidSchema,
    cycleNumber: z.number().int().positive(),
    assignedLoanOfficer: actor.nullable(),
    status: token,
    startedAt: apiTimestampSchema,
    endedAt: apiTimestampSchema.nullable(),
    recommendation: z.object({ ...creditContext, recommendationId: uuidSchema,
      reviewCycleId: uuidSchema, submittedAt: apiTimestampSchema }).nullable(),
    decision: z.object({ ...creditContext, decisionId: uuidSchema,
      reviewRecommendationId: uuidSchema, decidedAt: apiTimestampSchema }).nullable(),
  })),
}).superRefine((history, context) => {
  let previous = 0
  const ids = new Set<string>()
  for (const cycle of history.cycles) {
    if (cycle.cycleNumber <= previous || ids.has(cycle.reviewCycleId)
      || (cycle.recommendation && cycle.recommendation.reviewCycleId !== cycle.reviewCycleId)
      || (cycle.decision && (!cycle.recommendation
        || cycle.decision.reviewRecommendationId !== cycle.recommendation.recommendationId))
      || [cycle.recommendation, cycle.decision].some((action) => action
        && !action.internalNoteReadable && action.internalNotes !== undefined)) {
      context.addIssue({ code: 'custom', message: 'Review history evidence is inconsistent.' })
    }
    previous = cycle.cycleNumber
    ids.add(cycle.reviewCycleId)
  }
})

export type StaffReviewHistory = z.infer<typeof staffReviewHistorySchema>
