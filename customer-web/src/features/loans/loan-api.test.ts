import { describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'

import type { ApiClient, ProtectedRequestCoordinator } from '@/lib/api'

import { createLoanApi } from './loan-api'

const applicationId = '11111111-1111-4111-8111-111111111111'
const accountId = '22222222-2222-4222-8222-222222222222'
const scheduleId = '33333333-3333-4333-8333-333333333333'
const transactionId = '44444444-4444-4444-8444-444444444444'
const scheduleItemId = '55555555-5555-4555-8555-555555555555'

const servicingAmounts = {
  principalPaid: 1_000_000,
  interestPaid: 100_000,
  feePaid: 0,
  totalPaid: 1_100_000,
  principalOutstanding: 5_000_000,
  interestOutstanding: 440_000,
  feeOutstanding: 0,
  totalOutstanding: 5_440_000,
}

const loanAccount = {
  loanApplicationId: applicationId,
  loanAccountId: accountId,
  accountNumber: 'LA-20260901-000001',
  status: 'FUTURE_ACCOUNT_STATE',
  activatedAt: '2026-09-01T08:00:00',
  originatedPrincipal: 6_000_000,
  approvedTermMonths: 6,
  totalInterest: 540_000,
  totalFee: 0,
  totalRepayment: 6_540_000,
  servicing: {
    ...servicingAmounts,
    servicingEvaluationDate: '2026-09-01',
    lastPaymentValueDate: null,
    lastPaymentRecordedAt: null,
  },
  disbursementDestination: {
    bankCode: 'VCB',
    bankName: 'Vietcombank',
    accountHolderName: 'MERIDIAN CUSTOMER',
    maskedAccountNumber: '********',
  },
  finalRepaymentSchedule: {
    scheduleId,
    scheduleType: 'FINAL',
    version: 1,
    firstDueDate: '2026-09-30',
    lastDueDate: '2027-02-28',
    items: [{
      installmentNumber: 1,
      dueDate: '2026-09-30',
      principalDue: 1_000_000,
      interestDue: 90_000,
      feeDue: 0,
      totalDue: 1_090_000,
      servicing: {
        ...servicingAmounts,
        status: 'FUTURE_INSTALLMENT_STATE',
        statusEvaluationDate: '2026-09-01',
        lastPaymentValueDate: null,
        lastPaymentRecordedAt: null,
      },
    }],
  },
}

const historyPage = {
  page: 2,
  size: 20,
  totalElements: 45,
  totalPages: 3,
  items: [{
    repaymentTransactionId: transactionId,
    receivedAmount: 1_100_000,
    paymentValueDate: '2026-09-30',
    recordedAt: '2026-09-30T08:15:00',
    principalAllocated: 1_000_000,
    principalReleased: 0,
    resultingLoanAccountStatus: 'FUTURE_ACCOUNT_STATE',
    accountBalance: {
      ...servicingAmounts,
      lastPaymentValueDate: '2026-09-30',
      lastPaymentRecordedAt: '2026-09-30T08:15:00',
      servicingEvaluationDate: '2026-09-30',
      status: 'FUTURE_BALANCE_STATE',
    },
    allocations: [{
      sequence: 1,
      repaymentScheduleItemId: scheduleItemId,
      installmentNumber: 1,
      component: 'FUTURE_COMPONENT',
      allocatedAmount: 90_000,
    }],
    affectedInstallments: [{
      repaymentScheduleItemId: scheduleItemId,
      installmentNumber: 1,
      dueDate: '2026-09-30',
      previousStatus: 'NOT_DUE',
      resultingStatus: 'FUTURE_INSTALLMENT_STATE',
      evaluationDate: '2026-09-30',
      ...servicingAmounts,
      lastPaymentValueDate: '2026-09-30',
      lastPaymentRecordedAt: '2026-09-30T08:15:00',
      statusChanged: true,
    }],
  }],
}

function apiWith(response: unknown) {
  const request = vi.fn(async (path: string, options?: RequestInit) => {
    void path
    void options
    return response
  })
  const coordinator: ProtectedRequestCoordinator = {
    requestProtected: vi.fn((operation) => operation('protected-customer-token')),
  }
  return { api: createLoanApi(coordinator, { request } as ApiClient), request, coordinator }
}

describe('Customer LoanAccount API boundary', () => {
  it('uses the protected coordinator and preserves unknown status strings and returned balances', async () => {
    const response = [{
      loanApplicationId: applicationId,
      loanAccountId: accountId,
      accountNumber: 'LA-20260830-000001',
      applicationNumber: 'UCL-20260830-000001',
      productCode: 'UNSECURED_CONSUMER_LOAN',
      productType: 'UNSECURED',
      status: 'FUTURE_ACCOUNT_STATE',
      activatedAt: '2026-08-30T08:00:00',
      originatedPrincipal: 10_000_000,
      totalPaid: 1_234_567,
      totalOutstanding: 9_876_543,
      servicingActive: true,
    }]
    const request = vi.fn(async (path: string, options?: RequestInit) => {
      void path
      void options
      return response
    })
    const coordinator: ProtectedRequestCoordinator = {
      requestProtected: vi.fn((operation) => operation('protected-customer-token')),
    }
    const api = createLoanApi(coordinator, { request } as ApiClient)

    const accounts = await api.getOwnLoanAccounts()

    expect(accounts[0]).toMatchObject({
      status: 'FUTURE_ACCOUNT_STATE',
      totalPaid: 1_234_567,
      totalOutstanding: 9_876_543,
      servicingActive: true,
    })
    expect(coordinator.requestProtected).toHaveBeenCalledOnce()
    const [path, options] = request.mock.calls[0]!
    expect(path).toBe('/loan-accounts')
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer protected-customer-token')
  })

  it('parses LoanAccount detail, final schedule, installment servicing, nullable payment fields, and the masked destination', async () => {
    const { api, request } = apiWith(loanAccount)

    const result = await api.getLoanAccount(applicationId)

    expect(request).toHaveBeenCalledWith(
      `/loan-applications/${applicationId}/loan-account`,
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
    expect(result).toMatchObject({
      status: 'FUTURE_ACCOUNT_STATE',
      servicing: {
        totalPaid: 1_100_000,
        totalOutstanding: 5_440_000,
        lastPaymentValueDate: null,
        lastPaymentRecordedAt: null,
      },
      disbursementDestination: { maskedAccountNumber: '********' },
      finalRepaymentSchedule: {
        scheduleType: 'FINAL',
        items: [{ servicing: { status: 'FUTURE_INSTALLMENT_STATE' } }],
      },
    })
    expect(result.disbursementDestination).not.toHaveProperty('accountNumber')
  })

  it('parses immutable repayment history, allocations, outcomes, and unknown status values', async () => {
    const { api, request } = apiWith(historyPage)

    const result = await api.getRepaymentHistory(applicationId, 2, 20)

    expect(request).toHaveBeenCalledWith(
      `/loan-applications/${applicationId}/repayments?page=2&size=20`,
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
    expect(result).toMatchObject({
      page: 2,
      size: 20,
      items: [{
        resultingLoanAccountStatus: 'FUTURE_ACCOUNT_STATE',
        accountBalance: { totalOutstanding: 5_440_000, status: 'FUTURE_BALANCE_STATE' },
        allocations: [{ component: 'FUTURE_COMPONENT', allocatedAmount: 90_000 }],
        affectedInstallments: [{ resultingStatus: 'FUTURE_INSTALLMENT_STATE' }],
      }],
    })
    expect(result.items[0]).not.toHaveProperty('externalPaymentReference')
  })

  it('rejects invalid application IDs and pagination before making a request', async () => {
    const first = apiWith(loanAccount)
    await expect(first.api.getLoanAccount('not-a-uuid')).rejects.toBeInstanceOf(ZodError)
    expect(first.request).not.toHaveBeenCalled()

    const second = apiWith(historyPage)
    await expect(second.api.getRepaymentHistory(applicationId, -1, 20)).rejects.toBeInstanceOf(ZodError)
    await expect(second.api.getRepaymentHistory(applicationId, 0, 101)).rejects.toBeInstanceOf(ZodError)
    expect(second.request).not.toHaveBeenCalled()
  })

  it('rejects incomplete destination and malformed date-only fields at the boundary', async () => {
    const destination = apiWith({
      ...loanAccount,
      disbursementDestination: { bankCode: 'VCB' },
    })
    await expect(destination.api.getLoanAccount(applicationId)).rejects.toBeInstanceOf(ZodError)

    const date = apiWith({
      ...loanAccount,
      finalRepaymentSchedule: { ...loanAccount.finalRepaymentSchedule, firstDueDate: '09/30/2026' },
    })
    await expect(date.api.getLoanAccount(applicationId)).rejects.toBeInstanceOf(ZodError)
  })
})
