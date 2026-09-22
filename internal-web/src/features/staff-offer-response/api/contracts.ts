import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const money = z.number().finite().nonnegative()

export const offerRepaymentItemSchema = z.object({
  installmentNumber: z.number().int().positive(),
  principalDue: money,
  interestDue: money,
  feeDue: money,
  totalDue: money,
  repaymentTiming: z.string().trim().min(1),
})

export const assistedOfferSchema = z.object({
  approvedOfferId: uuidSchema,
  loanApplicationId: uuidSchema,
  status: z.string().trim().min(1),
  approvedPrincipal: money,
  approvedTermMonths: z.number().int().positive(),
  interestCalculationMethod: z.string().trim().min(1),
  flatMonthlyInterestRate: money,
  totalInterest: money,
  feeAmount: money,
  totalRepaymentAmount: money,
  repaymentMethod: z.string().trim().min(1),
  generatedAt: apiTimestampSchema,
  expiresAt: apiTimestampSchema,
  acceptedAt: apiTimestampSchema.nullable(),
  declinedAt: apiTimestampSchema.nullable(),
  expiredAt: apiTimestampSchema.nullable(),
  availableActions: z.array(z.string()),
  repaymentItems: z.array(offerRepaymentItemSchema),
})

export const actionEvidenceSchema = z.object({
  documentId: uuidSchema,
  documentVersionId: uuidSchema,
  evidenceType: z.string().trim().min(1),
  declaredOfferDecision: z.enum(['ACCEPT', 'DECLINE']).nullable(),
  targetId: uuidSchema,
  targetVersion: z.number().int().positive().nullable(),
  versionNumber: z.number().int().positive(),
  detectedMimeType: z.enum(['application/pdf', 'image/jpeg', 'image/png']),
  byteSize: z.number().int().positive(),
  uploadedAt: apiTimestampSchema,
})

export const assistedOfferResponseCaseSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productCode: z.enum(['UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN']),
  productType: z.string().trim().min(1),
  originationChannel: z.literal('STAFF_ASSISTED'),
  applicationStatus: z.string().trim().min(1),
  submittedAt: apiTimestampSchema,
  approvedOffer: assistedOfferSchema,
  evidence: actionEvidenceSchema.nullable(),
  workState: z.string().trim().min(1),
})

export const uploadedEvidenceVersionSchema = z.object({
  documentVersionId: uuidSchema,
  versionNumber: z.number().int().positive(),
  detectedMimeType: z.string().trim().min(1),
  byteSize: z.number().int().positive(),
  uploadedAt: apiTimestampSchema,
})

export const assistedOfferCommandPayloadSchema = z.object({
  loanApplicationId: uuidSchema,
  expectedApprovedOfferId: uuidSchema,
  action: z.enum(['ACCEPT', 'DECLINE']),
  evidenceDocumentVersionId: uuidSchema,
})

export type AssistedOfferResponseCase = z.infer<typeof assistedOfferResponseCaseSchema>
export type AssistedOfferDecision = 'ACCEPT' | 'DECLINE'
export type AssistedOfferCommandPayload = z.infer<typeof assistedOfferCommandPayloadSchema>
