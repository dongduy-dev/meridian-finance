import { describe, expect, it, vi } from 'vitest'

import type { ApiClient, ProtectedRequestCoordinator } from '@/lib/api'

import { createSalaryAdvanceApi } from './salary-advance-api'

const readiness = {
  productCode: 'SALARY_ADVANCE',
  customerPartnerEmployeeLinkId: null,
  employeeVerificationStatus: 'FUTURE_EMPLOYEE_STATUS',
  partnerEligibilityStatus: 'FUTURE_PARTNER_STATUS',
  limitStatus: 'FUTURE_LIMIT_STATUS',
  totalAmount: 4_000_000,
  usedAmount: 500_000,
  reservedAmount: 250_000,
  availableAmount: 3_250_000,
  lastRefreshAt: null,
  applicationAllowed: false,
  blockerCodes: ['EMPLOYEE_NOT_VERIFIED', 'FUTURE_BLOCKER'],
}

const option = {
  partnerCompanyId: '11111111-1111-4111-8111-111111111111',
  companyCode: 'MER-LONG-CODE',
  name: 'Meridian Partner Company',
}

const verification = {
  customerId: '22222222-2222-4222-8222-222222222222',
  partnerCompanyId: option.partnerCompanyId,
  partnerEmployeeId: null,
  customerPartnerEmployeeLinkId: null,
  outcome: 'FUTURE_VERIFICATION_OUTCOME',
  linkStatus: null,
  manualReviewRequired: false,
}

const application = {
  loanApplicationId: '33333333-3333-4333-8333-333333333333',
  applicationNumber: 'SA-20260830-000001',
  customerId: '22222222-2222-4222-8222-222222222222',
  productCode: 'SALARY_ADVANCE',
  productType: 'SALARY_BASED',
  status: 'FUTURE_APPLICATION_STATUS',
  requestedAmount: 2_000_000,
  requestedTermMonths: 5,
  customerPartnerEmployeeLinkId: '44444444-4444-4444-8444-444444444444',
  productVerificationResult: 'FUTURE_VERIFICATION_RESULT',
  totalLimitSnapshot: 5_000_000,
  usedAmountSnapshot: 0,
  reservedAmountSnapshot: 2_000_000,
  availableLimitSnapshot: 3_000_000,
  submittedAt: '2026-08-30T10:00:00',
}

function setup(responses: unknown[]) {
  const request = vi.fn().mockImplementation(() => Promise.resolve(responses.shift()))
  const client: ApiClient = { request }
  const coordinator: ProtectedRequestCoordinator = {
    requestProtected: vi.fn((operation) => operation('protected-customer-token')),
  }
  return { api: createSalaryAdvanceApi(coordinator, client), coordinator, request }
}

describe('Salary Advance API boundary', () => {
  it('parses nullable readiness fields, evolving strings, and preserved blockers through the protected client', async () => {
    const { api, coordinator, request } = setup([readiness])

    const result = await api.getReadiness()

    expect(result).toMatchObject({
      customerPartnerEmployeeLinkId: null,
      lastRefreshAt: null,
      employeeVerificationStatus: 'FUTURE_EMPLOYEE_STATUS',
      partnerEligibilityStatus: 'FUTURE_PARTNER_STATUS',
      limitStatus: 'FUTURE_LIMIT_STATUS',
      blockerCodes: ['EMPLOYEE_NOT_VERIFIED', 'FUTURE_BLOCKER'],
    })
    expect(coordinator.requestProtected).toHaveBeenCalledOnce()
    const [path, options] = request.mock.calls[0]!
    expect(path).toBe('/loan-products/salary-advance/readiness')
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
  })

  it('uses only the Customer-safe Partner verification option contract', async () => {
    const { api, coordinator, request } = setup([[option]])

    expect(await api.getPartnerVerificationOptions()).toEqual([option])
    expect(coordinator.requestProtected).toHaveBeenCalledOnce()
    const [path, options] = request.mock.calls[0]!
    expect(path).toBe('/partner-companies/verification-options')
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
    expect(Object.keys(option)).toEqual(['partnerCompanyId', 'companyCode', 'name'])
  })

  it('sends only employeeCode and preserves the same logical body across protected auth replay', async () => {
    const request = vi.fn().mockResolvedValue(verification)
    const coordinator: ProtectedRequestCoordinator = {
      requestProtected: vi.fn(async (operation) => {
        await operation('expired-token')
        return operation('refreshed-token')
      }),
    }
    const api = createSalaryAdvanceApi(coordinator, { request } as ApiClient)

    const result = await api.verifyEmployee({
      partnerCompanyId: option.partnerCompanyId,
      employeeCode: 'EMP-PRIVATE-001',
    })

    expect(result).toMatchObject({
      partnerEmployeeId: null,
      customerPartnerEmployeeLinkId: null,
      linkStatus: null,
      outcome: 'FUTURE_VERIFICATION_OUTCOME',
    })
    expect(request).toHaveBeenCalledTimes(2)
    for (const [path, options] of request.mock.calls) {
      expect(path).toBe(`/partner-companies/${option.partnerCompanyId}/employee-verifications`)
      expect(options?.method).toBe('POST')
      expect(options?.json).toEqual({ employeeCode: 'EMP-PRIVATE-001' })
      expect(options?.json).not.toHaveProperty('customerId')
      expect(options?.json).not.toHaveProperty('identityReference')
      expect(options?.json).not.toHaveProperty('salary')
      expect(options?.json).not.toHaveProperty('limit')
    }
    expect(new Headers(request.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe('Bearer expired-token')
    expect(new Headers(request.mock.calls[1]?.[1]?.headers).get('Authorization')).toBe('Bearer refreshed-token')
  })

  it('submits exactly the reusable link, whole-VND amount, and returned term without transport retry', async () => {
    const input = {
      customerPartnerEmployeeLinkId: application.customerPartnerEmployeeLinkId,
      requestedAmount: application.requestedAmount,
      requestedTermMonths: application.requestedTermMonths,
    }
    const { api, coordinator, request } = setup([application])

    const result = await api.submitApplication(input)

    expect(result).toMatchObject({
      applicationNumber: 'SA-20260830-000001',
      status: 'FUTURE_APPLICATION_STATUS',
      productVerificationResult: 'FUTURE_VERIFICATION_RESULT',
    })
    expect(coordinator.requestProtected).toHaveBeenCalledOnce()
    expect(request).toHaveBeenCalledOnce()
    const [path, options] = request.mock.calls[0]!
    expect(path).toBe('/loan-applications/salary-advance')
    expect(options?.method).toBe('POST')
    expect(options?.json).toEqual(input)
    expect(Number.isInteger(options?.json.requestedAmount)).toBe(true)
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
  })

  it('rejects fractional VND before making a submission request', async () => {
    const { api, request } = setup([])

    await expect(api.submitApplication({
      customerPartnerEmployeeLinkId: application.customerPartnerEmployeeLinkId,
      requestedAmount: 1_000_000.5,
      requestedTermMonths: 1,
    })).rejects.toThrow()
    expect(request).not.toHaveBeenCalled()
  })
})
