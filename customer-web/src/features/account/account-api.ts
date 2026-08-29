import { z } from 'zod'

import {
  createProtectedApiClient,
  type ApiClient,
  type ProtectedRequestCoordinator,
} from '@/lib/api'

const customerProfileSchema = z.object({
  fullName: z.string(),
  phoneNumber: z.string(),
  residentialAddress: z.string(),
  employmentStatus: z.string(),
  employerName: z.string().nullable(),
  termsConsentAccepted: z.boolean(),
  dataProcessingConsentAccepted: z.boolean(),
})

export const customerSchema = z.object({
  customerId: z.string().uuid(),
  customerNumber: z.string().min(1),
  status: z.string().min(1),
  verificationStatus: z.string().min(1),
  profileCompletionStatus: z.string().min(1),
  primaryActiveBankAccountPresent: z.boolean(),
  profile: customerProfileSchema.nullable(),
})

export const customerBankAccountSchema = z.object({
  customerBankAccountId: z.string().uuid(),
  bankCode: z.string().min(1),
  bankNameSnapshot: z.string().min(1),
  accountHolderName: z.string().min(1),
  maskedAccountNumber: z.string().min(1),
  accountNumberLastFour: z.string().min(1),
  status: z.string().min(1),
  primaryAccount: z.boolean(),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1),
  deactivatedAt: z.string().nullable(),
})

const customerBankAccountListSchema = z.array(customerBankAccountSchema)

export type Customer = z.infer<typeof customerSchema>
export type CustomerBankAccount = z.infer<typeof customerBankAccountSchema>

export interface UpdateCustomerProfileInput {
  fullName: string
  identityReference?: string
  phoneNumber: string
  residentialAddress: string
  employmentStatus: string
  employerName: string | null
  termsConsentAccepted: boolean
  dataProcessingConsentAccepted: boolean
}

export interface AddCustomerBankAccountInput {
  bankCode: string
  bankNameSnapshot: string
  accountHolderName: string
  accountNumber: string
}

export interface AccountApi {
  getOwnCustomer(): Promise<Customer>
  updateOwnProfile(input: UpdateCustomerProfileInput): Promise<Customer>
  getOwnBankAccounts(): Promise<CustomerBankAccount[]>
  addBankAccount(input: AddCustomerBankAccountInput): Promise<CustomerBankAccount>
  makePrimary(customerBankAccountId: string): Promise<CustomerBankAccount>
  deactivate(customerBankAccountId: string): Promise<CustomerBankAccount>
}

export function createAccountApi(
  coordinator: ProtectedRequestCoordinator,
  client?: ApiClient,
): AccountApi {
  const protectedClient = createProtectedApiClient(coordinator, client)

  return {
    async getOwnCustomer() {
      return customerSchema.parse(await protectedClient.request('/customers/me'))
    },
    async updateOwnProfile(input) {
      return customerSchema.parse(
        await protectedClient.request('/customers/me/profile', {
          method: 'PUT',
          json: input,
        }),
      )
    },
    async getOwnBankAccounts() {
      return customerBankAccountListSchema.parse(
        await protectedClient.request('/customers/me/bank-accounts'),
      )
    },
    async addBankAccount(input) {
      return customerBankAccountSchema.parse(
        await protectedClient.request('/customers/me/bank-accounts', {
          method: 'POST',
          json: input,
        }),
      )
    },
    async makePrimary(customerBankAccountId) {
      return customerBankAccountSchema.parse(
        await protectedClient.request(
          `/customers/me/bank-accounts/${encodeURIComponent(customerBankAccountId)}/make-primary`,
          { method: 'POST' },
        ),
      )
    },
    async deactivate(customerBankAccountId) {
      return customerBankAccountSchema.parse(
        await protectedClient.request(
          `/customers/me/bank-accounts/${encodeURIComponent(customerBankAccountId)}/deactivate`,
          { method: 'POST' },
        ),
      )
    },
  }
}
