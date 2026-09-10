import { describe, expect, it } from 'vitest'
import { contractFixture } from '@/features/staff-contracts/api/contracts.test'
import {
  disbursementDestinationRevealSchema,
  manualDisbursementConfirmationSchema,
  staffDisbursementCaseSchema,
  staffDisbursementWorkPageSchema,
} from './contracts'

export const applicationId = '11111111-1111-4111-8111-111111111111'
export const contractId = '22222222-2222-4222-8222-222222222222'
export const accountId = '33333333-3333-4333-8333-333333333333'
export const scheduleId = '44444444-4444-4444-8444-444444444444'

export function pendingCase(overrides: Record<string, unknown> = {}) {
  return {
    loanApplicationId: applicationId,
    applicationNumber: 'UCL-20260910-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    requestedAmount: 10_000_000,
    requestedTermMonths: 2,
    applicationStatus: 'DISBURSEMENT_PENDING',
    submittedAt: '2026-09-10T07:00:00',
    currentContract: contractFixture('READY_FOR_DISBURSEMENT'),
    activation: null,
    workStage: 'READY_TO_DISBURSE',
    ...overrides,
  }
}

export function activationFixture(overrides: Record<string, unknown> = {}) {
  return {
    loanAccountId: accountId,
    loanAccountNumber: 'LA-33333333333343338333333333333333',
    loanAccountStatus: 'ACTIVE',
    activatedAt: '2026-09-10T10:00:00',
    disbursedAmount: 10_000_000,
    disbursementValueDate: '2026-09-10',
    firstRepaymentDate: '2026-10-10',
    repaymentScheduleId: scheduleId,
    scheduleType: 'FINAL',
    scheduleVersion: 1,
    scheduleItems: [
      { installmentNumber: 1, dueDate: '2026-10-10', principalDue: 5_000_000, interestDue: 150_000, feeDue: 0, totalDue: 5_150_000 },
      { installmentNumber: 2, dueDate: '2026-11-10', principalDue: 5_000_000, interestDue: 150_000, feeDue: 0, totalDue: 5_150_000 },
    ],
    ...overrides,
  }
}

export function disbursedCase() {
  return pendingCase({
    applicationStatus: 'DISBURSED',
    activation: activationFixture(),
    workStage: 'DISBURSED',
  })
}

export function queueFixture(page = 0, overrides: Record<string, unknown> = {}) {
  const contract = contractFixture('READY_FOR_DISBURSEMENT')
  return {
    page,
    size: 25,
    totalElements: 1,
    totalPages: 1,
    items: [{
      loanApplicationId: applicationId,
      applicationNumber: 'UCL-20260910-000001',
      productCode: 'UNSECURED_CONSUMER_LOAN',
      productType: 'UNSECURED',
      requestedAmount: 10_000_000,
      requestedTermMonths: 2,
      applicationStatus: 'DISBURSEMENT_PENDING',
      submittedAt: '2026-09-10T07:00:00',
      currentContract: {
        contractId: contract.contractId,
        contractReference: contract.contractReference,
        contractVersion: contract.contractVersion,
        status: contract.status,
        approvedPrincipal: contract.approvedPrincipal,
        approvedTermMonths: contract.approvedTermMonths,
        repaymentMethod: contract.repaymentMethod,
        readinessConfirmedAt: contract.readinessConfirmedAt,
        disbursementDestination: {
          bankCode: contract.disbursementBankAccount.bankCode,
          bankName: contract.disbursementBankAccount.bankName,
          accountHolderName: contract.disbursementBankAccount.accountHolderName,
          maskedAccountNumber: contract.disbursementBankAccount.maskedAccountNumber,
        },
      },
      workStage: 'READY_TO_DISBURSE',
      ...overrides,
    }],
  }
}

export function confirmationFixture(idempotentReplay = false) {
  return {
    loanApplicationId: applicationId,
    applicationStatus: 'DISBURSED',
    ...activationFixture(),
    manualDisbursementId: '55555555-5555-4555-8555-555555555555',
    idempotentReplay,
  }
}

describe('Staff disbursement runtime contracts', () => {
  it('accepts safe pending and completed projections', () => {
    expect(staffDisbursementCaseSchema.parse(pendingCase()).workStage).toBe('READY_TO_DISBURSE')
    expect(staffDisbursementCaseSchema.parse(disbursedCase()).activation?.scheduleType).toBe('FINAL')
    expect(staffDisbursementWorkPageSchema.parse(queueFixture()).items).toHaveLength(1)
    expect(manualDisbursementConfirmationSchema.parse(confirmationFixture()).idempotentReplay).toBe(false)
  })

  it('rejects unmasked queue destinations and malformed dates', () => {
    expect(() => staffDisbursementWorkPageSchema.parse(queueFixture(0, {
      currentContract: {
        ...queueFixture().items[0]!.currentContract,
        disbursementDestination: {
          ...queueFixture().items[0]!.currentContract.disbursementDestination,
          maskedAccountNumber: '1234567890',
        },
      },
    }))).toThrow()
    expect(() => manualDisbursementConfirmationSchema.parse(confirmationFixture(false)))
      .not.toThrow()
    expect(() => manualDisbursementConfirmationSchema.parse({
      ...confirmationFixture(), firstRepaymentDate: '10/10/2026',
    })).toThrow()
  })

  it('accepts the sensitive reveal only through its dedicated schema', () => {
    expect(disbursementDestinationRevealSchema.parse({
      contractId,
      contractVersion: 1,
      bankCode: 'VCB',
      bankName: 'Vietcombank',
      accountHolderName: 'MERIDIAN CUSTOMER',
      accountNumber: '1234567890',
    }).accountNumber).toBe('1234567890')
  })
})
