import { describe, expect, it } from 'vitest'
import { staffVerificationCaseSchema } from './contracts'

const common = {
  loanApplicationId: '11111111-1111-4111-8111-111111111111',
  applicationNumber: 'UCL-20260905-000001',
  productType: 'PERSONAL',
  requestedAmount: 20_000_000,
  requestedTermMonths: 12,
  applicationStatus: 'VERIFICATION_PENDING',
  submittedAt: '2026-09-05T08:00:00',
  documentReadiness: { uploadComplete: true, processingReady: true },
  actions: { startAvailable: false, completeAvailable: true },
  correctionTargets: [],
}

const cycle = {
  verificationId: '22222222-2222-4222-8222-222222222222',
  verificationSequence: 1,
  productVerificationResult: 'PENDING_MANUAL_REVIEW',
  createdAt: '2026-09-05T08:01:00',
  reviewedAt: null,
}

describe('staff verification contract', () => {
  it.each([
    {
      ...common,
      productCode: 'SALARY_ADVANCE',
      productVerification: {
        verificationSequence: 1,
        employeeVerificationOutcome: 'ELIGIBLE',
        productVerificationResult: 'VERIFIED',
        totalLimitSnapshot: 10_000_000,
        usedAmountSnapshot: 1_000_000,
        reservedAmountSnapshot: 2_000_000,
        availableLimitSnapshot: 7_000_000,
        verifiedAt: '2026-09-05T08:01:00',
      },
    },
    {
      ...common,
      productCode: 'UNSECURED_CONSUMER_LOAN',
      productVerification: { currentCycle: cycle, history: [cycle], collateral: null },
    },
    {
      ...common,
      productCode: 'COLLATERAL_LOAN',
      productVerification: {
        currentCycle: cycle,
        history: [cycle],
        collateral: {
          collateralType: 'CAR', description: 'Fictional vehicle', estimatedValue: 90_000_000,
          ownershipStatus: 'CUSTOMER_OWNED', conditionNote: 'Serviceable',
        },
      },
    },
  ])('accepts a valid $productCode projection', (fixture) => {
    expect(staffVerificationCaseSchema.parse(fixture).productCode).toBe(fixture.productCode)
  })

  it('keeps a future verification result displayable without enabling it as a known command outcome', () => {
    const parsed = staffVerificationCaseSchema.parse({
      ...common,
      productCode: 'UNSECURED_CONSUMER_LOAN',
      productVerification: {
        currentCycle: { ...cycle, productVerificationResult: 'FUTURE_RESULT' },
        history: [{ ...cycle, productVerificationResult: 'FUTURE_RESULT' }],
        collateral: null,
      },
    })
    expect(parsed.productCode).toBe('UNSECURED_CONSUMER_LOAN')
    if (parsed.productCode !== 'UNSECURED_CONSUMER_LOAN') throw new Error('unexpected product')
    expect(parsed.productVerification.currentCycle.productVerificationResult).toBe('FUTURE_RESULT')
  })

  it('rejects a malformed Collateral projection without its assessment snapshot', () => {
    expect(() => staffVerificationCaseSchema.parse({
      ...common,
      productCode: 'COLLATERAL_LOAN',
      productVerification: { currentCycle: cycle, history: [cycle], collateral: null },
    })).toThrow()
  })
})
