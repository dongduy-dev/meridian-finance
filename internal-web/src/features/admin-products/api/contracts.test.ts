import { describe, expect, it } from 'vitest'
import { adminLoanProductSchema, updateProductLimitsInputSchema } from './contracts'

describe('Loan Product administration contracts', () => {
  it('accepts the purpose-specific projection including unknown future code and type values', () => {
    const parsed = adminLoanProductSchema.parse({
      productCode: 'FUTURE_PRODUCT', productType: 'FUTURE_TYPE', name: 'Future product',
      description: null, active: false, minAmount: 0, maxAmount: 10,
    })
    expect(parsed.productCode).toBe('FUTURE_PRODUCT')
    expect(Object.keys(parsed)).toEqual([
      'productCode', 'productType', 'name', 'description', 'active', 'minAmount', 'maxAmount',
    ])
  })

  it('rejects negative and inverted limit inputs', () => {
    expect(updateProductLimitsInputSchema.safeParse({ minAmount: -1, maxAmount: 10 }).success).toBe(false)
    expect(updateProductLimitsInputSchema.safeParse({ minAmount: 11, maxAmount: 10 }).success).toBe(false)
    expect(updateProductLimitsInputSchema.safeParse({ minAmount: 10, maxAmount: 10 }).success).toBe(true)
  })
})
