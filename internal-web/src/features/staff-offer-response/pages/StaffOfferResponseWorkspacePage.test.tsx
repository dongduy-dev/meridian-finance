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
import { NetworkError } from '@/lib/api'
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

function fixture(status = 'PENDING', declaredOfferDecision: 'ACCEPT' | 'DECLINE' = 'ACCEPT') {
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
      evidenceType: 'CUSTOMER_OFFER_RESPONSE', declaredOfferDecision, targetId: offerId,
      targetVersion: null, versionNumber: 1, detectedMimeType: 'application/pdf', byteSize: 2048,
      uploadedAt: '2026-09-22T00:00:00',
    },
    completedResponse: null,
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

  it.each(['ACCEPT', 'DECLINE', 'FUTURE_DECISION'])('shows a completed %s with safe wording and a separate Staff recorder', async (action) => {
    const value = fixture('ACCEPTED')
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...value,
      completedResponse: {
        action, recordedBy: { userId: '00000000-0000-0000-0000-000000000302',
          displayName: 'Deni Loan Officer', email: 'deni@meridian.local' },
        recordedAt: '2026-09-22T00:00:00', evidence: value.evidence,
      },
    })
    renderPage()
    expect(await screen.findByText('Recorded Customer response')).toBeVisible()
    expect(screen.getByText(action === 'FUTURE_DECISION'
      ? 'Customer action recorded from signed evidence' : 'Customer decision recorded from signed evidence')).toBeVisible()
    expect(screen.getByText('Recorded by')).toBeVisible()
    expect(screen.getByText('Deni Loan Officer')).toBeVisible()
    expect(screen.getByText('deni@meridian.local')).toBeVisible()
    expect(screen.getByText(action === 'ACCEPT' ? 'Accepted' : action === 'DECLINE' ? 'Declined' : 'Decision unavailable')).toBeVisible()
    expect(screen.getByRole('button', { name: 'View signed evidence' })).toBeVisible()
    vi.mocked(api.apiRequest).mockRejectedValueOnce(new NetworkError())
    await userEvent.setup().click(screen.getByRole('button', { name: 'View signed evidence' }))
    expect(await screen.findByText('Viewer unavailable')).toBeVisible()
    expect(api.apiRequest).toHaveBeenLastCalledWith(
      `/staff/loan-applications/${applicationId}/assisted-action-evidence/CUSTOMER_OFFER_RESPONSE/versions/${evidenceVersionId}/content`,
      expect.objectContaining({ responseType: 'blob' }),
    )
    expect(screen.queryByRole('button', { name: /Record Customer acceptance|Record Customer decline|Upload signed evidence|Replace signed evidence/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.queryByText('Customer — self-service')).not.toBeInTheDocument()
  })

  it('keeps exact retry available after a lost response even when the refreshed offer is completed', async () => {
    let completed = false
    const bodies: unknown[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        bodies.push((options as { body: unknown }).body)
        completed = true
        if (bodies.length === 1) throw new NetworkError()
        return fixture('ACCEPTED').approvedOffer
      }
      const value = fixture(completed ? 'ACCEPTED' : 'PENDING')
      return completed ? { ...value, completedResponse: {
        action: 'ACCEPT', recordedBy: { userId: '00000000-0000-0000-0000-000000000302',
          displayName: 'Deni Loan Officer', email: 'deni@meridian.local' },
        recordedAt: '2026-09-22T00:00:00', evidence: value.evidence,
      } } : value
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Record Customer acceptance' })
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Record Customer acceptance' }))
    expect(await screen.findByText('Recorded Customer response')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Retry this response' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Record Customer acceptance' })).not.toBeInTheDocument()
    expect(bodies).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Retry this response' }))
    await screen.findByText(/Customer decision was recorded/i)
    expect(bodies).toHaveLength(2)
    expect(bodies[1]).toEqual(bodies[0])
    expect(bodies[0]).toEqual({ requestId, expectedApprovedOfferId: offerId,
      action: 'ACCEPT', evidenceDocumentVersionId: evidenceVersionId })
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

    expect(await screen.findByText(/Staff records the response shown on the signed form/i)).toBeVisible()
    expect(screen.getByText('Awaiting Customer response')).toBeVisible()
    expect(screen.getByText('APPLICATION CASE')).toBeVisible()
    expect(screen.getByRole('heading', { level: 1, name: 'UCL-20260922-000001' })).toBeVisible()
    expect(screen.getByRole('heading', { level: 2, name: 'Customer offer response' })).toBeVisible()
    expect(screen.queryByText('EVIDENCED CUSTOMER DECISION')).not.toBeInTheDocument()
    expect(screen.getByText('Technical offer details').parentElement).not.toHaveAttribute('open')
    expect(screen.queryByRole('link', { name: /Application case|Overview|Decision/ })).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.every(([path]) => String(path).endsWith('/offer-response'))).toBe(true)
    expect(screen.getByRole('option', { name: 'Accept' })).toBeVisible()
    expect(screen.getByRole('option', { name: 'Decline' })).toBeVisible()
    expect(screen.queryByRole('option', { name: 'ACCEPT' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'DECLINE' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Record Customer acceptance' }))

    await screen.findByText(/Customer decision was recorded/i)
    expect(submitted).toEqual({
      requestId,
      expectedApprovedOfferId: offerId,
      action: 'ACCEPT',
      evidenceDocumentVersionId: evidenceVersionId,
    })
  })

  it('shows an unfamiliar offer status neutrally', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(fixture('FUTURE_STATUS'))
    renderPage()

    expect(await screen.findByText('Offer status unavailable')).toBeVisible()
    expect(screen.queryByText('FUTURE_STATUS')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Record Customer acceptance' })).not.toBeInTheDocument()
  })

  it('presents Decline naturally while retaining the DECLINE command value', async () => {
    let submitted: Record<string, unknown> | undefined
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        submitted = (options as { body: Record<string, unknown> }).body
        return fixture('PENDING', 'DECLINE').approvedOffer
      }
      return fixture('PENDING', 'DECLINE')
    })
    renderPage()
    const user = userEvent.setup()

    await screen.findByText(/Staff records the response shown on the signed form/i)
    await user.selectOptions(screen.getByLabelText('Customer decision on signed form'), 'DECLINE')
    expect(screen.getByText(/Customer's decision to decline this exact offer/i)).toBeVisible()
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Record Customer decline' }))

    expect(submitted).toEqual({
      requestId,
      expectedApprovedOfferId: offerId,
      action: 'DECLINE',
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
