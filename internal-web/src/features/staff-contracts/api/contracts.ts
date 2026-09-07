import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)
const moneySchema = z.number().finite().nonnegative()

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
    bankName: z.string().trim().min(1),
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
  requestedAmount: moneySchema,
  requestedTermMonths: z.number().int().positive(),
  applicationStatus: rawValue,
  submittedAt: apiTimestampSchema,
  currentContract: loanContractSchema.nullable(),
  readiness: contractReadinessSchema,
  workStage: rawValue,
}

export const staffContractCaseSchema = z.object(applicationHeaderShape)

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

export type StaffContractCase = z.infer<typeof staffContractCaseSchema>
export type StaffContractWorkPage = z.infer<typeof staffContractWorkPageSchema>
export type StaffContractWorkFilters = { productCode?: string; page: number; size: number }
export type LoanContract = z.infer<typeof loanContractSchema>
export type PreparationSemanticPayload = z.infer<typeof preparationSemanticPayloadSchema>
export type ReadinessConfirmationSemanticPayload = z.infer<typeof readinessConfirmationSemanticPayloadSchema>
export type PrepareLoanContractRequest = {
  preparationRequestId: string
  expectedCurrentContractVersion: number
  supersessionReasonCode: 'DISBURSEMENT_ACCOUNT_REFRESH' | null
}
export type ConfirmContractReadinessRequest = {
  confirmationRequestId: string
  expectedContractVersion: number
}
