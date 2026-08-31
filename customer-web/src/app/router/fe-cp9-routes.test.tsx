import { queryClient } from '@/app/providers/query-client'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { ApiError } from '@/lib/api'
import { formatPercentage } from '@/lib/format/presentation'
import { createAuthApiMock, createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

const applicationId = '10000000-0000-4000-8000-000000000001'
const offerId = '20000000-0000-4000-8000-000000000001'
const contractId = '30000000-0000-4000-8000-000000000001'

const application = {
  loanApplicationId: applicationId,
  applicationNumber: 'UCL-20260901-000001',
  productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED',
  requestedAmount: 6_000_000,
  requestedTermMonths: 6,
  status: 'CUSTOMER_ACCEPTANCE_PENDING',
  submittedAt: '2026-09-01T07:00:00',
  lifecycleActive: true,
  requiredAction: 'REVIEW_APPROVED_OFFER',
}

const detail = {
  loanApplicationId: applicationId,
  applicationNumber: application.applicationNumber,
  productCode: application.productCode,
  productType: application.productType,
  requestedAmount: application.requestedAmount,
  requestedTermMonths: application.requestedTermMonths,
  status: application.status,
  submittedAt: application.submittedAt,
}

const repaymentItem = {
  installmentNumber: 1,
  principalDue: 1_000_000,
  interestDue: 90_000,
  feeDue: 0,
  totalDue: 1_090_000,
}

const pendingOffer = {
  approvedOfferId: offerId,
  loanApplicationId: applicationId,
  status: 'PENDING',
  approvedPrincipal: 6_000_000,
  approvedTermMonths: 6,
  interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
  flatMonthlyInterestRate: 0.015,
  totalInterest: 540_000,
  feeAmount: 0,
  totalRepaymentAmount: 6_540_000,
  repaymentMethod: 'MONTHLY_INSTALLMENT',
  generatedAt: '2026-09-01T08:00:00',
  expiresAt: '2026-09-08T08:00:00',
  acceptedAt: null,
  declinedAt: null,
  expiredAt: null,
  availableActions: ['ACCEPT', 'DECLINE'],
  repaymentItems: [{ ...repaymentItem, repaymentTiming: 'MONTHLY_INSTALLMENT' }],
}

const preparedContract = {
  contractId,
  contractReference: 'CTR-UCL-20260901-VERY-LONG-REFERENCE-V1',
  contractVersion: 1,
  status: 'PREPARED',
  approvedPrincipal: pendingOffer.approvedPrincipal,
  approvedTermMonths: pendingOffer.approvedTermMonths,
  interestCalculationMethod: pendingOffer.interestCalculationMethod,
  flatMonthlyInterestRate: pendingOffer.flatMonthlyInterestRate,
  totalInterest: pendingOffer.totalInterest,
  feeAmount: pendingOffer.feeAmount,
  totalRepaymentAmount: pendingOffer.totalRepaymentAmount,
  repaymentMethod: pendingOffer.repaymentMethod,
  repaymentPreview: [repaymentItem],
  disbursementBankAccount: {
    bankCode: 'VCB',
    bankNameSnapshot: 'Vietcombank',
    accountHolderName: 'MERIDIAN CUSTOMER',
    maskedAccountNumber: '****6789',
    primaryAtCapture: true,
    activeAtCapture: true,
    capturedAt: '2026-09-01T09:00:00',
  },
  preparedAt: '2026-09-01T09:00:00',
  acknowledgedAt: null,
  readinessConfirmedAt: null,
  availableCustomerAction: 'ACKNOWLEDGE',
}

interface FixtureState {
  application: Record<string, unknown>
  detail: Record<string, unknown>
  offer: Record<string, unknown>
  contract?: Record<string, unknown>
  contractMissing?: boolean
  acceptMode?: 'success' | 'expired' | 'conflict' | 'uncertain-unavailable'
  declineMode?: 'success'
  acknowledgmentMode?: 'success' | 'uncertain' | 'stale' | 'replay-newer'
  offerRefreshUnavailable?: boolean
  acceptPosts: number
  declinePosts: number
  offerReads: number
  contractReads: number
  acknowledgmentBodies: Array<{ acknowledgmentRequestId: string; expectedContractVersion: number }>
}

function state(overrides: Partial<FixtureState> = {}): FixtureState {
  return {
    application,
    detail,
    offer: pendingOffer,
    contract: preparedContract,
    acceptMode: 'success',
    declineMode: 'success',
    acknowledgmentMode: 'success',
    acceptPosts: 0,
    declinePosts: 0,
    offerReads: 0,
    contractReads: 0,
    acknowledgmentBodies: [],
    ...overrides,
  }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function error(path: string, errorCode: string, status = 409) {
  return new Response(JSON.stringify({ timestamp: '2026-09-01T10:00:00Z', status, errorCode, message: `Safe ${errorCode} response.`, path }), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Request-ID': 'support-reference-cp9' },
  })
}

function fixtureFetch(fixture: FixtureState) {
  return async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'

    if (url.endsWith(`/loan-applications/${applicationId}/approved-offer/accept`) && method === 'POST') {
      fixture.acceptPosts += 1
      if (fixture.acceptMode === 'expired') {
        fixture.offer = { ...pendingOffer, status: 'EXPIRED', availableActions: [], expiredAt: '2026-09-08T08:00:00' }
        return error(url, 'OFFER_EXPIRED')
      }
      if (fixture.acceptMode === 'conflict') {
        fixture.offer = { ...pendingOffer, status: 'DECLINED', availableActions: [], declinedAt: '2026-09-01T10:00:00' }
        return error(url, 'OFFER_ACTION_CONFLICT')
      }
      if (fixture.acceptMode === 'uncertain-unavailable' && fixture.acceptPosts === 1) {
        fixture.offerRefreshUnavailable = true
        throw new TypeError('uncertain accept result')
      }
      fixture.offerRefreshUnavailable = false
      fixture.offer = { ...pendingOffer, status: 'ACCEPTED', availableActions: [], acceptedAt: '2026-09-01T10:00:00' }
      return json(fixture.offer)
    }
    if (url.endsWith(`/loan-applications/${applicationId}/approved-offer/decline`) && method === 'POST') {
      fixture.declinePosts += 1
      fixture.offer = { ...pendingOffer, status: 'DECLINED', availableActions: [], declinedAt: '2026-09-01T10:00:00' }
      return json(fixture.offer)
    }
    if (url.endsWith(`/loan-applications/${applicationId}/approved-offer`) && method === 'GET') {
      fixture.offerReads += 1
      if (fixture.offerRefreshUnavailable) throw new TypeError('authoritative offer unavailable')
      return json(fixture.offer)
    }
    if (url.endsWith(`/loan-applications/${applicationId}/contracts/current/acknowledgment`) && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as { acknowledgmentRequestId: string; expectedContractVersion: number }
      fixture.acknowledgmentBodies.push(body)
      if (fixture.acknowledgmentMode === 'uncertain' && fixture.acknowledgmentBodies.length === 1) throw new TypeError('uncertain acknowledgment')
      if (fixture.acknowledgmentMode === 'stale' && fixture.acknowledgmentBodies.length === 1) {
        fixture.contract = { ...preparedContract, contractId: '30000000-0000-4000-8000-000000000002', contractReference: 'CTR-UCL-20260901-V2', contractVersion: 2 }
        return error(url, 'CONTRACT_VERSION_STALE')
      }
      if (fixture.acknowledgmentMode === 'replay-newer' && fixture.acknowledgmentBodies.length === 1) {
        const acknowledgedOld = { ...preparedContract, status: 'ACKNOWLEDGED', acknowledgedAt: '2026-09-01T10:00:00', availableCustomerAction: null }
        fixture.contract = { ...preparedContract, contractId: '30000000-0000-4000-8000-000000000002', contractReference: 'CTR-UCL-20260901-V2', contractVersion: 2 }
        return json(acknowledgedOld)
      }
      fixture.contract = { ...fixture.contract!, status: 'ACKNOWLEDGED', acknowledgedAt: '2026-09-01T10:00:00', availableCustomerAction: null }
      return json(fixture.contract)
    }
    if (url.endsWith(`/loan-applications/${applicationId}/contracts/current`) && method === 'GET') {
      fixture.contractReads += 1
      if (fixture.contractMissing) return error(url, 'CURRENT_CONTRACT_MISSING', 404)
      return json(fixture.contract)
    }
    if (url.endsWith(`/loan-applications/${applicationId}`) && method === 'GET') return json(fixture.detail)
    if (url.endsWith('/loan-applications') && method === 'GET') return json([fixture.application])
    throw new Error(`Unexpected request: ${method} ${url}`)
  }
}

