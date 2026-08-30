import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

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

export type CustomerLoanAccountSummary = z.infer<typeof customerLoanAccountSummarySchema>

export interface LoanApi {
  getOwnLoanAccounts(): Promise<CustomerLoanAccountSummary[]>
}

export function createLoanApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): LoanApi {
  const protectedClient = createProtectedApiClient(coordinator, client)
  return {
    async getOwnLoanAccounts() {
      return customerLoanAccountIndexSchema.parse(
        await protectedClient.request('/loan-accounts'),
      )
    },
  }
}
