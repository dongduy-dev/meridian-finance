import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const javaUuid = z.string().guid()
const money = z.number().finite().nonnegative()
const dateOnly = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)
const timestamp = z.string().min(1)
const nullableDateOnly = dateOnly.nullable()
const nullableTimestamp = timestamp.nullable()

export const customerLoanAccountSummarySchema = z.object({
  loanApplicationId: z.string().uuid(),
  loanAccountId: z.string().uuid(),
  accountNumber: z.string().min(1),
  applicationNumber: z.string().min(1),
  productCode: z.string().min(1),
  productType: z.string().min(1),
  status: z.string().min(1),
  activatedAt: z.string().min(1),
  originatedPrincipal: z.number().finite().nonnegative(),
  totalPaid: z.number().finite().nonnegative(),
  totalOutstanding: z.number().finite().nonnegative(),
  servicingActive: z.boolean(),
})

const customerLoanAccountIndexSchema = z.array(customerLoanAccountSummarySchema)

const servicingAmountsSchema = z.object({
  principalPaid: money,
  interestPaid: money,
  feePaid: money,
  totalPaid: money,
  principalOutstanding: money,
  interestOutstanding: money,
  feeOutstanding: money,
  totalOutstanding: money,
})

export const loanAccountServicingSchema = servicingAmountsSchema.extend({
  servicingEvaluationDate: dateOnly,
  lastPaymentValueDate: nullableDateOnly,
  lastPaymentRecordedAt: nullableTimestamp,
})

export const installmentServicingSchema = servicingAmountsSchema.extend({
  status: z.string().min(1),
  statusEvaluationDate: dateOnly,
  lastPaymentValueDate: nullableDateOnly,
  lastPaymentRecordedAt: nullableTimestamp,
})

export const finalRepaymentScheduleItemSchema = z.object({
  installmentNumber: z.number().int().positive(),
  dueDate: dateOnly,
  principalDue: money,
  interestDue: money,
  feeDue: money,
  totalDue: money,
  servicing: installmentServicingSchema,
})

export const loanAccountSchema = z.object({
  loanApplicationId: javaUuid,
  loanAccountId: javaUuid,
  accountNumber: z.string().min(1),
  status: z.string().min(1),
  activatedAt: timestamp,
  originatedPrincipal: money,
  approvedTermMonths: z.number().int().positive(),
  totalInterest: money,
  totalFee: money,
  totalRepayment: money,
  servicing: loanAccountServicingSchema,
  disbursementDestination: z.object({
    bankCode: z.string().min(1),
    bankName: z.string().min(1),
    accountHolderName: z.string().min(1),
    maskedAccountNumber: z.string().min(1),
  }),
  finalRepaymentSchedule: z.object({
    scheduleId: javaUuid,
    scheduleType: z.string().min(1),
    version: z.number().int().positive(),
    firstDueDate: dateOnly,
    lastDueDate: dateOnly,
    items: z.array(finalRepaymentScheduleItemSchema),
  }),
})

export const repaymentAllocationSchema = z.object({
  sequence: z.number().int().positive(),
  repaymentScheduleItemId: javaUuid,
  installmentNumber: z.number().int().positive(),
  component: z.string().min(1),
  allocatedAmount: money,
})

export const repaymentInstallmentOutcomeSchema = servicingAmountsSchema.extend({
  repaymentScheduleItemId: javaUuid,
  installmentNumber: z.number().int().positive(),
  dueDate: dateOnly,
  previousStatus: z.string().min(1),
  resultingStatus: z.string().min(1),
  evaluationDate: dateOnly,
  lastPaymentValueDate: nullableDateOnly,
  lastPaymentRecordedAt: nullableTimestamp,
  statusChanged: z.boolean(),
})

export const repaymentHistoryItemSchema = z.object({
  repaymentTransactionId: javaUuid,
  receivedAmount: money,
  paymentValueDate: dateOnly,
  recordedAt: timestamp,
  principalAllocated: money,
  principalReleased: money,
  resultingLoanAccountStatus: z.string().min(1),
  accountBalance: loanAccountServicingSchema.extend({
    status: z.string().min(1),
  }),
  allocations: z.array(repaymentAllocationSchema),
  affectedInstallments: z.array(repaymentInstallmentOutcomeSchema),
})

export const repaymentHistoryPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(repaymentHistoryItemSchema),
})

export type CustomerLoanAccountSummary = z.infer<typeof customerLoanAccountSummarySchema>
export type LoanAccount = z.infer<typeof loanAccountSchema>
export type FinalRepaymentScheduleItem = z.infer<typeof finalRepaymentScheduleItemSchema>
export type RepaymentHistoryItem = z.infer<typeof repaymentHistoryItemSchema>
export type RepaymentHistoryPage = z.infer<typeof repaymentHistoryPageSchema>

export interface LoanApi {
  getOwnLoanAccounts(): Promise<CustomerLoanAccountSummary[]>
  getLoanAccount(loanApplicationId: string): Promise<LoanAccount>
  getRepaymentHistory(
    loanApplicationId: string,
    page: number,
    size: number,
  ): Promise<RepaymentHistoryPage>
}

export function createLoanApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): LoanApi {
  const protectedClient = createProtectedApiClient(coordinator, client)
  const applicationPath = (loanApplicationId: string) => {
    javaUuid.parse(loanApplicationId)
    return `/loan-applications/${encodeURIComponent(loanApplicationId)}`
  }

  return {
    async getOwnLoanAccounts() {
      return customerLoanAccountIndexSchema.parse(
        await protectedClient.request('/loan-accounts'),
      )
    },
    async getLoanAccount(loanApplicationId) {
      return loanAccountSchema.parse(
        await protectedClient.request(`${applicationPath(loanApplicationId)}/loan-account`),
      )
    },
    async getRepaymentHistory(loanApplicationId, page, size) {
      const validPage = z.number().int().nonnegative().parse(page)
      const validSize = z.number().int().min(1).max(100).parse(size)
      const search = new URLSearchParams({
        page: String(validPage),
        size: String(validSize),
      })
      return repaymentHistoryPageSchema.parse(
        await protectedClient.request(
          `${applicationPath(loanApplicationId)}/repayments?${search.toString()}`,
        ),
      )
    },
  }
}
