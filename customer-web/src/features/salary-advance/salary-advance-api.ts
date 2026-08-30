import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const nonEmptyString = z.string().min(1)
const javaUuid = z.string().guid()
const nullableUuid = javaUuid.nullable()

export const salaryAdvanceReadinessSchema = z.object({
  productCode: nonEmptyString,
  customerPartnerEmployeeLinkId: nullableUuid,
  employeeVerificationStatus: nonEmptyString,
  partnerEligibilityStatus: nonEmptyString,
  limitStatus: nonEmptyString,
  totalAmount: z.number().finite().nonnegative(),
  usedAmount: z.number().finite().nonnegative(),
  reservedAmount: z.number().finite().nonnegative(),
  availableAmount: z.number().finite().nonnegative(),
  lastRefreshAt: nonEmptyString.nullable(),
  applicationAllowed: z.boolean(),
  blockerCodes: z.array(nonEmptyString),
})

export const partnerVerificationOptionSchema = z.object({
  partnerCompanyId: javaUuid,
  companyCode: nonEmptyString,
  name: nonEmptyString,
})

const partnerVerificationOptionsSchema = z.array(partnerVerificationOptionSchema)

export const employeeVerificationSchema = z.object({
  customerId: javaUuid,
  partnerCompanyId: javaUuid,
  partnerEmployeeId: nullableUuid,
  customerPartnerEmployeeLinkId: nullableUuid,
  outcome: nonEmptyString,
  linkStatus: nonEmptyString.nullable(),
  manualReviewRequired: z.boolean(),
})

export const salaryAdvanceApplicationSchema = z.object({
  loanApplicationId: javaUuid,
  applicationNumber: nonEmptyString,
  customerId: javaUuid,
  productCode: nonEmptyString,
  productType: nonEmptyString,
  status: nonEmptyString,
  requestedAmount: z.number().finite().positive(),
  requestedTermMonths: z.number().int().positive(),
  customerPartnerEmployeeLinkId: javaUuid,
  productVerificationResult: nonEmptyString,
  totalLimitSnapshot: z.number().finite().nonnegative(),
  usedAmountSnapshot: z.number().finite().nonnegative(),
  reservedAmountSnapshot: z.number().finite().nonnegative(),
  availableLimitSnapshot: z.number().finite().nonnegative(),
  submittedAt: nonEmptyString,
})

export interface EmployeeVerificationInput {
  partnerCompanyId: string
  employeeCode: string
}

export interface SalaryAdvanceApplicationInput {
  customerPartnerEmployeeLinkId: string
  requestedAmount: number
  requestedTermMonths: number
}

const salaryAdvanceApplicationInputSchema = z.object({
  customerPartnerEmployeeLinkId: javaUuid,
  requestedAmount: z.number().int().positive(),
  requestedTermMonths: z.number().int().positive(),
})

export type SalaryAdvanceReadiness = z.infer<typeof salaryAdvanceReadinessSchema>
export type PartnerVerificationOption = z.infer<typeof partnerVerificationOptionSchema>
export type EmployeeVerification = z.infer<typeof employeeVerificationSchema>
export type SalaryAdvanceApplication = z.infer<typeof salaryAdvanceApplicationSchema>

export interface SalaryAdvanceApi {
  getReadiness(): Promise<SalaryAdvanceReadiness>
  getPartnerVerificationOptions(): Promise<PartnerVerificationOption[]>
  verifyEmployee(input: EmployeeVerificationInput): Promise<EmployeeVerification>
  submitApplication(input: SalaryAdvanceApplicationInput): Promise<SalaryAdvanceApplication>
}

export function createSalaryAdvanceApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): SalaryAdvanceApi {
  const protectedClient = createProtectedApiClient(coordinator, client)

  return {
    async getReadiness() {
      return salaryAdvanceReadinessSchema.parse(
        await protectedClient.request('/loan-products/salary-advance/readiness'),
      )
    },
    async getPartnerVerificationOptions() {
      return partnerVerificationOptionsSchema.parse(
        await protectedClient.request('/partner-companies/verification-options'),
      )
    },
    async verifyEmployee({ partnerCompanyId, employeeCode }) {
      return employeeVerificationSchema.parse(
        await protectedClient.request(
          `/partner-companies/${encodeURIComponent(partnerCompanyId)}/employee-verifications`,
          {
            method: 'POST',
            json: { employeeCode },
          },
        ),
      )
    },
    async submitApplication(input) {
      const body = salaryAdvanceApplicationInputSchema.parse(input)
      return salaryAdvanceApplicationSchema.parse(
        await protectedClient.request('/loan-applications/salary-advance', {
          method: 'POST',
          json: body,
        }),
      )
    },
  }
}
