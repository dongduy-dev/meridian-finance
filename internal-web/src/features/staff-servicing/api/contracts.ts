import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)
const moneySchema = z.number().finite().nonnegative()
const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)
const nullableDateSchema = dateSchema.nullable()
const nullableTimestampSchema = apiTimestampSchema.nullable()

const servicingSummarySchema = z.object({
  principalPaid: moneySchema,
  interestPaid: moneySchema,
  feePaid: moneySchema,
  totalPaid: moneySchema,
  principalOutstanding: moneySchema,
  interestOutstanding: moneySchema,
  feeOutstanding: moneySchema,
  totalOutstanding: moneySchema,
  servicingEvaluationDate: dateSchema,
  lastPaymentValueDate: nullableDateSchema,
  lastPaymentRecordedAt: nullableTimestampSchema,
})

const installmentServicingSchema = servicingSummarySchema.omit({ servicingEvaluationDate: true }).extend({
  status: rawValue,
  statusEvaluationDate: dateSchema,
})

const scheduleItemSchema = z.object({
  installmentNumber: z.number().int().positive(),
  dueDate: dateSchema,
  principalDue: moneySchema,
  interestDue: moneySchema,
  feeDue: moneySchema,
  totalDue: moneySchema,
  servicing: installmentServicingSchema,
})

export const loanAccountSchema = z.object({
  loanApplicationId: uuidSchema,
  loanAccountId: uuidSchema,
  accountNumber: rawValue,
  status: rawValue,
  activatedAt: apiTimestampSchema,
  originatedPrincipal: moneySchema,
  approvedTermMonths: z.number().int().positive(),
  totalInterest: moneySchema,
  totalFee: moneySchema,
  totalRepayment: moneySchema,
  servicing: servicingSummarySchema,
  disbursementDestination: z.object({
    bankCode: rawValue,
    bankName: rawValue,
    accountHolderName: rawValue,
    maskedAccountNumber: z.string().regex(/^\*+$/),
  }),
  finalRepaymentSchedule: z.object({
    scheduleId: uuidSchema,
    scheduleType: rawValue,
    version: z.number().int().positive(),
    firstDueDate: dateSchema,
    lastDueDate: dateSchema,
    items: z.array(scheduleItemSchema),
  }),
})

const servicingQueueItemSchema = z.object({
  loanApplicationId: uuidSchema,
  loanAccountId: uuidSchema,
  applicationNumber: rawValue,
  accountNumber: rawValue,
  productCode: rawValue,
  productType: rawValue,
  accountStatus: rawValue,
  activatedAt: apiTimestampSchema,
  originatedPrincipal: moneySchema,
  totalPaid: moneySchema,
  totalOutstanding: moneySchema.positive(),
  servicingEvaluationDate: dateSchema,
  lastPaymentValueDate: nullableDateSchema,
  lastPaymentRecordedAt: nullableTimestampSchema,
})

const settlementWorkItemSchema = servicingQueueItemSchema.omit({ originatedPrincipal: true })

const closureWorkItemSchema = settlementWorkItemSchema.extend({
  totalOutstanding: z.literal(0),
  accountStatus: z.literal('SETTLED'),
  payoffProvenance: z.enum(['CONTRACTUAL_PAYOFF', 'APPROVED_SETTLEMENT']),
})

export const staffServicingWorkPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(servicingQueueItemSchema),
})

export const staffSettlementWorkPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(settlementWorkItemSchema),
})

export const staffClosureWorkPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(closureWorkItemSchema),
})

const allocationSchema = z.object({
  sequence: z.number().int().positive(),
  repaymentScheduleItemId: uuidSchema,
  installmentNumber: z.number().int().positive(),
  component: rawValue,
  allocatedAmount: moneySchema,
})

const installmentOutcomeSchema = z.object({
  repaymentScheduleItemId: uuidSchema,
  installmentNumber: z.number().int().positive(),
  dueDate: dateSchema,
  previousStatus: rawValue,
  resultingStatus: rawValue,
  evaluationDate: dateSchema,
  principalPaid: moneySchema,
  interestPaid: moneySchema,
  feePaid: moneySchema,
  totalPaid: moneySchema,
  principalOutstanding: moneySchema,
  interestOutstanding: moneySchema,
  feeOutstanding: moneySchema,
  totalOutstanding: moneySchema,
  lastPaymentValueDate: nullableDateSchema,
  lastPaymentRecordedAt: nullableTimestampSchema,
  statusChanged: z.boolean(),
})

