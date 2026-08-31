import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const javaUuid = z.string().guid()

export const customerApplicationSummarySchema = z.object({
  loanApplicationId: javaUuid,
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

export const customerApplicationDetailSchema = z.object({
  loanApplicationId: javaUuid,
  applicationNumber: z.string().min(1),
  productCode: z.string().min(1),
  productType: z.string().min(1),
  requestedAmount: z.number().finite().nonnegative(),
  requestedTermMonths: z.number().int().positive(),
  status: z.string().min(1),
  submittedAt: z.string().min(1),
})

export const cancelledApplicationSchema = z.object({
  loanApplicationId: javaUuid,
  resultingStatus: z.string().min(1),
  cancelledAt: z.string().min(1),
  idempotentReplay: z.boolean(),
})

export type CustomerApplicationSummary = z.infer<typeof customerApplicationSummarySchema>
export type CustomerApplicationDetail = z.infer<typeof customerApplicationDetailSchema>
export type CancelledApplication = z.infer<typeof cancelledApplicationSchema>

export interface ApplicationApi {
  getOwnApplications(): Promise<CustomerApplicationSummary[]>
  getOwnApplication(loanApplicationId: string): Promise<CustomerApplicationDetail>
  cancelOwnApplication(loanApplicationId: string, requestId: string): Promise<CancelledApplication>
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
    async getOwnApplication(loanApplicationId) {
      javaUuid.parse(loanApplicationId)
      return customerApplicationDetailSchema.parse(
        await protectedClient.request(
          `/loan-applications/${encodeURIComponent(loanApplicationId)}`,
        ),
      )
    },
    async cancelOwnApplication(loanApplicationId, requestId) {
      javaUuid.parse(loanApplicationId)
      javaUuid.parse(requestId)
      return cancelledApplicationSchema.parse(
        await protectedClient.request(
          `/loan-applications/${encodeURIComponent(loanApplicationId)}/cancel`,
          { method: 'POST', json: { requestId } },
        ),
      )
    },
  }
}
