import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  getStaffLoanApplicationCase,
  getStaffLoanApplications,
  revealCustomerIdentityReference,
} from './staff-applications-api'

const item = {
  loanApplicationId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
  applicationNumber: 'SA-20260902-000001',
  productCode: 'SALARY_ADVANCE',
  productType: 'SALARY_BASED',
  requestedAmount: 3_000_000,
  requestedTermMonths: 1,
  status: 'UNDER_REVIEW',
  submittedAt: '2026-09-02T08:00:00',
}

describe('Staff application API', () => {
  it('uses a direct non-replayed POST and validates the minimal protected reveal shape', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({ loanApplicationId: item.loanApplicationId, identityReference: 'FICTIONAL-ID-8901' })
    const manager = { protectedRequest } as unknown as AuthSessionManager
    await expect(revealCustomerIdentityReference(manager, item.loanApplicationId)).resolves.toMatchObject({ identityReference: 'FICTIONAL-ID-8901' })
    expect(protectedRequest).toHaveBeenCalledWith(`/staff/loan-applications/${item.loanApplicationId}/customer-identity-reference/reveal`, { method: 'POST' }, { replayAfterSessionRefresh: false })
    for (const identityReference of ['', 'CONTROL\n8901', 'A'.repeat(101)]) {
      protectedRequest.mockResolvedValue({ loanApplicationId: item.loanApplicationId, identityReference })
      await expect(revealCustomerIdentityReference(manager, item.loanApplicationId)).rejects.toThrow()
    }
    protectedRequest.mockResolvedValue({ loanApplicationId: item.loanApplicationId, identityReference: 'FICTIONAL-ID-8901', ciphertext: 'must not appear' })
    await expect(revealCustomerIdentityReference(manager, item.loanApplicationId)).rejects.toThrow()
  })

  it('uses protected transport and sends only supported index parameters', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({
      page: 2,
      size: 20,
      totalElements: 41,
      totalPages: 3,
      items: [item],
    })
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await getStaffLoanApplications(manager, {
      productCode: 'SALARY_ADVANCE',
      status: 'UNDER_REVIEW',
      page: 2,
      size: 20,
    })

    expect(protectedRequest).toHaveBeenCalledWith(
      '/staff/loan-applications?page=2&size=20&productCode=SALARY_ADVANCE&status=UNDER_REVIEW',
    )
  })

  it('loads the purpose-limited case through protected transport', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({
      ...item,
      formalReviewRecorded: false,
      assignedLoanOfficer: null,
      customerContext: null,
      collateralContext: null,
      customerReadiness: {
        active: true,
        profileComplete: true,
        hasPrimaryActiveBankAccount: true,
        verificationStatus: 'VERIFIED',
      },
      lifecycleHistory: [],
    })
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await getStaffLoanApplicationCase(manager, item.loanApplicationId)

    expect(protectedRequest).toHaveBeenCalledWith(
      `/staff/loan-applications/${item.loanApplicationId}`,
    )
  })
})
