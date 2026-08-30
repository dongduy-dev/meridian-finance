import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

export const customerApplicationSummarySchema = z.object({
  loanApplicationId: z.string().uuid(),
  applicationNumber: z.string().min(1),
  productCode: z.string().min(1),
  productType: z.string().min(1),
  requestedAmount: z.number().finite().nonnegative(),
  requestedTermMonths: z.number().int().positive(),
  status: z.string().min(1),
  submittedAt: z.string().min(1),
  lifecycleActive: z.boolean(),
  requiredAction: z.string().min(1),
})

const customerApplicationIndexSchema = z.array(customerApplicationSummarySchema)

export type CustomerApplicationSummary = z.infer<typeof customerApplicationSummarySchema>

export interface ApplicationApi {
  getOwnApplications(): Promise<CustomerApplicationSummary[]>
}

export function createApplicationApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): ApplicationApi {
  const protectedClient = createProtectedApiClient(coordinator, client)
  return {
    async getOwnApplications() {
      return customerApplicationIndexSchema.parse(
        await protectedClient.request('/loan-applications'),
      )
    },
  }
}
