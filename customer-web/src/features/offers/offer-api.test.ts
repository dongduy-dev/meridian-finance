import { describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'

import type { ApiClient, ApiRequestOptions, ProtectedRequestCoordinator } from '@/lib/api'

import { createOfferApi } from './offer-api'

const applicationId = '11111111-1111-4111-8111-111111111111'
const offer = {
  approvedOfferId: '22222222-2222-4222-8222-222222222222',
  loanApplicationId: applicationId,
  status: 'PENDING',
  approvedPrincipal: 6_000_000,
  approvedTermMonths: 6,
  interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
  flatMonthlyInterestRate: 0.015,
  totalInterest: 540_000,
  feeAmount: 0,
  totalRepaymentAmount: 6_540_000,
  repaymentMethod: 'MONTHLY_INSTALLMENT',
  generatedAt: '2026-09-01T08:00:00',
  expiresAt: '2026-09-08T08:00:00',
  acceptedAt: null,
  declinedAt: null,
  expiredAt: null,
  availableActions: ['ACCEPT', 'DECLINE', 'FUTURE_ACTION'],
  repaymentItems: [{ installmentNumber: 1, principalDue: 1_000_000, interestDue: 90_000, feeDue: 0, totalDue: 1_090_000, repaymentTiming: 'MONTHLY_INSTALLMENT' }],
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
  return { api: createOfferApi(coordinator, { request } as ApiClient), request }
}

describe('Approved offer API boundary', () => {
  it('parses the offer and provisional items while preserving evolving string values', async () => {
    const { api } = apiWith(offer)
    await expect(api.getApprovedOffer(applicationId)).resolves.toMatchObject({
      status: 'PENDING',
      availableActions: ['ACCEPT', 'DECLINE', 'FUTURE_ACTION'],
      repaymentItems: [{ repaymentTiming: 'MONTHLY_INSTALLMENT', totalDue: 1_090_000 }],
    })
  })

  it('posts accept and decline with no body or invented operation identity', async () => {
    const { api, request } = apiWith(offer)
    await api.acceptApprovedOffer(applicationId)
    await api.declineApprovedOffer(applicationId)

    expect(request.mock.calls.map(([path]) => path)).toEqual([
      `/loan-applications/${applicationId}/approved-offer/accept`,
      `/loan-applications/${applicationId}/approved-offer/decline`,
    ])
    expect(request.mock.calls[0]?.[1]).toMatchObject({ method: 'POST' })
    expect(request.mock.calls[0]?.[1]).not.toHaveProperty('json')
    expect(request.mock.calls[1]?.[1]).not.toHaveProperty('requestId')
    expect(new Headers(request.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
  })

  it('rejects malformed IDs and malformed repayment items at the boundary', async () => {
    const first = apiWith(offer)
    await expect(first.api.getApprovedOffer('not-a-uuid')).rejects.toBeInstanceOf(ZodError)

    const second = apiWith({ ...offer, repaymentItems: [{ ...offer.repaymentItems[0], installmentNumber: 0 }] })
    await expect(second.api.getApprovedOffer(applicationId)).rejects.toBeInstanceOf(ZodError)
  })
})
