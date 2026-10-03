import { describe, expect, it } from 'vitest'
import { staffContractCaseSchema, staffContractWorkPageSchema } from './contracts'

const applicationId = '11111111-1111-4111-8111-111111111111'
const contractId = '22222222-2222-4222-8222-222222222222'

export const actorFixture = {
  userId: '66666666-6666-4666-8666-666666666666',
  displayName: 'Mina Accounting',
  email: 'mina@meridian.local',
}

export function accountingContextFixture() {
  return {
    customer: { customerNumber: 'CUS-000001', fullName: 'Ari Customer', phoneNumber: '0901234567' },
    handoff: {
      approved: { actor: actorFixture, at: '2026-09-07T07:30:00' },
      contractPrepared: { actor: actorFixture, at: '2026-09-07T08:00:00' },
      customerAcknowledgment: {
        mode: 'CUSTOMER_SELF_SERVICE', recordedBy: null,
        at: '2026-09-07T08:30:00', evidenceDocumentVersionId: null,
      },
      readinessConfirmed: null,
      disbursementConfirmed: null,
    },
  }
}

export function contractFixture(status = 'ACKNOWLEDGED') {
  return {
    contractId,
    contractReference: 'MCT-22222222-2222-4222-8222-222222222222',
    contractVersion: 1,
    status,
    approvedPrincipal: 10_000_000,
    approvedTermMonths: 2,
    interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
    flatMonthlyInterestRate: 0.015,
    totalInterest: 300_000,
    feeAmount: 0,
    totalRepaymentAmount: 10_300_000,
    repaymentMethod: 'MONTHLY',
    repaymentPreview: [
      { installmentNumber: 1, principalDue: 5_000_000, interestDue: 150_000, feeDue: 0, totalDue: 5_150_000 },
      { installmentNumber: 2, principalDue: 5_000_000, interestDue: 150_000, feeDue: 0, totalDue: 5_150_000 },
    ],
    disbursementBankAccount: {
      bankCode: 'VCB', bankNameSnapshot: 'Vietcombank', accountHolderName: 'MERIDIAN CUSTOMER',
      maskedAccountNumber: '****7890', primaryAtCapture: true, activeAtCapture: true,
      capturedAt: '2026-09-07T08:00:00',
    },
    preparedAt: '2026-09-07T08:00:00',
    acknowledgedAt: status === 'PREPARED' ? null : '2026-09-07T08:30:00',
    readinessConfirmedAt: status === 'READY_FOR_DISBURSEMENT' ? '2026-09-07T09:00:00' : null,
    availableCustomerAction: status === 'PREPARED' ? 'ACKNOWLEDGE' : null,
  }
}

export function caseFixture(overrides: Record<string, unknown> = {}) {
  return {
    loanApplicationId: applicationId,
    applicationNumber: 'UCL-20260907-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    originationChannel: 'CUSTOMER_DIGITAL',
    requestedAmount: 10_000_000,
    requestedTermMonths: 2,
    applicationStatus: 'CONTRACT_PENDING',
    submittedAt: '2026-09-07T07:00:00',
    currentContract: contractFixture(),
    readiness: {
      loanApplicationId: applicationId,
      contractId,
      contractVersion: 1,
      ready: true,
      blockerCodes: [],
      calculationSemantics: 'POINT_IN_TIME_ADVISORY',
      recomputedDuringConfirmation: true,
    },
    assistedAcknowledgmentEvidence: null,
    accountingContext: accountingContextFixture(),
    workStage: 'READY_TO_CONFIRM',
    ...overrides,
  }
}

describe('Staff contract runtime contracts', () => {
  it('accepts seeded Identity actors in accounting provenance', () => {
    const value = caseFixture()
    const seededActor = {
      ...actorFixture,
      userId: '00000000-0000-0000-0000-000000000303',
    }
    const parsed = staffContractCaseSchema.parse({
      ...value,
      accountingContext: {
        ...value.accountingContext,
        handoff: {
          ...value.accountingContext.handoff,
          approved: { actor: seededActor, at: '2026-09-07T07:30:00' },
        },
      },
    })
    expect(parsed.accountingContext?.handoff.approved.actor.userId).toBe(seededActor.userId)
  })

  it('accepts the safe queue and case projections', () => {
    const value = caseFixture()
    const parsed = staffContractCaseSchema.parse({ ...value, accountingContext: {
      ...value.accountingContext, customer: { ...value.accountingContext.customer,
        customerId: '99999999-9999-4999-8999-999999999999', identityReference: 'SYNTHETIC-IDENTITY',
      },
    } })
    expect(parsed.accountingContext.customer).toEqual(value.accountingContext.customer)
    expect(parsed.accountingContext.customer.phoneNumber).toBe('0901234567')
    expect(staffContractCaseSchema.safeParse({ ...value, accountingContext: {
      ...value.accountingContext, customer: { ...value.accountingContext.customer, phoneNumber: '' },
    } }).success).toBe(false)
    expect(staffContractCaseSchema.parse(caseFixture()).currentContract?.disbursementBankAccount.maskedAccountNumber)
      .toBe('****7890')
    expect(staffContractWorkPageSchema.parse({
      page: 0, size: 25, totalElements: 1, totalPages: 1, items: [caseFixture()],
    }).items).toHaveLength(1)
  })

  it('rejects an unmasked destination and malformed paging', () => {
    const value = caseFixture()
    expect(() => staffContractCaseSchema.parse({
      ...value,
      currentContract: {
        ...value.currentContract,
        disbursementBankAccount: {
          ...value.currentContract.disbursementBankAccount,
          maskedAccountNumber: '1234567890',
        },
      },
    })).toThrow()
    expect(() => staffContractWorkPageSchema.parse({
      page: 0, size: 101, totalElements: 0, totalPages: 0, items: [],
    })).toThrow()
  })
})
