import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { caseFixture, contractFixture } from './contracts.test'
import {
  confirmContractReadiness,
  getStaffContractCase,
  getStaffContractWork,
  prepareLoanContract,
} from './staff-contracts-api'

describe('Staff contract API', () => {
  it('uses only the purpose-limited Staff reads and existing contract commands', async () => {
    const protectedRequest = vi.fn(async (path: string) => path.includes('/staff/contract-work')
      ? { page: 0, size: 25, totalElements: 1, totalPages: 1, items: [caseFixture()] }
      : path.includes('/staff/loan-applications/') ? caseFixture() : contractFixture())
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await getStaffContractWork(manager, { productCode: 'UNSECURED_CONSUMER_LOAN', page: 0, size: 25 })
    await getStaffContractCase(manager, '11111111-1111-4111-8111-111111111111')
    await prepareLoanContract(manager, '11111111-1111-4111-8111-111111111111', {
      preparationRequestId: '33333333-3333-4333-8333-333333333333',
      expectedCurrentContractVersion: 0,
      supersessionReasonCode: null,
    })
    await confirmContractReadiness(manager, '11111111-1111-4111-8111-111111111111', {
      confirmationRequestId: '44444444-4444-4444-8444-444444444444',
      expectedContractVersion: 1,
    })

    expect(protectedRequest).toHaveBeenCalledWith(
      '/staff/contract-work?page=0&size=25&productCode=UNSECURED_CONSUMER_LOAN',
    )
    expect(protectedRequest.mock.calls.map(([path]) => String(path)).some((path) =>
      path.includes('/acknowledgment') || path.includes('/destination') || path.includes('/disbursements')
      || path.includes('/approved-offer'))).toBe(false)
  })
})
