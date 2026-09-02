import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  getStaffLoanApplicationCase,
  getStaffLoanApplications,
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
