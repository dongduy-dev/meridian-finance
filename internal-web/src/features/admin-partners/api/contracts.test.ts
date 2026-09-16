import { describe, expect, it } from 'vitest'
import { importPartnerEmployeesInputSchema, partnerCompanySchema, partnerEmployeeSchema } from './contracts'

describe('Partner administration contracts', () => {
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
