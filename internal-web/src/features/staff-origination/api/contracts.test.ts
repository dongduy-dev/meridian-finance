import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { assistedOriginationSchema, intakeEvidenceSchema, staffCustomerSchema } from './contracts'
import { getIntakeEvidenceContent, listEvidence, uploadEvidence } from './staff-origination-api'

describe('Staff-assisted origination contracts', () => {
  it('reads exact intake bytes on demand through a private binary request', async () => {
    const response = { blob: new Blob(['private']), contentType: 'application/pdf' }
    const protectedRequest = vi.fn().mockResolvedValue(response)
    const manager = { protectedRequest } as unknown as AuthSessionManager
    expect(await getIntakeEvidenceContent(manager, 'case', 'UCL_PAPER_APPLICATION', 'version')).toBe(response)
    expect(protectedRequest).toHaveBeenCalledWith('/staff/assisted-originations/case/evidence/UCL_PAPER_APPLICATION/versions/version/content', { responseType: 'blob', cache: 'no-store' })
  })

  it('rejects foreign-case metadata and inconsistent or storage-bearing version metadata', async () => {
    const caseId = '11111111-1111-4111-8111-111111111111'
    const versionId = '33333333-3333-4333-8333-333333333333'
    const item = { intakeDocumentId: '22222222-2222-4222-8222-222222222222', assistedOriginationCaseId: caseId,
      evidenceType: 'UCL_PAPER_APPLICATION', currentVersionId: versionId,
      versions: [{ intakeDocumentVersionId: versionId, versionNumber: 1, originalFilename: 'paper.pdf', detectedMimeType: 'application/pdf', byteSize: 10, uploadedAt: '2026-10-07T10:00:00' }] }
    expect(intakeEvidenceSchema.safeParse({ ...item, currentVersionId: '44444444-4444-4444-8444-444444444444' }).success).toBe(false)
    expect(intakeEvidenceSchema.safeParse({ ...item, versions: [...item.versions, ...item.versions] }).success).toBe(false)
    expect(intakeEvidenceSchema.safeParse({ ...item, versions: [{ ...item.versions[0], storageKey: 'private-key' }] }).success).toBe(false)
    const protectedRequest = vi.fn().mockResolvedValue([{ ...item, assistedOriginationCaseId: '55555555-5555-4555-8555-555555555555' }])
    await expect(listEvidence({ protectedRequest } as unknown as AuthSessionManager, caseId)).rejects.toThrow('Intake evidence metadata is inconsistent.')
  })

  it('accepts the controlled intake and evidence vocabulary', () => {
    expect(assistedOriginationSchema.parse({
      assistedOriginationCaseId: '11111111-1111-4111-8111-111111111111',
      productCode: 'UNSECURED_CONSUMER_LOAN', customerId: null, status: 'OPEN',
      createdByStaffUserId: '00000000-0000-0000-0000-000000000304',
      createdAt: '2026-09-17T08:00:00', updatedAt: '2026-09-17T08:00:00', terminalAt: null,
    }).status).toBe('OPEN')
    expect(intakeEvidenceSchema.parse({
      intakeDocumentId: '33333333-3333-4333-8333-333333333333',
      assistedOriginationCaseId: '11111111-1111-4111-8111-111111111111',
      evidenceType: 'CUSTOMER_IDENTITY', currentVersionId: null, versions: [],
    }).evidenceType).toBe('CUSTOMER_IDENTITY')
    expect(staffCustomerSchema.parse({
      customerId: '99999999-9999-9999-9999-999999999999', customerNumber: 'CUS-000000001',
      status: 'ACTIVE', verificationStatus: 'UNVERIFIED', profileCompletionStatus: 'COMPLETE',
      primaryActiveBankAccountPresent: false,
      profile: { fullName: 'Paper Customer', phoneNumber: '0900', residentialAddress: 'Address',
        employmentStatus: 'EMPLOYED', employerName: null, termsConsentAccepted: true,
        dataProcessingConsentAccepted: true },
    }).customerId).toBe('99999999-9999-9999-9999-999999999999')
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
    const protectedRequest = vi.fn().mockResolvedValue({
      intakeDocumentVersionId: '66666666-6666-4666-8666-666666666666',
      versionNumber: 2,
      originalFilename: 'replacement.pdf',
      detectedMimeType: 'application/pdf',
      byteSize: 14,
      uploadedAt: '2026-09-17T08:30:00',
    })
    const manager = { protectedRequest } as unknown as AuthSessionManager
    const file = new File(['%PDF-fictional'], 'replacement.pdf', { type: 'application/pdf' })

    const result = await uploadEvidence(
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
    expect(result.versionNumber).toBe(2)
  })
})
