import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from './errors'
import { operatorErrorMessage } from './operator-error-message'

const fallback = 'Result unconfirmed. Review the latest payment evidence before retrying this payment.'
const apiError = (status: number, code: string) => new ApiError(status, code,
  'Private database failure: loanApplicationId=secret; raw sensitive value', '/private', '2026-10-07T00:00:00Z')

describe('operator error presentation', () => {
  it.each([
    ['STALE_DOCUMENT_VERSION', /document version changed/i],
    ['STALE_REVIEW_RECOMMENDATION', /recommendation changed/i],
    ['CONTRACT_VERSION_STALE', /contract version changed/i],
    ['CORRECTION_TASK_PROOF_MISSING', /correction evidence is missing/i],
    ['REPAYMENT_EXCEEDS_OUTSTANDING', /payment exceeds the outstanding balance/i],
  ])('explains the business restriction for %s without API prose', (code, message) => {
    const text = operatorErrorMessage(apiError(409, code), fallback)
    expect(text).toMatch(message)
    expect(text).not.toMatch(/database|secret|loanApplicationId/)
  })

  it.each([new NetworkError(), apiError(500, 'REPAYMENT_EXCEEDS_OUTSTANDING'),
    apiError(408, 'VALIDATION_ERROR'), apiError(409, 'FUTURE_FAILURE'), new Error('private')])(
    'preserves uncertain-result recovery for %s', (error) => {
      expect(operatorErrorMessage(error, fallback)).toBe(fallback)
    })

  it('conceals missing records and generic forbidden reasons', () => {
    expect(operatorErrorMessage(apiError(404, 'CUSTOMER_EXISTS_ELSEWHERE'), fallback)).toBe('This record is not available in your current workspace.')
    expect(operatorErrorMessage(apiError(403, 'PRIVATE_PERMISSION'), fallback)).toMatch(/do not have access/)
  })

  it('distinguishes credit and correction separation of duties without naming another actor', () => {
    expect(operatorErrorMessage(apiError(403, 'MAKER_CHECKER_VIOLATION'), fallback)).toMatch(/different authorized Approver/)
    expect(operatorErrorMessage(apiError(403, 'STAFF_CORRECTION_MAKER_CHECKER_VIOLATION'), fallback)).toMatch(/Another authorized Staff member/)
  })
})
