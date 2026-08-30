import { describe, expect, it, vi } from 'vitest'

import type { ApiClient, ProtectedRequestCoordinator } from '@/lib/api'

import { createAccountApi } from './account-api'

const customer = {
  customerId: '22222222-2222-4222-8222-222222222222',
  customerNumber: 'CUS-000000001',
  status: 'ACTIVE',
  verificationStatus: 'UNVERIFIED',
  profileCompletionStatus: 'COMPLETE',
  primaryActiveBankAccountPresent: true,
  profile: {
    fullName: 'Customer Demo',
    phoneNumber: '0901234567',
    residentialAddress: '1 Meridian Street',
    employmentStatus: 'SALARIED',
    employerName: 'Meridian Partner Co',
    termsConsentAccepted: true,
    dataProcessingConsentAccepted: true,
  },
}

const bankAccount = {
  customerBankAccountId: '33333333-3333-4333-8333-333333333333',
  bankCode: 'VCB',
  bankNameSnapshot: 'Vietcombank',
  accountHolderName: 'Customer Demo',
  maskedAccountNumber: '****7890',
  accountNumberLastFour: '7890',
  status: 'ACTIVE',
  primaryAccount: true,
  createdAt: '2026-08-30T08:00:00',
  updatedAt: '2026-08-30T08:00:00',
  deactivatedAt: null,
}

function setup(responses: unknown[]) {
  const request = vi.fn().mockImplementation(() => Promise.resolve(responses.shift()))
  const client: ApiClient = { request }
  const coordinator: ProtectedRequestCoordinator = {
    requestProtected: vi.fn((operation) => operation('protected-access-token')),
  }
  return { api: createAccountApi(coordinator, client), coordinator, request }
}

describe('Customer account API', () => {
  it('uses the exact protected Customer-owned paths and methods', async () => {
    const { api, coordinator, request } = setup([
      customer,
      customer,
      [bankAccount],
      bankAccount,
      bankAccount,
      { ...bankAccount, status: 'DEACTIVATED', primaryAccount: false, deactivatedAt: '2026-08-30T09:00:00' },
    ])
    const profileInput = {
      fullName: 'Customer Demo',
      phoneNumber: '0901234567',
      residentialAddress: '1 Meridian Street',
      employmentStatus: 'SALARIED',
      employerName: null,
      termsConsentAccepted: true,
      dataProcessingConsentAccepted: true,
    }
    const addInput = {
      bankCode: 'VCB',
      bankNameSnapshot: 'Vietcombank',
      accountHolderName: 'Customer Demo',
      accountNumber: '1234567890',
    }

    await api.getOwnCustomer()
    await api.updateOwnProfile(profileInput)
    await api.getOwnBankAccounts()
    await api.addBankAccount(addInput)
    await api.makePrimary(bankAccount.customerBankAccountId)
    await api.deactivate(bankAccount.customerBankAccountId)

    expect(coordinator.requestProtected).toHaveBeenCalledTimes(6)
    expect(request.mock.calls.map(([path, options]) => [path, options?.method ?? 'GET'])).toEqual([
      ['/customers/me', 'GET'],
      ['/customers/me/profile', 'PUT'],
      ['/customers/me/bank-accounts', 'GET'],
      ['/customers/me/bank-accounts', 'POST'],
      [`/customers/me/bank-accounts/${bankAccount.customerBankAccountId}/make-primary`, 'POST'],
      [`/customers/me/bank-accounts/${bankAccount.customerBankAccountId}/deactivate`, 'POST'],
    ])
    expect(request.mock.calls[1]?.[1]?.json).toEqual(profileInput)
    expect(request.mock.calls[1]?.[1]?.json).not.toHaveProperty('customerId')
    expect(request.mock.calls[1]?.[1]?.json).not.toHaveProperty('identityReference')
    expect(request.mock.calls[3]?.[1]?.json).toEqual(addInput)
    for (const [, options] of request.mock.calls) {
      expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer protected-access-token')
    }
  })

  it('rejects malformed Customer and bank-account response contracts', async () => {
    const invalidCustomer = setup([{ ...customer, primaryActiveBankAccountPresent: 'yes' }])
    await expect(invalidCustomer.api.getOwnCustomer()).rejects.toThrow()

    const unsafeAccount = setup([[{ ...bankAccount, maskedAccountNumber: null }]])
    await expect(unsafeAccount.api.getOwnBankAccounts()).rejects.toThrow()
  })
})
