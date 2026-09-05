import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { StaffVerificationCase } from './contracts'
import { completeVerification, getStaffVerificationCase, startVerification } from './staff-verification-api'

const applicationId = '11111111-1111-4111-8111-111111111111'
const verificationId = '22222222-2222-4222-8222-222222222222'
const itemId = '33333333-3333-4333-8333-333333333333'
const versionId = '44444444-4444-4444-8444-444444444444'

function collateralCase(): StaffVerificationCase {
  const cycle = { verificationId, verificationSequence: 1, productVerificationResult: 'PENDING_MANUAL_REVIEW', createdAt: '2026-09-05T08:00:00', reviewedAt: null }
  return {
    loanApplicationId: applicationId, applicationNumber: 'COL-20260905-000001',
    productCode: 'COLLATERAL_LOAN', productType: 'SECURED', requestedAmount: 50_000_000,
    requestedTermMonths: 24, applicationStatus: 'VERIFICATION_PENDING', submittedAt: '2026-09-05T07:00:00',
    documentReadiness: { uploadComplete: true, processingReady: true },
    actions: { startAvailable: false, completeAvailable: true },
    correctionTargets: [{ checklistItemId: itemId, documentType: 'COLLATERAL_OWNERSHIP_EVIDENCE', requirementStatus: 'REQUIRED', currentDocumentVersionId: versionId }],
    productVerification: {
      currentCycle: cycle, history: [cycle],
      collateral: { collateralType: 'CAR', description: 'Fictional vehicle', estimatedValue: 90_000_000, ownershipStatus: 'CUSTOMER_OWNED', conditionNote: 'Serviceable' },
    },
  }
}

describe('staff verification API', () => {
  it('uses the purpose-limited read and existing product command paths', async () => {
    const data = collateralCase()
    const protectedRequest = vi.fn().mockResolvedValue(data)
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await getStaffVerificationCase(manager, applicationId)
    await startVerification(manager, data)

    expect(protectedRequest).toHaveBeenNthCalledWith(1, `/staff/loan-applications/${applicationId}/verification`)
    expect(protectedRequest).toHaveBeenNthCalledWith(2, `/loan-applications/${applicationId}/collateral-loan-verification/start`, { method: 'POST' })
  })

  it('builds a Collateral more-information command only from an authoritative evidence target', async () => {
    const data = collateralCase()
    const protectedRequest = vi.fn().mockResolvedValue(undefined)
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await completeVerification(manager, data, {
      expectedVerificationId: verificationId,
      outcome: 'REQUIRES_MORE_INFORMATION',
      assessmentNote: 'Ownership evidence needs a clearer image.',
      reasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED',
      tasks: [{ targetId: itemId, scope: 'DOCUMENT_REPLACEMENT', instruction: 'Upload a complete scan.' }],
    })

    expect(protectedRequest).toHaveBeenCalledWith(
      `/loan-applications/${applicationId}/collateral-loan-verification/complete`,
      { method: 'POST', body: {
        expectedVerificationId: verificationId,
        outcome: 'REQUIRES_MORE_INFORMATION',
        assessmentNote: 'Ownership evidence needs a clearer image.',
        reasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED',
        correctionPlan: { tasks: [{
          scope: 'DOCUMENT_REPLACEMENT', responsibleParty: 'CUSTOMER',
          documentType: 'COLLATERAL_OWNERSHIP_EVIDENCE', createChecklistItem: false,
          checklistItemId: itemId, baselineDocumentVersionId: versionId,
          customerInstruction: 'Upload a complete scan.', staffInstruction: null,
        }] },
      } },
    )
  })

  it('fails locally when a caller supplies a free-form target identifier', async () => {
    const protectedRequest = vi.fn()
    const manager = { protectedRequest } as unknown as AuthSessionManager
    await expect(completeVerification(manager, collateralCase(), {
      expectedVerificationId: verificationId,
      outcome: 'REQUIRES_MORE_INFORMATION', assessmentNote: 'Needs evidence.',
      reasonCode: 'DOCUMENT_REVIEW_REQUIRED',
      tasks: [{ targetId: '55555555-5555-4555-8555-555555555555', scope: 'DOCUMENT_REVIEW', instruction: 'Review again.' }],
    })).rejects.toThrow(/no longer available/i)
    expect(protectedRequest).not.toHaveBeenCalled()
  })
})
