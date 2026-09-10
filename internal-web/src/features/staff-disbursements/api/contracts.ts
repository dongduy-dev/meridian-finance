import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'
import { loanContractSchema } from '@/features/staff-contracts/api/contracts'

const rawValue = z.string().trim().min(1)
const moneySchema = z.number().finite().nonnegative()
const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)

const destinationSummarySchema = z.object({
  bankCode: rawValue,
  bankName: rawValue,
  accountHolderName: rawValue,
  maskedAccountNumber: z.string().trim().regex(/^\*{4}\S+$/),
})

const disbursementContractSummarySchema = z.object({
  contractId: uuidSchema,
  contractReference: rawValue,
  contractVersion: z.number().int().positive(),
  status: rawValue,
  approvedPrincipal: moneySchema,
  approvedTermMonths: z.number().int().positive(),
  repaymentMethod: rawValue,
  readinessConfirmedAt: apiTimestampSchema,
  disbursementDestination: destinationSummarySchema,
})

const activationScheduleItemSchema = z.object({
  installmentNumber: z.number().int().positive(),
  dueDate: dateSchema,
  principalDue: moneySchema,
  interestDue: moneySchema,
  feeDue: moneySchema,
  totalDue: moneySchema,
})

export const disbursementActivationSchema = z.object({
  loanAccountId: uuidSchema,
  loanAccountNumber: rawValue,
  loanAccountStatus: rawValue,
  activatedAt: apiTimestampSchema,
  disbursedAmount: moneySchema,
  disbursementValueDate: dateSchema,
  firstRepaymentDate: dateSchema,
  repaymentScheduleId: uuidSchema,
  scheduleType: rawValue,
  scheduleVersion: z.number().int().positive(),
  scheduleItems: z.array(activationScheduleItemSchema),
})

const queueItemSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: rawValue,
  productCode: rawValue,
  productType: rawValue,
  requestedAmount: moneySchema,
  requestedTermMonths: z.number().int().positive(),
  applicationStatus: rawValue,
  submittedAt: apiTimestampSchema,
  currentContract: disbursementContractSummarySchema,
  workStage: rawValue,
})

export const staffDisbursementWorkPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(queueItemSchema),
})

export const staffDisbursementCaseSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: rawValue,
  productCode: rawValue,
  productType: rawValue,
  requestedAmount: moneySchema,
  requestedTermMonths: z.number().int().positive(),
  applicationStatus: rawValue,
  submittedAt: apiTimestampSchema,
  currentContract: loanContractSchema,
  activation: disbursementActivationSchema.nullable(),
  workStage: rawValue,
})

export const disbursementDestinationRevealSchema = z.object({
  contractId: uuidSchema,
  contractVersion: z.number().int().positive(),
  bankCode: rawValue,
  bankName: rawValue,
  accountHolderName: rawValue,
  accountNumber: rawValue,
})

export const manualDisbursementConfirmationSchema = disbursementActivationSchema.extend({
  loanApplicationId: uuidSchema,
  applicationStatus: rawValue,
  manualDisbursementId: uuidSchema,
  idempotentReplay: z.boolean(),
})

export const disbursementSemanticPayloadSchema = z.object({
  loanApplicationId: uuidSchema,
  expectedContractVersion: z.number().int().positive(),
  externalTransferReference: z.string().min(1),
  disbursementValueDate: dateSchema,
  firstRepaymentDate: dateSchema,
})

export type StaffDisbursementWorkPage = z.infer<typeof staffDisbursementWorkPageSchema>
export type StaffDisbursementCase = z.infer<typeof staffDisbursementCaseSchema>
export type StaffDisbursementWorkFilters = { productCode?: string; page: number; size: number }
export type DisbursementDestinationReveal = z.infer<typeof disbursementDestinationRevealSchema>
export type ManualDisbursementConfirmation = z.infer<typeof manualDisbursementConfirmationSchema>
export type DisbursementSemanticPayload = z.infer<typeof disbursementSemanticPayloadSchema>
export type ConfirmManualDisbursementRequest = {
  requestId: string
  expectedContractVersion: number
  externalTransferReference: string
  disbursementValueDate: string
  firstRepaymentDate: string
}
