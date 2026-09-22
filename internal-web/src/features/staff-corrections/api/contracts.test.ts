import { describe, expect, it } from 'vitest'
import { staffCorrectionCaseSchema } from './contracts'

describe('Staff correction contracts', () => {
  it('accepts a safe empty correction case and future backend statuses', () => {
    expect(staffCorrectionCaseSchema.parse({
      loanApplicationId: '11111111-1111-4111-8111-111111111111',
      applicationNumber: 'MER-1',
      productCode: 'FUTURE_PRODUCT',
      originationChannel: 'FUTURE_CHANNEL',
      applicationStatus: 'FUTURE_STATUS',
      correctionRequest: null,
      assistedCancellation: {
        available: false,
        correctionRequestId: null,
        evidence: null,
        evidenceUploadAvailable: false,
        cancellationCommandAvailable: false,
      },
    }).correctionRequest).toBeNull()
  })

  it('rejects leaked or malformed task structure instead of trusting it', () => {
    expect(staffCorrectionCaseSchema.safeParse({ loanApplicationId: 'bad' }).success).toBe(false)
  })
})
