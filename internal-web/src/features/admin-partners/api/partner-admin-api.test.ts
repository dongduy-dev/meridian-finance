import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getPartnerImportBatch } from './partner-admin-api'
import { partnerAdminKeys, partnerImportBatchQuery } from './queries'

const companyId = '22222222-2222-2222-2222-222222222222'
const batchId = '33333333-3333-4333-8333-333333333333'
const otherId = '44444444-4444-4444-8444-444444444444'
const detail = { id: batchId, partnerCompanyId: companyId, effectiveMonth: '2026-09', status: 'FAILED',
  validRowCount: 1, invalidRowCount: 1, employees: [], rejections: [] }

describe('selected Partner import reads', () => {
  it('requests one exact batch and rejects mismatched response identities', async () => {
    const protectedRequest = vi.fn().mockResolvedValue(detail)
    const manager = { protectedRequest } as unknown as AuthSessionManager
    expect(await getPartnerImportBatch(manager, companyId, batchId)).toEqual(detail)
    expect(protectedRequest).toHaveBeenCalledExactlyOnceWith(`/partner-companies/${companyId}/employee-import-batches/${batchId}`)
    protectedRequest.mockResolvedValueOnce({ ...detail, id: otherId })
    await expect(getPartnerImportBatch(manager, companyId, batchId)).rejects.toThrow('identity mismatch')
    protectedRequest.mockResolvedValueOnce({ ...detail, partnerCompanyId: otherId })
    await expect(getPartnerImportBatch(manager, companyId, batchId)).rejects.toThrow('identity mismatch')
  })

  it('isolates company and batch keys, validates identifiers and retains no unused sensitive data', () => {
    const manager = {} as AuthSessionManager
    const query = partnerImportBatchQuery(manager, companyId, batchId, true)
    expect(query.queryKey).toEqual(partnerAdminKeys.importBatch(companyId, batchId))
    expect(query.queryKey).not.toEqual(partnerAdminKeys.importBatch(otherId, batchId))
    expect(query.queryKey).not.toEqual(partnerAdminKeys.importBatch(companyId, otherId))
    expect(query.enabled).toBe(true)
    expect(query.staleTime).toBe(0)
    expect(query.gcTime).toBe(0)
    expect(partnerImportBatchQuery(manager, companyId, batchId, false).enabled).toBe(false)
    expect(partnerImportBatchQuery(manager, 'invalid', batchId, true).enabled).toBe(false)
    expect(partnerImportBatchQuery(manager, companyId, 'invalid', true).enabled).toBe(false)
  })

  it('accepts equivalent canonical UUID casing in a direct route', async () => {
    const id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    const manager = { protectedRequest: vi.fn().mockResolvedValue({ ...detail, partnerCompanyId: id }) } as unknown as AuthSessionManager
    expect((await getPartnerImportBatch(manager, id.toUpperCase(), batchId)).partnerCompanyId).toBe(id)
  })
})
