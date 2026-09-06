import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)

export const staffReviewCaseSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productCode: rawValue,
  productType: rawValue,
  requestedAmount: z.number().finite().nonnegative(),
  requestedTermMonths: z.number().int().positive(),
  applicationStatus: rawValue,
  submittedAt: apiTimestampSchema,
  documentReadiness: z.object({
    uploadComplete: z.boolean(),
    processingReady: z.boolean(),
  }),
  productReadiness: z.object({
    productVerificationResult: rawValue,
    readyForReview: z.boolean(),
  }),
  reviewStartAvailable: z.boolean(),
  currentReviewCycle: z.object({
    reviewCycleId: uuidSchema,
    cycleNumber: z.number().int().positive(),
    status: rawValue,
    startedAt: apiTimestampSchema,
    endedAt: apiTimestampSchema.nullable(),
  }).nullable(),
})

export type StaffReviewCase = z.infer<typeof staffReviewCaseSchema>

export const recommendationActions = [
  'RECOMMEND_APPROVAL',
  'RECOMMEND_REJECTION',
  'RETURN_TO_CUSTOMER_REVISION',
  'REQUEST_STAFF_CORRECTION',
] as const
export type RecommendationAction = (typeof recommendationActions)[number]

export const correctionScopes = [
  'SUPPORTING_DOCUMENT_UPLOAD',
  'DOCUMENT_REPLACEMENT',
  'DOCUMENT_REVIEW',
] as const
export type CorrectionScope = (typeof correctionScopes)[number]
export type CorrectionResponsibility = 'CUSTOMER' | 'STAFF'

const reviewCycleSchema = z.object({
  reviewCycleId: uuidSchema,
  cycleNumber: z.number().int().positive(),
  status: rawValue,
  startedAt: apiTimestampSchema,
  endedAt: apiTimestampSchema.nullable(),
})

export const correctionOptionSchema = z.object({
  documentType: rawValue,
  checklistItemId: uuidSchema.nullable(),
  currentDocumentVersionId: uuidSchema.nullable(),
  allowedScopes: z.array(rawValue),
})

export const staffRecommendationCaseSchema = z.object({
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
    currentReviewCycle: reviewCycleSchema.nullable(),
  }),
  recommendation: z.object({
    recommendationId: uuidSchema,
    reviewCycleId: uuidSchema,
    action: rawValue,
    reason: z.string().nullable(),
    reasonCode: rawValue.nullable(),
    submittedAt: apiTimestampSchema,
  }).nullable(),
  recommendationAvailable: z.boolean(),
  correctionReasonCodes: z.array(rawValue),
  correctionOptions: z.array(correctionOptionSchema),
})

export type StaffRecommendationCase = z.infer<typeof staffRecommendationCaseSchema>
export type CorrectionOption = z.infer<typeof correctionOptionSchema>

export type CorrectionTaskInput = {
  optionIndex: number
  scope: CorrectionScope
  responsibleParty: CorrectionResponsibility
  instruction: string
}

export type CorrectionTaskRequest = {
  scope: CorrectionScope
  responsibleParty: CorrectionResponsibility
  documentType: string
  createChecklistItem: boolean
  checklistItemId: string | null
  baselineDocumentVersionId: string | null
  customerInstruction: string | null
  staffInstruction: string | null
}

export type RecommendationRequest = {
  action: RecommendationAction
  reason: string | null
  internalNotes: string | null
  expectedReviewCycleId: string
  reasonCode: string | null
  correctionPlan: { tasks: CorrectionTaskRequest[] } | null
}
