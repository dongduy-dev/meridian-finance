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
