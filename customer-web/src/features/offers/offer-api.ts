import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const javaUuid = z.string().guid()
const money = z.number().finite().nonnegative()

export const provisionalRepaymentItemSchema = z.object({
  installmentNumber: z.number().int().positive(),
  principalDue: money,
  interestDue: money,
  feeDue: money,
  totalDue: money,
  repaymentTiming: z.string().min(1),
})

export const approvedOfferSchema = z.object({
  approvedOfferId: javaUuid,
  loanApplicationId: javaUuid,
  status: z.string().min(1),
  approvedPrincipal: money,
  approvedTermMonths: z.number().int().positive(),
  interestCalculationMethod: z.string().min(1),
  flatMonthlyInterestRate: money,
  totalInterest: money,
  feeAmount: money,
  totalRepaymentAmount: money,
  repaymentMethod: z.string().min(1),
  generatedAt: z.string().min(1),
  expiresAt: z.string().min(1),
  acceptedAt: z.string().min(1).nullable(),
  declinedAt: z.string().min(1).nullable(),
  expiredAt: z.string().min(1).nullable(),
  availableActions: z.array(z.string().min(1)),
  repaymentItems: z.array(provisionalRepaymentItemSchema),
})

export type ApprovedOffer = z.infer<typeof approvedOfferSchema>
export type ProvisionalRepaymentItem = z.infer<typeof provisionalRepaymentItemSchema>

export interface OfferApi {
  getApprovedOffer(loanApplicationId: string): Promise<ApprovedOffer>
  acceptApprovedOffer(loanApplicationId: string): Promise<ApprovedOffer>
  declineApprovedOffer(loanApplicationId: string): Promise<ApprovedOffer>
}

export function createOfferApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): OfferApi {
  const protectedClient = createProtectedApiClient(coordinator, client)
  const path = (loanApplicationId: string) => {
    javaUuid.parse(loanApplicationId)
    return `/loan-applications/${encodeURIComponent(loanApplicationId)}/approved-offer`
  }

  return {
    async getApprovedOffer(loanApplicationId) {
      return approvedOfferSchema.parse(await protectedClient.request(path(loanApplicationId)))
    },
    async acceptApprovedOffer(loanApplicationId) {
      return approvedOfferSchema.parse(await protectedClient.request(`${path(loanApplicationId)}/accept`, {
        method: 'POST',
      }))
    },
    async declineApprovedOffer(loanApplicationId) {
      return approvedOfferSchema.parse(await protectedClient.request(`${path(loanApplicationId)}/decline`, {
        method: 'POST',
      }))
    },
  }
}
