import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const javaUuid = z.string().guid()
const money = z.number().finite().nonnegative()

export const contractRepaymentItemSchema = z.object({
  installmentNumber: z.number().int().positive(),
  principalDue: money,
  interestDue: money,
  feeDue: money,
  totalDue: money,
})

export const contractBankAccountSchema = z.object({
  bankCode: z.string().min(1),
  bankNameSnapshot: z.string().min(1),
  accountHolderName: z.string().min(1),
  maskedAccountNumber: z.string().min(1),
  primaryAtCapture: z.boolean(),
  activeAtCapture: z.boolean(),
  capturedAt: z.string().min(1),
})

export const loanContractSchema = z.object({
  contractId: javaUuid,
  contractReference: z.string().min(1),
  contractVersion: z.number().int().positive(),
  status: z.string().min(1),
  approvedPrincipal: money,
  approvedTermMonths: z.number().int().positive(),
  interestCalculationMethod: z.string().min(1),
  flatMonthlyInterestRate: money,
  totalInterest: money,
  feeAmount: money,
  totalRepaymentAmount: money,
  repaymentMethod: z.string().min(1),
  repaymentPreview: z.array(contractRepaymentItemSchema),
  disbursementBankAccount: contractBankAccountSchema,
  preparedAt: z.string().min(1),
  acknowledgedAt: z.string().min(1).nullable(),
  readinessConfirmedAt: z.string().min(1).nullable(),
  availableCustomerAction: z.string().min(1).nullable(),
})

export const acknowledgeLoanContractRequestSchema = z.object({
  acknowledgmentRequestId: javaUuid,
  expectedContractVersion: z.number().int().positive(),
})

export type LoanContract = z.infer<typeof loanContractSchema>
export type AcknowledgeLoanContractRequest = z.infer<typeof acknowledgeLoanContractRequestSchema>

export interface ContractApi {
  getCurrentContract(loanApplicationId: string): Promise<LoanContract>
  acknowledgeCurrentContract(loanApplicationId: string, request: AcknowledgeLoanContractRequest): Promise<LoanContract>
}

export function createContractApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): ContractApi {
  const protectedClient = createProtectedApiClient(coordinator, client)
  const path = (loanApplicationId: string) => {
    javaUuid.parse(loanApplicationId)
    return `/loan-applications/${encodeURIComponent(loanApplicationId)}/contracts/current`
  }

  return {
    async getCurrentContract(loanApplicationId) {
      return loanContractSchema.parse(await protectedClient.request(path(loanApplicationId)))
    },
    async acknowledgeCurrentContract(loanApplicationId, request) {
      return loanContractSchema.parse(await protectedClient.request(`${path(loanApplicationId)}/acknowledgment`, {
        method: 'POST',
        json: acknowledgeLoanContractRequestSchema.parse(request),
      }))
    },
  }
}
