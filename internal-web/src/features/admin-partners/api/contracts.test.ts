import { describe, expect, it } from 'vitest'
import {
  importPartnerEmployeesInputSchema,
  partnerCompanySchema,
  partnerEligibilityReviewPageSchema,
  partnerEmployeeSchema,
} from './contracts'

describe('Partner administration contracts', () => {
  const partnerCompany = {
    id: '22222222-2222-2222-2222-222222222222',
    companyCode: 'AURORA_MANUFACTURING',
    name: 'Aurora Manufacturing Ltd.',
    status: 'ACTIVE',
    salaryAdvancePolicyLimit: 15_000_000,
  }

  it('accepts canonical Meridian deterministic UUID identifiers', () => {
    expect(partnerCompanySchema.parse(partnerCompany).id).toBe(partnerCompany.id)

    expect(partnerEligibilityReviewPageSchema.parse({
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      items: [{
        reviewId: '4144793b-e7b6-4ce6-bc79-360670e8e5f5',
        customerId: 'a01eeb37-6ba8-4b6d-9885-66002f8529ff',
        partnerCompanyId: partnerCompany.id,
        partnerCompanyCode: partnerCompany.companyCode,
        partnerCompanyName: partnerCompany.name,
        effectiveMonth: '2026-09',
        triggerOutcome: 'PENDING_MANUAL_REVIEW',
        requestedEmployeeCode: 'deni-001-0982',
        status: 'PENDING',
        createdAt: '2026-09-24T07:35:33.58384',
        reviewable: true,
        nonReviewableReason: null,
      }],
    }).items[0].partnerCompanyId).toBe(partnerCompany.id)
  })

  it('continues to accept RFC versioned UUID identifiers', () => {
    const id = '4144793b-e7b6-4ce6-bc79-360670e8e5f5'
    expect(partnerCompanySchema.parse({ ...partnerCompany, id }).id).toBe(id)
  })

  it.each([
    'not-a-uuid',
    '22222222-2222-2222',
    'gggggggg-2222-2222-2222-222222222222',
  ])('rejects malformed UUID identifier %s', (id) => {
    expect(partnerCompanySchema.safeParse({ ...partnerCompany, id }).success).toBe(false)
  })

  it('preserves unknown returned states for safe presentation', () => {
    expect(partnerCompanySchema.parse({
      id: '11111111-1111-4111-8111-111111111111', companyCode: 'ACME', name: 'Acme',
      status: 'FUTURE_STATUS', salaryAdvancePolicyLimit: 1,
    }).status).toBe('FUTURE_STATUS')
    expect(partnerEmployeeSchema.parse({
      id: '22222222-2222-4222-8222-222222222222',
      partnerCompanyId: '11111111-1111-4111-8111-111111111111',
      importBatchId: '33333333-3333-4333-8333-333333333333', employeeCode: 'EMP-1',
      identityReference: 'ID-1', salaryAmount: 1, salaryAdvanceLimit: 1,
      employmentStatus: 'FUTURE_STATUS', active: true,
    }).employmentStatus).toBe('FUTURE_STATUS')
  })

  it('rejects impossible months and invalid obvious row shapes', () => {
    const row = { employeeCode: 'EMP-1', identityReference: 'ID-1', salaryAmount: 1, salaryAdvanceLimit: 1, employmentStatus: 'ACTIVE', active: true }
    expect(importPartnerEmployeesInputSchema.safeParse({ effectiveMonth: '2026-99', rows: [row] }).success).toBe(false)
    expect(importPartnerEmployeesInputSchema.safeParse({ effectiveMonth: '2026-09', rows: [{ ...row, salaryAmount: -1 }] }).success).toBe(false)
  })
})
