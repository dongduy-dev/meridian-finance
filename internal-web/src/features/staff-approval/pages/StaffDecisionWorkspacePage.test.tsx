import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
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
const recommendationId = '44444444-4444-4444-8444-444444444444'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-06T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'approver@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['APPROVER'], permissions: ['approval:decide'],
}

function decisionCase(decided = false, eligible = true) {
  const decision = decided ? { decisionId: '77777777-7777-4777-8777-777777777777', reviewRecommendationId: recommendationId,
    action: 'APPROVE', reason: null, reasonCode: null, decidedAt: '2026-09-06T08:30:00' } : null
  return {
    loanApplicationId: applicationId, applicationNumber: 'UCL-1', productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'PERSONAL', requestedAmount: 10_000_000, requestedTermMonths: 6,
    applicationStatus: decided ? 'CUSTOMER_ACCEPTANCE_PENDING' : 'APPROVAL_PENDING', submittedAt: '2026-09-06T08:00:00',
    evidence: { uploadComplete: true, processingReady: true, productVerificationResult: 'VERIFIED', readyForDecision: true,
      currentReviewCycle: { reviewCycleId: '33333333-3333-4333-8333-333333333333', cycleNumber: 1,
        status: decided ? 'COMPLETED' : 'ACTIVE', startedAt: '2026-09-06T08:10:00', endedAt: decided ? '2026-09-06T08:30:00' : null } },
    recommendation: { recommendationId, reviewCycleId: '33333333-3333-4333-8333-333333333333',
      action: 'RECOMMEND_APPROVAL', reason: null, reasonCode: null, submittedAt: '2026-09-06T08:20:00' },
    makerCheckerEligible: eligible, decisionAvailable: eligible && !decided,
    latestDecision: decision, decisionHistory: decision ? [decision] : [],
    correctionReasonCodes: ['DOCUMENT_REPLACEMENT_REQUIRED', 'DOCUMENT_REVIEW_REQUIRED'],
    correctionOptions: [{ documentType: 'INCOME_PROOF', checklistItemId: '55555555-5555-4555-8555-555555555555',
      currentDocumentVersionId: '66666666-6666-4666-8666-666666666666', allowedScopes: ['DOCUMENT_REPLACEMENT', 'DOCUMENT_REVIEW'] }],
  }
}

function renderPage(queryClient = createQueryClient()) {
  const router = createTestRouter([`/staff/applications/${applicationId}/decision`])
  render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return queryClient
}

describe('Staff decision workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('keeps a lost decision result locked through failed GET and reconciles on explicit Refresh without another POST', async () => {
    let posted = false
    let readsAvailable = true
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const isPost = String(path).endsWith('/approval-decisions') && (options as RequestInit | undefined)?.method === 'POST'
      if (isPost) {
        posted = true
        readsAvailable = false
        throw new NetworkError('connection lost')
      }
      if (posted && !readsAvailable) throw new NetworkError('read unavailable')
      return decisionCase(posted)
    })
    const queryClient = createQueryClient()
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    renderPage(queryClient)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review decision' }, { timeout: 5_000 }))
    await user.click(screen.getByRole('button', { name: 'Confirm decision' }))

    expect(await screen.findByText(/decision result is unknown/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
    const posts = () => vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/approval-decisions') && (options as RequestInit | undefined)?.method === 'POST')
    expect(posts()).toHaveLength(1)

    readsAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByText(/durable decision and resulting Loan state are confirmed/i)).toBeVisible()
    expect(posts()).toHaveLength(1)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('/approved-offer'))).toBe(false)
    expect(invalidateQueries).not.toHaveBeenCalledWith({ queryKey: staffApplicationKeys.all })
  })

  it('keeps a confirmed command successful when its immediate reconciliation GET is unavailable', async () => {
    let posted = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const isPost = String(path).endsWith('/approval-decisions')
        && (options as RequestInit | undefined)?.method === 'POST'
      if (isPost) {
        posted = true
        return {}
      }
      if (posted) throw new NetworkError('read unavailable')
      return decisionCase()
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review decision' }))
    await user.click(screen.getByRole('button', { name: 'Confirm decision' }))

    expect(await screen.findByText(/Command confirmed; refreshed state unavailable/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/approval-decisions') && (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
  })

  it('renders maker-checker as a durable blocker', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(decisionCase(false, false))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Maker-checker block' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
  })

  it('renders an unknown recommendation action neutrally and fails closed', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...decisionCase(),
      recommendation: { ...decisionCase().recommendation, action: 'FUTURE_RECOMMENDATION' },
    })
    renderPage()

    expect(await screen.findByText('Recommendation action unavailable')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
  })

  it('keeps recommendation and readiness evidence before decision controls at narrow width', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(decisionCase())
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 375 })
    renderPage()

    const recommendation = await screen.findByRole('heading', { name: 'Recommendation being decided' })
    const readiness = screen.getByRole('heading', { name: 'Authoritative readiness' })
    const decision = screen.getByRole('heading', { name: 'Record independent decision' })
    expect(recommendation.compareDocumentPosition(readiness) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0)
    expect(readiness.compareDocumentPosition(decision) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0)
  })
})
