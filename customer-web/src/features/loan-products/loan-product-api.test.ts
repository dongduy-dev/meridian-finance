import { describe, expect, it, vi } from 'vitest'

import type { ApiClient } from '@/lib/api'

import { createLoanProductApi } from './loan-product-api'

const productResponse = {
  productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED',
  name: 'Unsecured Consumer Loan',
  description: null,
  active: true,
  minAmount: 2_000_000,
  maxAmount: 50_000_000,
  policy: {
    allowedTermsMonths: [3, 6, 9, 12],
    pricing: { flatMonthlyInterestRate: 0.018, feeAmount: 0 },
    interestCalculationMethod: 'FUTURE_INTEREST_METHOD',
    repaymentMethod: 'FUTURE_REPAYMENT_METHOD',
    offerValidityDays: 7,
    submissionEvidenceRequirements: [
      { documentType: 'FUTURE_DOCUMENT_TYPE', requirementStatus: 'FUTURE_REQUIREMENT' },
    ],
    eligibilityNotes: ['Customer readiness is required.'],
  },
}

describe('loan product API boundary', () => {
  it('parses public list and detail responses with nullable descriptions and evolving strings', async () => {
    const request = vi.fn(async (path: string) =>
      path === '/loan-products' ? [productResponse] : productResponse,
    )
    const api = createLoanProductApi({ request } as ApiClient)

    const products = await api.getProducts()
    const product = await api.getProduct('UNSECURED_CONSUMER_LOAN')

    expect(products.at(0)?.description).toBeNull()
    expect(product.policy.interestCalculationMethod).toBe('FUTURE_INTEREST_METHOD')
    expect(product.policy.submissionEvidenceRequirements[0]).toEqual({
      documentType: 'FUTURE_DOCUMENT_TYPE',
      requirementStatus: 'FUTURE_REQUIREMENT',
    })
    expect(request).toHaveBeenNthCalledWith(1, '/loan-products')
    expect(request).toHaveBeenNthCalledWith(2, '/loan-products/UNSECURED_CONSUMER_LOAN')
  })
})
