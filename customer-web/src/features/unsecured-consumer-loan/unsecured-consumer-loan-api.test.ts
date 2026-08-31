import { describe, expect, it, vi } from 'vitest'

import type { ApiClient, ProtectedRequestCoordinator } from '@/lib/api'

import { createUnsecuredConsumerLoanApi } from './unsecured-consumer-loan-api'

const application = {
  loanApplicationId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  applicationNumber: 'UCL-20260831-000001', productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED', status: 'FUTURE_APPLICATION_STATUS', requestedAmount: 5_000_000,
  requestedTermMonths: 6, productVerificationResult: 'FUTURE_RESULT', submittedAt: '2026-08-31T09:00:00',
}

it('submits the exact protected UCL body and preserves it across authenticated replay', async () => {
  const request = vi.fn().mockResolvedValue(application)
  const coordinator: ProtectedRequestCoordinator = {
    requestProtected: vi.fn(async (operation) => { await operation('expired'); return operation('fresh') }),
  }
  const api = createUnsecuredConsumerLoanApi(coordinator, { request } as ApiClient)
  const input = { requestedAmount: 5_000_000, requestedTermMonths: 6 }

  expect(await api.submitApplication(input)).toMatchObject({ loanApplicationId: application.loanApplicationId, status: 'FUTURE_APPLICATION_STATUS' })
  expect(request).toHaveBeenCalledTimes(2)
  for (const [path, options] of request.mock.calls) {
    expect(path).toBe('/loan-applications/unsecured-consumer-loan')
    expect(options.method).toBe('POST')
    expect(options.json).toEqual(input)
    expect(Number.isInteger(options.json.requestedAmount)).toBe(true)
    expect(options.json).not.toHaveProperty('customerId')
    expect(options.json).not.toHaveProperty('productCode')
  }
})

describe('UCL request validation', () => {
  it('rejects fractional VND before transport', async () => {
    const request = vi.fn()
    const coordinator: ProtectedRequestCoordinator = { requestProtected: vi.fn((operation) => operation('token')) }
    const api = createUnsecuredConsumerLoanApi(coordinator, { request } as ApiClient)
    await expect(api.submitApplication({ requestedAmount: 1.5, requestedTermMonths: 6 })).rejects.toThrow()
    expect(request).not.toHaveBeenCalled()
  })
})
