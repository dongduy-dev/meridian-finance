import { describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'

import type { ApiClient, ApiRequestOptions, ProtectedRequestCoordinator } from '@/lib/api'

import { createContractApi } from './contract-api'

const applicationId = '11111111-1111-4111-8111-111111111111'
const requestId = '22222222-2222-4222-8222-222222222222'
const contract = {
  contractId: '33333333-3333-4333-8333-333333333333',
  contractReference: 'CTR-2026-000001-V1',
  contractVersion: 1,
  status: 'PREPARED',
  approvedPrincipal: 6_000_000,
  approvedTermMonths: 6,
  interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
  flatMonthlyInterestRate: 0.015,
  totalInterest: 540_000,
  feeAmount: 0,
  totalRepaymentAmount: 6_540_000,
  repaymentMethod: 'MONTHLY_INSTALLMENT',
  repaymentPreview: [{ installmentNumber: 1, principalDue: 1_000_000, interestDue: 90_000, feeDue: 0, totalDue: 1_090_000 }],
  disbursementBankAccount: {
    bankCode: 'VCB', bankNameSnapshot: 'Vietcombank', accountHolderName: 'MERIDIAN CUSTOMER',
    maskedAccountNumber: '****6789', primaryAtCapture: true, activeAtCapture: true,
    capturedAt: '2026-09-01T09:00:00',
  },
  preparedAt: '2026-09-01T09:00:00',
  acknowledgedAt: null,
  readinessConfirmedAt: null,
  availableCustomerAction: 'ACKNOWLEDGE',
}

function apiWith(response: unknown) {
  const request = vi.fn(async (path: string, options?: ApiRequestOptions) => {
    void path
    void options
    return response
  })
  const coordinator: ProtectedRequestCoordinator = {
    requestProtected: vi.fn((operation) => operation('protected-customer-token')),
  }
  return { api: createContractApi(coordinator, { request } as ApiClient), request }
}

describe('Current contract API boundary', () => {
  it('parses exact terms, repayment preview, and only the masked destination projection', async () => {
    const { api } = apiWith(contract)
    const result = await api.getCurrentContract(applicationId)
    expect(result).toMatchObject({
      contractVersion: 1,
      repaymentPreview: [{ totalDue: 1_090_000 }],
      disbursementBankAccount: { maskedAccountNumber: '****6789' },
    })
    expect(result.disbursementBankAccount).not.toHaveProperty('accountNumber')
  })

  it('sends the acknowledgment UUID and exact expected version in the request body', async () => {
    const { api, request } = apiWith(contract)
    await api.acknowledgeCurrentContract(applicationId, {
      acknowledgmentRequestId: requestId,
      expectedContractVersion: 1,
    })
    expect(request).toHaveBeenCalledWith(
      `/loan-applications/${applicationId}/contracts/current/acknowledgment`,
      expect.objectContaining({
        method: 'POST',
        json: { acknowledgmentRequestId: requestId, expectedContractVersion: 1 },
      }),
    )
    expect(request.mock.calls[0]?.[1]).not.toHaveProperty('requestId')
  })

  it('rejects invalid IDs, versions, and unmasked schema omissions at the boundary', async () => {
    const first = apiWith(contract)
    await expect(first.api.acknowledgeCurrentContract(applicationId, { acknowledgmentRequestId: 'bad-id', expectedContractVersion: 1 })).rejects.toBeInstanceOf(ZodError)
    await expect(first.api.acknowledgeCurrentContract(applicationId, { acknowledgmentRequestId: requestId, expectedContractVersion: 0 })).rejects.toBeInstanceOf(ZodError)

    const second = apiWith({ ...contract, disbursementBankAccount: { bankCode: 'VCB' } })
    await expect(second.api.getCurrentContract(applicationId)).rejects.toBeInstanceOf(ZodError)
  })
})
