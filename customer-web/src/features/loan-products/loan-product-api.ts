import { z } from 'zod'

import { apiClient, type ApiClient } from '@/lib/api'

const nonEmptyString = z.string().min(1)

export const submissionEvidenceRequirementSchema = z.object({
  documentType: nonEmptyString,
  requirementStatus: nonEmptyString,
})

export const loanProductSchema = z.object({
  productCode: nonEmptyString,
  productType: nonEmptyString,
  name: nonEmptyString,
  description: z.string().nullable(),
  active: z.boolean(),
  minAmount: z.number().finite().nonnegative(),
  maxAmount: z.number().finite().nonnegative(),
  policy: z.object({
    allowedTermsMonths: z.array(z.number().int().positive()),
    pricing: z.object({
      flatMonthlyInterestRate: z.number().finite().nonnegative(),
      feeAmount: z.number().finite().nonnegative(),
    }),
    interestCalculationMethod: nonEmptyString,
    repaymentMethod: nonEmptyString,
    offerValidityDays: z.number().int().nonnegative(),
    submissionEvidenceRequirements: z.array(submissionEvidenceRequirementSchema),
    eligibilityNotes: z.array(nonEmptyString),
  }),
})

const loanProductListSchema = z.array(loanProductSchema)

export type LoanProduct = z.infer<typeof loanProductSchema>
export type SubmissionEvidenceRequirement = z.infer<typeof submissionEvidenceRequirementSchema>

export interface LoanProductApi {
  getProducts(): Promise<LoanProduct[]>
  getProduct(productCode: string): Promise<LoanProduct>
}

export function createLoanProductApi(client: ApiClient = apiClient): LoanProductApi {
  return {
    async getProducts() {
      return loanProductListSchema.parse(await client.request('/loan-products'))
    },
    async getProduct(productCode) {
      return loanProductSchema.parse(
        await client.request(`/loan-products/${encodeURIComponent(productCode)}`),
      )
    },
  }
}
