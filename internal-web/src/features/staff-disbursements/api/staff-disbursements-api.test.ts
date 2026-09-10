import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  confirmationFixture,
  pendingCase,
  queueFixture,
} from './contracts.test'
import {
  confirmManualDisbursement,
  getStaffDisbursementCase,
  getStaffDisbursementWork,
  revealDisbursementDestination,
} from './staff-disbursements-api'

describe('Staff disbursement API', () => {
  it('uses purpose-limited Staff reads and exact existing commands', async () => {
    const protectedRequest = vi.fn(async (path: string) => {
      if (path.includes('/staff/disbursement-work')) return queueFixture()
      if (path.includes('/staff/loan-applications/')) return pendingCase()
      if (path.includes('/reveal')) return {
        contractId: '22222222-2222-4222-8222-222222222222', contractVersion: 1,
        bankCode: 'VCB', bankName: 'Vietcombank', accountHolderName: 'MERIDIAN CUSTOMER',
        accountNumber: '1234567890',
      }
      return confirmationFixture()
    })
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await getStaffDisbursementWork(manager, { productCode: 'UNSECURED_CONSUMER_LOAN', page: 0, size: 25 })
    await getStaffDisbursementCase(manager, '11111111-1111-4111-8111-111111111111')
    await revealDisbursementDestination(manager, '11111111-1111-4111-8111-111111111111', 1)
    await confirmManualDisbursement(manager, '11111111-1111-4111-8111-111111111111', {
      requestId: '66666666-6666-4666-8666-666666666666', expectedContractVersion: 1,
      externalTransferReference: 'BANK-REFERENCE', disbursementValueDate: '2026-09-10',
      firstRepaymentDate: '2026-10-10',
    })

    expect(protectedRequest).toHaveBeenCalledWith(
      '/staff/disbursement-work?page=0&size=25&productCode=UNSECURED_CONSUMER_LOAN',
    )
    expect(protectedRequest).toHaveBeenCalledWith(
      '/staff/loan-applications/11111111-1111-4111-8111-111111111111/disbursement',
    )
    expect(protectedRequest).toHaveBeenCalledWith(
      '/loan-applications/11111111-1111-4111-8111-111111111111/contracts/current/disbursement-destination/reveal',
      { method: 'POST', body: { expectedContractVersion: 1 } },
    )
    expect(protectedRequest).toHaveBeenCalledWith(
      '/loan-applications/11111111-1111-4111-8111-111111111111/disbursements',
      {
        method: 'POST',
        body: {
          requestId: '66666666-6666-4666-8666-666666666666',
          expectedContractVersion: 1,
          externalTransferReference: 'BANK-REFERENCE',
          disbursementValueDate: '2026-09-10',
          firstRepaymentDate: '2026-10-10',
        },
      },
    )
    expect(protectedRequest.mock.calls.some(([path]) => String(path).includes('/staff/loan-applications?')))
      .toBe(false)
  })
})
