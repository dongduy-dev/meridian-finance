import { QueryClient } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthSessionManager } from '@/features/auth/model/auth-session'
import * as authApi from '@/features/auth/api/auth-api'
import { clearAccessToken } from '@/features/auth/model/access-credential'
import { decideIdentity } from './api'

vi.mock('@/features/auth/api/auth-api', async importOriginal => ({
  ...await importOriginal<typeof import('@/features/auth/api/auth-api')>(), login: vi.fn(), refresh: vi.fn(),
}))
const id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const versionId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const result = { verificationId: id, sequence: 1, customerNumber: 'CUS-001', fullName: 'Ari Fictional',
  source: 'CUSTOMER_DIGITAL', method: 'MANUAL_STAFF_DOCUMENT_REVIEW', status: 'VERIFIED', rejectionReason: null,
  submittedAt: '2026-10-03T10:00:00', completedAt: '2026-10-03T11:00:00', evidence: null }
afterEach(() => { vi.clearAllMocks(); vi.restoreAllMocks(); vi.unstubAllGlobals(); clearAccessToken() })

it.each([true, false])('opts the verify=%s decision out of session replay with the exact evidence and decision payload', async verify => {
  const protectedRequest = vi.fn().mockResolvedValue(result)
  await decideIdentity({ protectedRequest } as unknown as AuthSessionManager, id, verify, versionId, 'FICTIONAL-REFERENCE', 'UNREADABLE_EVIDENCE')
  expect(protectedRequest).toHaveBeenCalledWith(`/staff/customer-identity-verifications/${id}/${verify ? 'verify' : 'reject'}`, {
    method: 'POST', body: { requestId: expect.any(String), documentVersionId: versionId,
      ...(verify ? { presentedIdentityReference: 'FICTIONAL-REFERENCE' } : { rejectionReason: 'UNREADABLE_EVIDENCE' }) },
  }, { replayAfterSessionRefresh: false })
})

it.each([true, false])('refreshes a recognized 401 but sends only one verify=%s business POST', async verify => {
  const session: authApi.AuthResponse = { tokenType: 'Bearer', accessToken: 'fictional-token', expiresAt: '2026-10-06T00:00:00Z',
    userId: id, email: 'staff@meridian.local', userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['customer:identity:verify'] }
  vi.mocked(authApi.login).mockResolvedValue(session)
  vi.mocked(authApi.refresh).mockResolvedValue({ ...session, accessToken: 'rotated-fictional-token' })
  const client = new QueryClient()
  const manager = new AuthSessionManager(client)
  await manager.login(session.email, 'fictional-password')
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({
    timestamp: 'now', status: 401, errorCode: 'TOKEN_EXPIRED', message: 'Expired.', path: '/decision',
  }), { status: 401, headers: { 'Content-Type': 'application/json' } })))
  vi.stubGlobal('fetch', fetchMock)
  await expect(decideIdentity(manager, id, verify, versionId, 'FICTIONAL-REFERENCE', 'UNREADABLE_EVIDENCE'))
    .rejects.toMatchObject({ status: 401, errorCode: 'TOKEN_EXPIRED' })
  expect(fetchMock).toHaveBeenCalledTimes(1)
  expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'POST' })
  expect(authApi.refresh).toHaveBeenCalledTimes(1)
  expect(manager.getSnapshot().status).toBe('authenticated')
  client.clear()
})