function renderRoute(path: string, fixture: FixtureState) {
  const fetchMock = vi.fn(fixtureFetch(fixture))
  vi.stubGlobal('fetch', fetchMock)
  const router = createTestRouter([path])
  render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return { fetchMock, router }
}

afterEach(() => {
  queryClient.clear()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  cleanup()
})

describe('FE-CP9 offer flow', () => {
  it('loads and presents backend financial terms, pending expiry, and provisional items', async () => {
    let resolveOffer!: (response: Response) => void
    const pending = new Promise<Response>((resolve) => { resolveOffer = resolve })
    const fixture = state()
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => String(input).endsWith('/approved-offer') ? pending : fixtureFetch(fixture)(input, init))
    vi.stubGlobal('fetch', fetchMock)
    const router = createTestRouter([`/applications/${applicationId}/offer`])
    render(<AppProviders router={router} authManager={createTestAuthManager()} />)
    expect(await screen.findByLabelText('Loading approved offer')).toBeVisible()
    resolveOffer(json(pendingOffer))
    expect(await screen.findByRole('heading', { name: 'Review your offer' })).toHaveFocus()
    await screen.findByRole('heading', { name: 'Approved offer' })
    expect(screen.getByText('Approved principal').parentElement).toHaveTextContent(/6\.000\.000\s+₫/)
    expect(screen.getByText(formatPercentage(pendingOffer.flatMonthlyInterestRate))).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Provisional repayment preview' })).toBeVisible()
    expect(screen.getByText('These returned amounts are not the final dated LoanAccount schedule.')).toBeVisible()
    expect(screen.getByText(/Meridian's returned status and actions remain authoritative/i)).toBeVisible()
  })

  it.each([
    ['ACCEPTED', 'Accepted'],
    ['DECLINED', 'Declined'],
    ['EXPIRED', 'Expired'],
  ])('renders terminal %s status without inferred actions', async (status, label) => {
    renderRoute(`/applications/${applicationId}/offer`, state({ offer: { ...pendingOffer, status, availableActions: [] } }))
    expect(await screen.findByText(label)).toBeVisible()
    expect(screen.queryByRole('button', { name: /accept offer|decline offer/i })).not.toBeInTheDocument()
  })

  it('uses only availableActions and fails safely for unknown status and action values', async () => {
    renderRoute(`/applications/${applicationId}/offer`, state({ offer: { ...pendingOffer, status: 'FUTURE_STATUS', availableActions: ['FUTURE_ACTION'] } }))
    expect(await screen.findByText('Status unavailable')).toBeVisible()
    expect(screen.getByText('Action unavailable')).toBeVisible()
    expect(screen.queryByRole('button', { name: /accept offer|decline offer/i })).not.toBeInTheDocument()
  })

  it('accepts from the backend action projection and navigates to the contract route', async () => {
    const user = userEvent.setup()
    const fixture = state()
    const { router } = renderRoute(`/applications/${applicationId}/offer`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Accept offer' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/applications/${applicationId}/contract`))
    expect(fixture.acceptPosts).toBe(1)
  })

  it('requires destructive decline confirmation and returns a friendly Application Detail notice', async () => {
    const user = userEvent.setup()
    const fixture = state()
    const { router } = renderRoute(`/applications/${applicationId}/offer`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Decline offer' }))
    const dialog = await screen.findByRole('dialog', { name: 'Decline this offer?' })
    expect(within(dialog).getByText(/ends this application/i)).toBeVisible()
    await user.click(within(dialog).getByRole('button', { name: 'Decline offer' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/applications/${applicationId}`))
    expect(await screen.findByText('Offer declined')).toBeVisible()
    expect(screen.queryByText('CUSTOMER_DECLINED')).not.toBeInTheDocument()
  })

  it.each([
    ['expired', 'This offer has expired.', 'Expired'],
    ['conflict', 'The offer changed', 'Declined'],
  ] as const)('refreshes authoritative state after %s and removes stale actions', async (mode, message, terminalLabel) => {
    const user = userEvent.setup()
    const fixture = state({ acceptMode: mode })
    renderRoute(`/applications/${applicationId}/offer`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Accept offer' }))
    expect(await screen.findByText(new RegExp(message, 'i'))).toBeVisible()
    expect(screen.getAllByText(terminalLabel).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Accept offer' })).not.toBeInTheDocument()
  })

  it('blocks the contradictory action after uncertainty and offers only same-action retry until recovery', async () => {
    const user = userEvent.setup()
    const fixture = state({ acceptMode: 'uncertain-unavailable' })
    const { router } = renderRoute(`/applications/${applicationId}/offer`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Accept offer' }))
    expect(await screen.findByText('Offer response needs confirmation')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Retry accept' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Decline offer' })).not.toBeInTheDocument()
    const retry = screen.getByRole('button', { name: 'Retry accept' })
    await waitFor(() => expect(retry).toBeEnabled())
    await user.click(retry)
    await waitFor(() => expect(router.state.location.pathname).toBe(`/applications/${applicationId}/contract`))
    expect(fixture.acceptPosts).toBe(2)
  })
})

describe('FE-CP9 contract flow', () => {
  it('renders the supported contract-missing waiting state only with contract-phase application context', async () => {
    const fixture = state({ contractMissing: true, detail: { ...detail, status: 'CONTRACT_PENDING' } })
    renderRoute(`/applications/${applicationId}/contract`, fixture)
    expect(await screen.findByText('Your operational contract is not ready yet')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Check again' })).toBeVisible()
    expect(screen.queryByRole('button', { name: /prepare contract/i })).not.toBeInTheDocument()
  })

  it('presents exact versioned terms, masked destination, repayment preview, and operational meaning', async () => {
    renderRoute(`/applications/${applicationId}/contract`, state({ detail: { ...detail, status: 'CONTRACT_PENDING' } }))
    expect(await screen.findByRole('heading', { name: 'Review your contract' })).toHaveFocus()
    expect(await screen.findByRole('heading', { name: preparedContract.contractReference })).toBeVisible()
    expect(screen.getByText('Operational contract version 1')).toBeVisible()
    expect(screen.getByText('****6789')).toBeVisible()
    expect(screen.queryByText(/full account/i)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Contract repayment preview' })).toBeVisible()
    expect(screen.getByText(/not a generated legal agreement/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: /readiness|reveal|prepare contract/i })).not.toBeInTheDocument()
  })

  it('drives acknowledgment only from availableCustomerAction and handles unknown values safely', async () => {
    const first = renderRoute(`/applications/${applicationId}/contract`, state({ contract: { ...preparedContract, status: 'PREPARED', availableCustomerAction: null } }))
    expect(await screen.findByText('No Customer action required')).toBeVisible()
    expect(screen.queryByRole('button', { name: /acknowledge version/i })).not.toBeInTheDocument()
    first.router.dispose()
    cleanup()
    queryClient.clear()

    renderRoute(`/applications/${applicationId}/contract`, state({ contract: { ...preparedContract, status: 'FUTURE_STATUS', availableCustomerAction: 'FUTURE_ACTION' } }))
    expect(await screen.findByText('Status unavailable')).toBeVisible()
    expect(screen.getByText('Action unavailable')).toBeVisible()
    expect(screen.queryByRole('button', { name: /acknowledge version/i })).not.toBeInTheDocument()
  })

  it('uses the exact displayed version, stays on Contract, and confirms only refreshed current state', async () => {
    const user = userEvent.setup()
    const fixture = state()
    const { router } = renderRoute(`/applications/${applicationId}/contract`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Acknowledge version 1' }))
    const dialog = await screen.findByRole('dialog', { name: 'Acknowledge contract version 1?' })
    await user.click(within(dialog).getByRole('button', { name: 'Acknowledge version 1' }))
    expect(await screen.findByText('Contract acknowledged')).toBeVisible()
    expect(router.state.location.pathname).toBe(`/applications/${applicationId}/contract`)
    expect(fixture.acknowledgmentBodies[0]?.expectedContractVersion).toBe(1)
    expect(screen.queryByRole('button', { name: /acknowledge version/i })).not.toBeInTheDocument()
  })

  it('reuses one acknowledgment UUID after uncertainty for the same exact version', async () => {
    const user = userEvent.setup()
    const fixture = state({ acknowledgmentMode: 'uncertain' })
    renderRoute(`/applications/${applicationId}/contract`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Acknowledge version 1' }))
    const dialog = await screen.findByRole('dialog', { name: 'Acknowledge contract version 1?' })
    await user.click(within(dialog).getByRole('button', { name: 'Acknowledge version 1' }))
    expect(await within(dialog).findByText(/could not be confirmed/i)).toBeVisible()
    await user.click(within(dialog).getByRole('button', { name: 'Acknowledge version 1' }))
    expect(await screen.findByText('Contract acknowledged')).toBeVisible()
    expect(fixture.acknowledgmentBodies).toHaveLength(2)
    expect(fixture.acknowledgmentBodies[0]?.acknowledgmentRequestId).toBe(fixture.acknowledgmentBodies[1]?.acknowledgmentRequestId)
  })

  it('refreshes stale version and generates a new identity for deliberate replacement acknowledgment', async () => {
    const user = userEvent.setup()
    const fixture = state({ acknowledgmentMode: 'stale' })
    renderRoute(`/applications/${applicationId}/contract`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Acknowledge version 1' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Acknowledge version 1' }))
    expect(await screen.findByText('Review the current contract version')).toBeVisible()
    expect(screen.getByText('Operational contract version 2')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Acknowledge version 2' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Acknowledge version 2' }))
    await screen.findByText('Contract acknowledged')
    expect(fixture.acknowledgmentBodies[0]?.acknowledgmentRequestId).not.toBe(fixture.acknowledgmentBodies[1]?.acknowledgmentRequestId)
    expect(fixture.acknowledgmentBodies[1]?.expectedContractVersion).toBe(2)
  })

  it('does not mark a newer current version acknowledged from an old-version replay response', async () => {
    const user = userEvent.setup()
    const fixture = state({ acknowledgmentMode: 'replay-newer' })
    renderRoute(`/applications/${applicationId}/contract`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Acknowledge version 1' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Acknowledge version 1' }))
    expect(await screen.findByText('Review the current contract version')).toBeVisible()
    expect(screen.getByText('Operational contract version 2')).toBeVisible()
    expect(screen.queryByText('Contract acknowledged')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Acknowledge version 2' })).toBeVisible()
  })
})

describe('FE-CP9 route protection', () => {
  it.each([
    `/applications/${applicationId}/offer`,
    `/applications/${applicationId}/contract`,
  ])('keeps %s behind Customer authentication', async (path) => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh).mockRejectedValue(new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid refresh token.' }))
    const router = createTestRouter([path])
    render(<AppProviders router={router} authManager={new AuthSessionManager(api, vi.fn())} />)
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
  })
})
