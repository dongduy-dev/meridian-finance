import { describe, expect, it, vi } from 'vitest'

import type { ApiClient, ApiRequestOptions, ProtectedRequestCoordinator } from '@/lib/api'

import { createApplicationApi } from './application-api'

describe('Customer application API boundary', () => {
  it('uses the protected coordinator and preserves unknown status/action strings', async () => {
    const response = [{
      loanApplicationId: '11111111-1111-4111-8111-111111111111',
      applicationNumber: 'UCL-20260830-000001',
      productCode: 'UNSECURED_CONSUMER_LOAN',
      productType: 'UNSECURED',
      requestedAmount: 10_000_000,
      requestedTermMonths: 6,
      status: 'FUTURE_APPLICATION_STATE',
      submittedAt: '2026-08-30T08:00:00',
      lifecycleActive: true,
      requiredAction: 'FUTURE_CUSTOMER_ACTION',
    }]
    const request = vi.fn(async (path: string, options?: RequestInit) => {
      void path
      void options
      return response
    })
    const coordinator: ProtectedRequestCoordinator = {
      requestProtected: vi.fn((operation) => operation('protected-customer-token')),
    }
    const api = createApplicationApi(coordinator, { request } as ApiClient)

    const applications = await api.getOwnApplications()

    expect(applications[0]).toMatchObject({
      status: 'FUTURE_APPLICATION_STATE',
      requiredAction: 'FUTURE_CUSTOMER_ACTION',
      lifecycleActive: true,
    })
    expect(coordinator.requestProtected).toHaveBeenCalledOnce()
    const [path, options] = request.mock.calls[0]!
    expect(path).toBe('/loan-applications')
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
  })

  it('reads safe detail and sends the exact cancellation body', async () => {
    const applicationId = '11111111-1111-4111-8111-111111111111'
    const requestId = '22222222-2222-4222-8222-222222222222'
    const detail = {
      loanApplicationId: applicationId, applicationNumber: 'SA-20260830-000001',
      productCode: 'SALARY_ADVANCE', productType: 'SALARY_BASED', requestedAmount: 2_000_000,
      requestedTermMonths: 1, status: 'RETURNED_FOR_REVISION', submittedAt: '2026-08-30T08:00:00',
    }
    const cancellation = {
      loanApplicationId: applicationId, resultingStatus: 'CANCELLED',
      cancelledAt: '2026-08-31T08:00:00', idempotentReplay: false,
    }
    const request = vi.fn(async (path: string, options?: ApiRequestOptions) => {
      void options
      return path.endsWith('/cancel') ? cancellation : detail
    })
    const coordinator: ProtectedRequestCoordinator = {
      requestProtected: vi.fn((operation) => operation('protected-customer-token')),
    }
    const api = createApplicationApi(coordinator, { request } as ApiClient)

    expect(await api.getOwnApplication(applicationId)).toEqual(detail)
    expect(await api.cancelOwnApplication(applicationId, requestId)).toEqual(cancellation)

    expect(request.mock.calls[0]?.[0]).toBe(`/loan-applications/${applicationId}`)
    expect(request.mock.calls[1]?.[0]).toBe(`/loan-applications/${applicationId}/cancel`)
    expect(request.mock.calls[1]?.[1]).toMatchObject({ method: 'POST' })
    expect(request.mock.calls[1]?.[1]?.json).toEqual({ requestId })
  })
})
