import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const nonEmptyString = z.string().min(1)
const javaUuid = z.string().guid()

export const unsecuredConsumerLoanApplicationSchema = z.object({
  loanApplicationId: javaUuid,
  applicationNumber: nonEmptyString,
  productCode: nonEmptyString,
  productType: nonEmptyString,
  status: nonEmptyString,
  requestedAmount: z.number().finite().positive(),
  requestedTermMonths: z.number().int().positive(),
  productVerificationResult: nonEmptyString,
  submittedAt: nonEmptyString,
})

const inputSchema = z.object({
  requestedAmount: z.number().int().positive(),
  requestedTermMonths: z.number().int().positive(),
})

export type UnsecuredConsumerLoanApplication = z.infer<typeof unsecuredConsumerLoanApplicationSchema>
export type UnsecuredConsumerLoanApplicationInput = z.infer<typeof inputSchema>

export function createUnsecuredConsumerLoanApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
) {
  const protectedClient = createProtectedApiClient(coordinator, client)
  return {
    async submitApplication(input: UnsecuredConsumerLoanApplicationInput) {
      const body = inputSchema.parse(input)
      return unsecuredConsumerLoanApplicationSchema.parse(
        await protectedClient.request('/loan-applications/unsecured-consumer-loan', {
          method: 'POST',
          json: body,
        }),
      )
    },
  }
}
