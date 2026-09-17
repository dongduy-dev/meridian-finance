import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { assistedOriginationSchema, intakeEvidenceSchema, staffCustomerSchema } from './contracts'
import { uploadEvidence } from './staff-origination-api'

describe('Staff-assisted origination contracts', () => {
  it('accepts the controlled intake and evidence vocabulary', () => {
    expect(assistedOriginationSchema.parse({
      assistedOriginationCaseId: '11111111-1111-4111-8111-111111111111',
      productCode: 'UNSECURED_CONSUMER_LOAN', customerId: null, status: 'OPEN',
      createdByStaffUserId: '22222222-2222-4222-8222-222222222222',
      createdAt: '2026-09-17T08:00:00', updatedAt: '2026-09-17T08:00:00', terminalAt: null,
    }).status).toBe('OPEN')
    expect(intakeEvidenceSchema.parse({
      intakeDocumentId: '33333333-3333-4333-8333-333333333333',
      assistedOriginationCaseId: '11111111-1111-4111-8111-111111111111',
      evidenceType: 'CUSTOMER_IDENTITY', currentVersionId: null, versions: [],
    }).evidenceType).toBe('CUSTOMER_IDENTITY')
  })

  it('rejects Salary Advance and responses containing raw identity fields', () => {
    expect(() => assistedOriginationSchema.parse({
      assistedOriginationCaseId: '11111111-1111-4111-8111-111111111111',
      productCode: 'SALARY_ADVANCE', customerId: null, status: 'OPEN',
      createdByStaffUserId: '22222222-2222-4222-8222-222222222222',
      createdAt: '2026-09-17T08:00:00', updatedAt: '2026-09-17T08:00:00', terminalAt: null,
    })).toThrow()
    expect(() => staffCustomerSchema.parse({
      customerId: '44444444-4444-4444-8444-444444444444', customerNumber: 'CUS-000000001',
      status: 'ACTIVE', verificationStatus: 'UNVERIFIED', profileCompletionStatus: 'COMPLETE',
      primaryActiveBankAccountPresent: false,
      identityReference: '012345678901',
      profile: { fullName: 'Paper Customer', phoneNumber: '0900', residentialAddress: 'Address',
        employmentStatus: 'EMPLOYED', employerName: null, termsConsentAccepted: true,
        dataProcessingConsentAccepted: true },
    })).toThrow()
  })

  it('sends replacement evidence as multipart data with replay and expected-version controls', async () => {
    const protectedRequest = vi.fn().mockResolvedValue(undefined)
    const manager = { protectedRequest } as unknown as AuthSessionManager
    const file = new File(['%PDF-fictional'], 'replacement.pdf', { type: 'application/pdf' })

    await uploadEvidence(
      manager,
      '11111111-1111-4111-8111-111111111111',
      'UCL_PAPER_APPLICATION',
      file,
      '77777777-7777-4777-8777-777777777777',
      '33333333-3333-4333-8333-333333333333',
    )

    expect(protectedRequest).toHaveBeenCalledOnce()
    const [path, options] = protectedRequest.mock.calls[0] as [string, { method: string; body: FormData }]
    expect(path).toBe('/staff/assisted-originations/11111111-1111-4111-8111-111111111111/evidence/UCL_PAPER_APPLICATION/versions')
    expect(options.method).toBe('POST')
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.body.get('file')).toBe(file)
    expect(options.body.get('uploadRequestId')).toBe('77777777-7777-4777-8777-777777777777')
    expect(options.body.get('expectedCurrentVersionId')).toBe('33333333-3333-4333-8333-333333333333')
  })
})
