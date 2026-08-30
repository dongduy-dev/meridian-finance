import { describe, expect, it, vi } from 'vitest'

import type { ApiClient, ProtectedRequestCoordinator } from '@/lib/api'

import { createLoanApi } from './loan-api'

describe('Customer LoanAccount API boundary', () => {
  it('uses the protected coordinator and preserves unknown status strings and returned balances', async () => {
    const response = [{
      loanApplicationId: '11111111-1111-4111-8111-111111111111',
      loanAccountId: '22222222-2222-4222-8222-222222222222',
      accountNumber: 'LA-20260830-000001',
      applicationNumber: 'UCL-20260830-000001',
      productCode: 'UNSECURED_CONSUMER_LOAN',
      productType: 'UNSECURED',
      status: 'FUTURE_ACCOUNT_STATE',
      activatedAt: '2026-08-30T08:00:00',
      originatedPrincipal: 10_000_000,
      totalPaid: 1_234_567,
      totalOutstanding: 9_876_543,
      servicingActive: true,
    }]
    const request = vi.fn(async (path: string, options?: RequestInit) => {
      void path
      void options
      return response
    })
    const coordinator: ProtectedRequestCoordinator = {
      requestProtected: vi.fn((operation) => operation('protected-customer-token')),
    }
    const api = createLoanApi(coordinator, { request } as ApiClient)

    const accounts = await api.getOwnLoanAccounts()

    expect(accounts[0]).toMatchObject({
      status: 'FUTURE_ACCOUNT_STATE',
      totalPaid: 1_234_567,
      totalOutstanding: 9_876_543,
      servicingActive: true,
    })
    expect(coordinator.requestProtected).toHaveBeenCalledOnce()
    const [path, options] = request.mock.calls[0]!
    expect(path).toBe('/loan-accounts')
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
  })
})
