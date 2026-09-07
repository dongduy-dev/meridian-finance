import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'
import { correctionOptionSchema, type CorrectionTaskRequest } from '@/features/staff-review/api/contracts'

const rawValue = z.string().trim().min(1)
const recommendationSchema = z.object({
  recommendationId: uuidSchema,
  reviewCycleId: uuidSchema,
  action: rawValue,
  reason: z.string().nullable(),
  reasonCode: rawValue.nullable(),
  submittedAt: apiTimestampSchema,
})
const decisionSchema = z.object({
  decisionId: uuidSchema,
  reviewRecommendationId: uuidSchema,
  action: rawValue,
  reason: z.string().nullable(),
  reasonCode: rawValue.nullable(),
  decidedAt: apiTimestampSchema,
})

export const staffDecisionCaseSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productCode: rawValue,
  productType: rawValue,
  requestedAmount: z.number().finite().nonnegative(),
  requestedTermMonths: z.number().int().positive(),
  applicationStatus: rawValue,
  submittedAt: apiTimestampSchema,
  evidence: z.object({
    uploadComplete: z.boolean(),
    processingReady: z.boolean(),
    productVerificationResult: rawValue,
    readyForDecision: z.boolean(),
    currentReviewCycle: z.object({
      reviewCycleId: uuidSchema,
      cycleNumber: z.number().int().positive(),
      status: rawValue,
      startedAt: apiTimestampSchema,
      endedAt: apiTimestampSchema.nullable(),
    }).nullable(),
  }),
  recommendation: recommendationSchema.nullable(),
  makerCheckerEligible: z.boolean(),
  decisionAvailable: z.boolean(),
  latestDecision: decisionSchema.nullable(),
  decisionHistory: z.array(decisionSchema),
  correctionReasonCodes: z.array(rawValue),
  correctionOptions: z.array(correctionOptionSchema),
})

export const staffApprovalQueuePageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(z.object({
    loanApplicationId: uuidSchema,
    applicationNumber: z.string().trim().min(1),
    productCode: rawValue,
    productType: rawValue,
    requestedAmount: z.number().finite().nonnegative(),
    requestedTermMonths: z.number().int().positive(),
    applicationStatus: rawValue,
    submittedAt: apiTimestampSchema,
    recommendationId: uuidSchema,
    recommendationAction: rawValue,
    recommendationSubmittedAt: apiTimestampSchema,
    makerCheckerEligible: z.boolean(),
    decisionAvailable: z.boolean(),
  })),
})

export const decisionActions = [
  'APPROVE',
  'REJECT',
  'RETURN_TO_LOAN_OFFICER_REVIEW',
  'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
] as const

export type DecisionAction = (typeof decisionActions)[number]
export type StaffDecisionCase = z.infer<typeof staffDecisionCaseSchema>
export type StaffApprovalQueuePage = z.infer<typeof staffApprovalQueuePageSchema>
export type StaffApprovalQueueFilters = { productCode?: string; page: number; size: number }
export type ApprovalDecisionRequest = {
  action: DecisionAction
  reason: string | null
  internalNotes: string | null
  expectedReviewRecommendationId: string
  expectedReviewCycleId: string
  reasonCode: string | null
  correctionPlan: { tasks: CorrectionTaskRequest[] } | null
}