const accountBalanceSchema = servicingSummarySchema.extend({ status: rawValue })

const repaymentOutcomeFields = {
  repaymentTransactionId: uuidSchema,
  receivedAmount: moneySchema.positive(),
  paymentValueDate: dateSchema,
  recordedAt: apiTimestampSchema,
  principalAllocated: moneySchema,
  principalReleased: moneySchema,
  resultingLoanAccountStatus: rawValue,
  accountBalance: accountBalanceSchema,
  allocations: z.array(allocationSchema),
  affectedInstallments: z.array(installmentOutcomeSchema),
}

export const recordRepaymentResultSchema = z.object({
  loanApplicationId: uuidSchema,
  loanAccountId: uuidSchema,
  finalScheduleId: uuidSchema,
  ...repaymentOutcomeFields,
  idempotentReplay: z.boolean(),
})

export const approvedSettlementResultSchema = z.object({
  loanApplicationId: uuidSchema,
  loanAccountId: uuidSchema,
  repaymentTransactionId: uuidSchema,
  finalScheduleId: uuidSchema,
  settlementAmount: moneySchema.positive(),
  paymentValueDate: dateSchema,
  approvedAt: apiTimestampSchema,
  principalAllocated: moneySchema,
  principalReleased: moneySchema,
  resultingLoanAccountStatus: z.literal('SETTLED'),
  accountBalance: accountBalanceSchema.extend({ status: z.literal('SETTLED') }),
  idempotentReplay: z.boolean(),
})

export const approvedSettlementEvidenceSchema = z.object({
  loanApplicationId: uuidSchema,
  loanAccountId: uuidSchema,
  settlementAmount: moneySchema.positive(),
  paymentValueDate: dateSchema,
  approvedAt: apiTimestampSchema,
})

export const closedLoanAccountResultSchema = z.object({
  loanApplicationId: uuidSchema,
  loanAccountId: uuidSchema,
  resultingStatus: z.literal('CLOSED'),
  closedAt: apiTimestampSchema,
  idempotentReplay: z.boolean(),
})

export const repaymentHistoryPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(z.object(repaymentOutcomeFields)),
})

export const repaymentSemanticPayloadSchema = z.object({
  loanApplicationId: uuidSchema,
  externalPaymentReference: z.string().min(1),
  amount: z.number().int().positive(),
  paymentValueDate: dateSchema,
})

export type LoanAccount = z.infer<typeof loanAccountSchema>
export type StaffServicingWorkPage = z.infer<typeof staffServicingWorkPageSchema>
export type StaffSettlementWorkPage = z.infer<typeof staffSettlementWorkPageSchema>
export type StaffClosureWorkPage = z.infer<typeof staffClosureWorkPageSchema>
export type StaffServicingWorkFilters = {
  productCode?: string
  accountStatus?: 'ACTIVE' | 'OVERDUE'
  page: number
  size: number
}
export type StaffTerminalWorkFilters = { productCode?: string; page: number; size: number }
export type RepaymentHistoryPage = z.infer<typeof repaymentHistoryPageSchema>
export type RecordRepaymentResult = z.infer<typeof recordRepaymentResultSchema>
export type RepaymentSemanticPayload = z.infer<typeof repaymentSemanticPayloadSchema>
export type RecordRepaymentRequest = Omit<RepaymentSemanticPayload, 'loanApplicationId'> & { requestId: string }
export type SettlementSemanticPayload = {
  loanApplicationId: string
  externalPaymentReference: string
  expectedSettlementAmount: number
  paymentValueDate: string
}
export type ApproveSettlementRequest = Omit<SettlementSemanticPayload, 'loanApplicationId'> & { requestId: string }
export type ApprovedSettlementResult = z.infer<typeof approvedSettlementResultSchema>
export type ApprovedSettlementEvidence = z.infer<typeof approvedSettlementEvidenceSchema>
export type ClosedLoanAccountResult = z.infer<typeof closedLoanAccountResultSchema>
