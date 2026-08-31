import { expect, it, vi } from 'vitest'

import type { ApiClient, ProtectedRequestCoordinator } from '@/lib/api'

import { createCollateralLoanApi } from './collateral-loan-api'

const application = {
  loanApplicationId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', applicationNumber: 'CL-20260831-000001',
  productCode: 'COLLATERAL_LOAN', productType: 'SECURED', status: 'FUTURE_STATUS',
  requestedAmount: 25_000_000, requestedTermMonths: 12, collateralType: 'MOTORBIKE',
  productVerificationResult: 'FUTURE_RESULT', evidenceRequirements: [{
    checklistItemId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', documentType: 'FUTURE_DOCUMENT', requirementStatus: 'FUTURE_REQUIREMENT',
  }], submittedAt: '2026-08-31T09:00:00',
}

it('submits exact nested Collateral fields through the protected boundary', async () => {
  const request = vi.fn().mockResolvedValue(application)
  const coordinator: ProtectedRequestCoordinator = { requestProtected: vi.fn(async (operation) => { await operation('expired'); return operation('fresh') }) }
  const api = createCollateralLoanApi(coordinator, { request } as ApiClient)
  const input = {
    requestedAmount: 25_000_000, requestedTermMonths: 12,
    collateral: { type: 'MOTORBIKE' as const, description: '2024 motorbike', estimatedValue: 35_000_000, ownershipStatus: 'Owned by Customer', conditionNote: 'Normal used condition' },
  }

  expect(await api.submitApplication(input)).toMatchObject({ status: 'FUTURE_STATUS', collateralType: 'MOTORBIKE' })
  expect(request).toHaveBeenCalledTimes(2)
  const [path, options] = request.mock.calls[0]!
  expect(path).toBe('/loan-applications/collateral-loan')
  expect(options.method).toBe('POST')
  expect(options.json).toEqual(input)
  expect(options.json.collateral.type).toBe('MOTORBIKE')
  expect(options.json).not.toHaveProperty('collateralType')
  expect(options.json).not.toHaveProperty('customerId')
  expect(request.mock.calls[1]?.[1]?.json).toEqual(input)
})
