import { describe, expect, it } from 'vitest'
import {
  loanAccountSchema,
  recordRepaymentResultSchema,
  repaymentHistoryPageSchema,
  staffServicingWorkPageSchema,
} from './contracts'

export const applicationId = '11111111-1111-4111-8111-111111111111'
export const accountId = '22222222-2222-4222-8222-222222222222'
export const scheduleId = '33333333-3333-4333-8333-333333333333'
export const scheduleItemId = '44444444-4444-4444-8444-444444444444'
export const transactionId = '55555555-5555-4555-8555-555555555555'

export function accountFixture(status = 'ACTIVE') {
  return {
    loanApplicationId: applicationId,
    loanAccountId: accountId,
    accountNumber: 'LA-20260910-000001',
    status,
    activatedAt: '2026-09-01T10:00:00',
    originatedPrincipal: 1000,
    approvedTermMonths: 1,
    totalInterest: 100,
    totalFee: 100,
    totalRepayment: 1200,
    servicing: {
      principalPaid: 0,
      interestPaid: 0,
      feePaid: 100,
      totalPaid: 100,
      principalOutstanding: 1000,
      interestOutstanding: 100,
      feeOutstanding: 0,
      totalOutstanding: 1100,
      servicingEvaluationDate: '2026-09-10',
      lastPaymentValueDate: '2026-09-09',
      lastPaymentRecordedAt: '2026-09-09T08:00:00',
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
      firstDueDate: '2026-10-01',
      lastDueDate: '2026-10-01',
      items: [{
        installmentNumber: 1,
        dueDate: '2026-10-01',
        principalDue: 1000,
        interestDue: 100,
        feeDue: 100,
        totalDue: 1200,
        servicing: {
          principalPaid: 0,
          interestPaid: 0,
          feePaid: 100,
          totalPaid: 100,
          principalOutstanding: 1000,
          interestOutstanding: 100,
          feeOutstanding: 0,
          totalOutstanding: 1100,
          status: 'NOT_DUE',
          statusEvaluationDate: '2026-09-10',
          lastPaymentValueDate: '2026-09-09',
          lastPaymentRecordedAt: '2026-09-09T08:00:00',
        },
      }],
    },
  }
}

export function queueFixture(page = 0, status = 'ACTIVE') {
  return {
    page,
    size: 25,
    totalElements: 26,
    totalPages: 2,
    items: [{
      loanApplicationId: applicationId,
      loanAccountId: accountId,
      applicationNumber: 'UCL-20260910-000001',
      accountNumber: 'LA-20260910-000001',
      productCode: 'UNSECURED_CONSUMER_LOAN',
      productType: 'UNSECURED',
      accountStatus: status,
      activatedAt: '2026-09-01T10:00:00',
      originatedPrincipal: 1000,
      totalPaid: 100,
      totalOutstanding: 1100,
      servicingEvaluationDate: '2026-09-10',
      lastPaymentValueDate: '2026-09-09',
      lastPaymentRecordedAt: '2026-09-09T08:00:00',
    }],
  }
}

export function repaymentResultFixture(overrides: Record<string, unknown> = {}) {
  return {
    loanApplicationId: applicationId,
    loanAccountId: accountId,
    repaymentTransactionId: transactionId,
    finalScheduleId: scheduleId,
    receivedAmount: 100,
    paymentValueDate: '2026-09-10',
    recordedAt: '2026-09-10T10:00:00',
    principalAllocated: 0,
    principalReleased: 0,
    resultingLoanAccountStatus: 'ACTIVE',
    accountBalance: { ...accountFixture().servicing, status: 'ACTIVE' },
    allocations: [{
      sequence: 1,
      repaymentScheduleItemId: scheduleItemId,
      installmentNumber: 1,
      component: 'FEE',
      allocatedAmount: 100,
    }],
    affectedInstallments: [{
      repaymentScheduleItemId: scheduleItemId,
      installmentNumber: 1,
      dueDate: '2026-10-01',
      previousStatus: 'NOT_DUE',
      resultingStatus: 'NOT_DUE',
      evaluationDate: '2026-09-10',
      principalPaid: 0,
      interestPaid: 0,
      feePaid: 100,
      totalPaid: 100,
      principalOutstanding: 1000,
      interestOutstanding: 100,
      feeOutstanding: 0,
      totalOutstanding: 1100,
      lastPaymentValueDate: '2026-09-10',
      lastPaymentRecordedAt: '2026-09-10T10:00:00',
      statusChanged: false,
    }],
    idempotentReplay: false,
    ...overrides,
  }
}

export function historyFixture() {
  const item = repaymentHistoryPageSchema.shape.items.element.parse(repaymentResultFixture())
  return { page: 0, size: 20, totalElements: 1, totalPages: 1, items: [item] }
}

describe('Staff servicing contracts', () => {
  it('parses safe queue, account, history, and repayment outcome contracts', () => {
    expect(staffServicingWorkPageSchema.parse(queueFixture()).items).toHaveLength(1)
    expect(loanAccountSchema.parse(accountFixture()).disbursementDestination.maskedAccountNumber).toBe('********')
    expect(repaymentHistoryPageSchema.parse(historyFixture()).items).toHaveLength(1)
    expect(recordRepaymentResultSchema.parse(repaymentResultFixture()).idempotentReplay).toBe(false)
  })

  it('rejects an unmasked destination and invalid serviceable queue balance', () => {
    expect(() => loanAccountSchema.parse({
      ...accountFixture(),
      disbursementDestination: { ...accountFixture().disbursementDestination, maskedAccountNumber: '1234567890' },
    })).toThrow()
    expect(() => staffServicingWorkPageSchema.parse({
      ...queueFixture(),
      items: [{ ...queueFixture().items[0], totalOutstanding: 0 }],
    })).toThrow()
  })
})
