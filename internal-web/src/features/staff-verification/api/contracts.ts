import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)
const money = z.number().finite().nonnegative()

const documentReadinessSchema = z.object({
  uploadComplete: z.boolean(),
  processingReady: z.boolean(),
})

const actionsSchema = z.object({
  startAvailable: z.boolean(),
  completeAvailable: z.boolean(),
})

const cycleSchema = z.object({
  verificationId: uuidSchema,
  verificationSequence: z.number().int().positive(),
  productVerificationResult: rawValue,
  createdAt: apiTimestampSchema,
  reviewedAt: apiTimestampSchema.nullable(),
})

const correctionTargetSchema = z.object({
  checklistItemId: uuidSchema,
  documentType: rawValue,
  requirementStatus: rawValue,
  currentDocumentVersionId: uuidSchema,
})

const common = {
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productType: rawValue,
  requestedAmount: money,
  requestedTermMonths: z.number().int().positive(),
  applicationStatus: rawValue,
  submittedAt: apiTimestampSchema,
  documentReadiness: documentReadinessSchema,
  actions: actionsSchema,
  correctionTargets: z.array(correctionTargetSchema).max(10),
}

const salaryAdvanceVerificationSchema = z.object({
  verificationSequence: z.number().int().positive(),
  employeeVerificationOutcome: rawValue,
  productVerificationResult: rawValue,
  totalLimitSnapshot: money,
  usedAmountSnapshot: money,
  reservedAmountSnapshot: money,
  availableLimitSnapshot: money,
  verifiedAt: apiTimestampSchema,
})

const collateralSchema = z.object({
  collateralType: rawValue,
  description: z.string().trim().min(1),
  estimatedValue: money,
  ownershipStatus: z.string().trim().min(1),
  conditionNote: z.string().trim().min(1),
})

const manualVerificationSchema = z.object({
  currentCycle: cycleSchema,
  history: z.array(cycleSchema).min(1),
  collateral: collateralSchema.nullable(),
})

export const staffVerificationCaseSchema = z.discriminatedUnion('productCode', [
  z.object({
    ...common,
    productCode: z.literal('SALARY_ADVANCE'),
    productVerification: salaryAdvanceVerificationSchema,
  }),
  z.object({
    ...common,
    productCode: z.literal('UNSECURED_CONSUMER_LOAN'),
    productVerification: manualVerificationSchema.extend({ collateral: z.null() }),
  }),
  z.object({
    ...common,
    productCode: z.literal('COLLATERAL_LOAN'),
    productVerification: manualVerificationSchema.extend({ collateral: collateralSchema }),
  }),
])

export const verificationOutcomeSchema = z.enum([
  'VERIFIED',
  'FAILED',
  'REQUIRES_MORE_INFORMATION',
])

export type StaffVerificationCase = z.infer<typeof staffVerificationCaseSchema>
export type ManualVerificationCase = Extract<StaffVerificationCase, {
  productCode: 'UNSECURED_CONSUMER_LOAN' | 'COLLATERAL_LOAN'
}>
export type VerificationOutcome = z.infer<typeof verificationOutcomeSchema>
export type CorrectionTarget = z.infer<typeof correctionTargetSchema>

export type CorrectionTaskInput = {
  targetId: string
  scope: 'DOCUMENT_REPLACEMENT' | 'DOCUMENT_REVIEW'
  instruction: string
}

export type CompleteVerificationInput = {
  expectedVerificationId?: string
  outcome: VerificationOutcome
  assessmentNote: string
  reasonCode?: 'SUPPORTING_DOCUMENT_REQUIRED' | 'DOCUMENT_REPLACEMENT_REQUIRED' | 'DOCUMENT_REVIEW_REQUIRED'
  tasks?: CorrectionTaskInput[]
}
