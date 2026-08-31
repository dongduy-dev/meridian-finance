import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const nonEmptyString = z.string().min(1)
const javaUuid = z.string().guid()

export const collateralTypes = [
  'MOTORBIKE', 'CAR', 'ELECTRONICS', 'PROPERTY_DOCUMENT', 'OTHER',
] as const

const collateralDetailsSchema = z.object({
  type: z.enum(collateralTypes),
  description: z.string().min(1).max(500),
  estimatedValue: z.number().int().positive(),
  ownershipStatus: z.string().min(1).max(200),
  conditionNote: z.string().min(1).max(500),
})

const inputSchema = z.object({
  requestedAmount: z.number().int().positive(),
  requestedTermMonths: z.number().int().positive(),
  collateral: collateralDetailsSchema,
})

const evidenceRequirementSchema = z.object({
  checklistItemId: javaUuid,
  documentType: nonEmptyString,
  requirementStatus: nonEmptyString,
})

export const collateralLoanApplicationSchema = z.object({
  loanApplicationId: javaUuid,
  applicationNumber: nonEmptyString,
  productCode: nonEmptyString,
  productType: nonEmptyString,
  status: nonEmptyString,
  requestedAmount: z.number().finite().positive(),
  requestedTermMonths: z.number().int().positive(),
  collateralType: nonEmptyString,
  productVerificationResult: nonEmptyString,
  evidenceRequirements: z.array(evidenceRequirementSchema),
  submittedAt: nonEmptyString,
})

export type CollateralType = typeof collateralTypes[number]
export type CollateralLoanApplication = z.infer<typeof collateralLoanApplicationSchema>
export type CollateralLoanApplicationInput = z.infer<typeof inputSchema>

export function createCollateralLoanApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
) {
  const protectedClient = createProtectedApiClient(coordinator, client)
  return {
    async submitApplication(input: CollateralLoanApplicationInput) {
      const body = inputSchema.parse(input)
      return collateralLoanApplicationSchema.parse(
        await protectedClient.request('/loan-applications/collateral-loan', {
          method: 'POST',
          json: body,
        }),
      )
    },
  }
}
