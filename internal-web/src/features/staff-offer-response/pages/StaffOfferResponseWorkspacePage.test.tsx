import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const applicationId = '11111111-1111-4111-8111-111111111111'
const offerId = '22222222-2222-4222-8222-222222222222'
const evidenceVersionId = '33333333-3333-4333-8333-333333333333'
const requestId = '44444444-4444-4444-8444-444444444444'

const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-22T10:00:00Z',
  userId: '55555555-5555-4555-8555-555555555555', email: 'officer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'],
  permissions: ['loan:offer:respond:staff', 'document:upload:assisted-action'],
}

function fixture(status = 'PENDING') {
  return {
    loanApplicationId: applicationId,
    applicationNumber: 'UCL-20260922-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    originationChannel: 'STAFF_ASSISTED',
    applicationStatus: status === 'PENDING' ? 'CUSTOMER_ACCEPTANCE_PENDING' : 'CONTRACT_PENDING',
    submittedAt: '2026-09-20T00:00:00',
    approvedOffer: {
      approvedOfferId: offerId, loanApplicationId: applicationId, status,
      approvedPrincipal: 5_000_000, approvedTermMonths: 6,
      interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL', flatMonthlyInterestRate: 0.018,
      totalInterest: 540_000, feeAmount: 0, totalRepaymentAmount: 5_540_000,
      repaymentMethod: 'MONTHLY_INSTALLMENT', generatedAt: '2026-09-21T00:00:00',
      expiresAt: '2026-09-28T00:00:00', acceptedAt: status === 'ACCEPTED' ? '2026-09-22T00:00:00' : null,
      declinedAt: null, expiredAt: null, availableActions: status === 'PENDING' ? ['ACCEPT', 'DECLINE'] : [],
      repaymentItems: [],
    },
    evidence: {
      documentId: '66666666-6666-4666-8666-666666666666', documentVersionId: evidenceVersionId,
      evidenceType: 'CUSTOMER_OFFER_RESPONSE', declaredOfferDecision: 'ACCEPT', targetId: offerId,
      targetVersion: null, versionNumber: 1, detectedMimeType: 'application/pdf', byteSize: 2048,
      uploadedAt: '2026-09-22T00:00:00',
    },
    workState: status === 'PENDING' ? 'ACTION_AVAILABLE' : 'COMPLETED',
  }
}

function renderPage() {
  const router = createTestRouter([`/staff/applications/${applicationId}/offer-response`])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Staff-assisted offer response workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(requestId)
  })

  it('records the Customer decision only after exact evidence and explicit confirmation', async () => {
    let accepted = false
    let submitted: Record<string, unknown> | undefined
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        accepted = true
        submitted = (options as { body: Record<string, unknown> }).body
        return fixture('ACCEPTED').approvedOffer
      }
      return accepted ? fixture('ACCEPTED') : fixture()
    })
    renderPage()
    const user = userEvent.setup()

    expect(await screen.findByText(/recording actor, not decision subject/i)).toBeVisible()
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Record Customer ACCEPT' }))

    await screen.findByText(/evidenced Customer decision was recorded/i)
    expect(submitted).toEqual({
      requestId,
      expectedApprovedOfferId: offerId,
      action: 'ACCEPT',
      evidenceDocumentVersionId: evidenceVersionId,
    })
  })

  it('does not query or render the route without the exact permission', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: ['loan:read'] })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
    expect(vi.mocked(api.apiRequest)).not.toHaveBeenCalled()
  })
})
