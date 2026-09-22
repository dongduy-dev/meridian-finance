import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)
const moneySchema = z.number().finite().nonnegative()

export const assistedActionEvidenceSchema = z.object({
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

export const uploadedEvidenceVersionSchema = z.object({
  documentVersionId: uuidSchema,
  versionNumber: z.number().int().positive(),
  detectedMimeType: z.string().trim().min(1),
  byteSize: z.number().int().positive(),
  uploadedAt: apiTimestampSchema,
})

export const contractRepaymentItemSchema = z.object({
  installmentNumber: z.number().int().positive(),
  principalDue: moneySchema,
  interestDue: moneySchema,
  feeDue: moneySchema,
  totalDue: moneySchema,
})

export const loanContractSchema = z.object({
  contractId: uuidSchema,
  contractReference: z.string().trim().min(1),
  contractVersion: z.number().int().positive(),
  status: rawValue,
  approvedPrincipal: moneySchema,
  approvedTermMonths: z.number().int().positive(),
  interestCalculationMethod: rawValue,
  flatMonthlyInterestRate: moneySchema,
  totalInterest: moneySchema,
  feeAmount: moneySchema,
  totalRepaymentAmount: moneySchema,
  repaymentMethod: rawValue,
  repaymentPreview: z.array(contractRepaymentItemSchema),
  disbursementBankAccount: z.object({
    bankCode: z.string().trim().min(1),
    bankNameSnapshot: z.string().trim().min(1),
    accountHolderName: z.string().trim().min(1),
    maskedAccountNumber: z.string().trim().regex(/^\*{4}\S+$/),
    primaryAtCapture: z.boolean(),
    activeAtCapture: z.boolean(),
    capturedAt: apiTimestampSchema,
  }),
  preparedAt: apiTimestampSchema,
  acknowledgedAt: apiTimestampSchema.nullable(),
  readinessConfirmedAt: apiTimestampSchema.nullable(),
  availableCustomerAction: rawValue.nullable(),
})

export const contractReadinessSchema = z.object({
  loanApplicationId: uuidSchema,
  contractId: uuidSchema.nullable(),
  contractVersion: z.number().int().positive().nullable(),
  ready: z.boolean(),
  blockerCodes: z.array(rawValue),
  calculationSemantics: rawValue,
  recomputedDuringConfirmation: z.boolean(),
})

const applicationHeaderShape = {
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productCode: rawValue,
  productType: rawValue,
  originationChannel: z.enum(['CUSTOMER_DIGITAL', 'STAFF_ASSISTED']),
  requestedAmount: moneySchema,
  requestedTermMonths: z.number().int().positive(),
  applicationStatus: rawValue,
  submittedAt: apiTimestampSchema,
  currentContract: loanContractSchema.nullable(),
  readiness: contractReadinessSchema,
  workStage: rawValue,
}

export const staffContractCaseSchema = z.object({
  ...applicationHeaderShape,
  assistedAcknowledgmentEvidence: assistedActionEvidenceSchema.nullable(),
})

export const staffContractWorkPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(z.object(applicationHeaderShape)),
})

export const preparationSemanticPayloadSchema = z.object({
  loanApplicationId: uuidSchema,
  expectedCurrentContractVersion: z.number().int().nonnegative(),
  supersessionReasonCode: z.literal('DISBURSEMENT_ACCOUNT_REFRESH').nullable(),
})

export const readinessConfirmationSemanticPayloadSchema = z.object({
  loanApplicationId: uuidSchema,
  expectedContractVersion: z.number().int().positive(),
})

export const assistedAcknowledgmentSemanticPayloadSchema = z.object({
  loanApplicationId: uuidSchema,
  contractId: uuidSchema,
  expectedContractVersion: z.number().int().positive(),
  evidenceDocumentVersionId: uuidSchema,
})

export type StaffContractCase = z.infer<typeof staffContractCaseSchema>
export type StaffContractWorkPage = z.infer<typeof staffContractWorkPageSchema>
export type StaffContractWorkFilters = { productCode?: string; page: number; size: number }
export type LoanContract = z.infer<typeof loanContractSchema>
export type PreparationSemanticPayload = z.infer<typeof preparationSemanticPayloadSchema>
export type ReadinessConfirmationSemanticPayload = z.infer<typeof readinessConfirmationSemanticPayloadSchema>
export type AssistedAcknowledgmentSemanticPayload = z.infer<typeof assistedAcknowledgmentSemanticPayloadSchema>
export type PrepareLoanContractRequest = {
  preparationRequestId: string
  expectedCurrentContractVersion: number
  supersessionReasonCode: 'DISBURSEMENT_ACCOUNT_REFRESH' | null
}
export type ConfirmContractReadinessRequest = {
  confirmationRequestId: string
  expectedContractVersion: number
}
