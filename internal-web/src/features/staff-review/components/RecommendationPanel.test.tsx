import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError, NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { RecommendationPanel } from './RecommendationPanel'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const applicationId = '11111111-1111-4111-8111-111111111111'
const cycleId = '33333333-3333-4333-8333-333333333333'
const assignedOfficer = {
  userId: '22222222-2222-4222-8222-222222222222',
  displayName: 'Deni Loan Officer',
  email: 'officer@meridian.local',
}
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-06T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'officer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['loan:review', 'approval:recommend'],
}

function recommendationCase(recorded = false) {
  return {
    loanApplicationId: applicationId, applicationNumber: 'UCL-1', productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'PERSONAL', requestedAmount: 10_000_000, requestedTermMonths: 6,
    applicationStatus: recorded ? 'APPROVAL_PENDING' : 'UNDER_REVIEW', submittedAt: '2026-09-06T08:00:00',
    evidence: { uploadComplete: true, processingReady: true, productVerificationResult: 'VERIFIED', readyForDecision: true,
      currentReviewCycle: { reviewCycleId: cycleId, cycleNumber: 1, assignedLoanOfficer: assignedOfficer,
        status: 'ACTIVE', startedAt: '2026-09-06T08:10:00', endedAt: null } },
    recommendation: recorded ? { recommendationId: '44444444-4444-4444-8444-444444444444', reviewCycleId: cycleId,
      action: 'RECOMMEND_APPROVAL', reason: null, reasonCode: null, recordedBy: assignedOfficer,
      submittedAt: '2026-09-06T08:20:00' } : null,
    recommendationAvailable: !recorded,
    correctionReasonCodes: ['DOCUMENT_REPLACEMENT_REQUIRED', 'DOCUMENT_REVIEW_REQUIRED'],
    correctionOptions: [{ documentType: 'INCOME_PROOF', checklistItemId: '55555555-5555-4555-8555-555555555555',
      currentDocumentVersionId: '66666666-6666-4666-8666-666666666666', allowedScopes: ['DOCUMENT_REPLACEMENT', 'DOCUMENT_REVIEW'] }],
  }
}

describe('RecommendationPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it.each(['RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION'] as const)(
    'sends the displayed review cycle for %s',
    async (selectedAction) => {
      let recorded = false
      let submittedBody: Record<string, unknown> | undefined
      vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
        if (String(path).endsWith('/review-recommendations')
          && (options as RequestInit | undefined)?.method === 'POST') {
          submittedBody = (options as { body?: Record<string, unknown> }).body
          recorded = true
          return {}
        }
        const value = recommendationCase(recorded)
        return recorded
          ? { ...value, recommendation: { ...value.recommendation!, action: selectedAction } }
          : value
      })
      render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)
      const user = userEvent.setup()
      if (selectedAction === 'RECOMMEND_REJECTION') {
        await user.click(await screen.findByLabelText('Recommend rejection'))
        await user.type(screen.getByLabelText('Recommendation reason'), 'Policy reason.')
      }
      await user.click(await screen.findByRole('button', { name: 'Review recommendation' }))
      await user.click(screen.getByRole('button', { name: selectedAction === 'RECOMMEND_APPROVAL' ? 'Recommend approval' : 'Recommend rejection' }))

      await screen.findByText(/recommendation was confirmed for the displayed review cycle/i)
      await waitFor(() => expect(screen.getByRole('heading', { name: `Recommendation result for UCL-1` })).toHaveFocus())
      expect(submittedBody).toMatchObject({
        action: selectedAction,
        expectedReviewCycleId: cycleId,
      })
    },
  )

  it('renders recommendation controls for a returned case authorized by the backend', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...recommendationCase(),
      applicationStatus: 'RETURNED_TO_REVIEW',
      recommendationAvailable: true,
    })

    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)

    expect(await screen.findByRole('button', { name: 'Review recommendation' })).toBeVisible()
    expect(screen.queryByText('Recommendation is unavailable for the authoritative current state.')).not.toBeInTheDocument()
  })

  it('shows assignment context but no recommendation command to another Loan Officer', async () => {
    const value = recommendationCase()
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...value,
      recommendationAvailable: false,
      evidence: {
        ...value.evidence,
        currentReviewCycle: {
          ...value.evidence.currentReviewCycle!,
          assignedLoanOfficer: {
            userId: '99999999-9999-4999-8999-999999999999',
            displayName: 'Minh Loan Officer',
            email: 'minh.loan.officer@meridian.local',
          },
        },
      },
    })

    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)

    expect(await screen.findByRole('heading', { name: 'Recommendation assigned to Minh Loan Officer' })).toBeVisible()
    expect(screen.getByText(/only Minh Loan Officer .* can record its recommendation/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review recommendation' })).not.toBeInTheDocument()
  })

  it('identifies the Staff actor who recorded an existing recommendation', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(recommendationCase(true))

    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)

    expect(await screen.findByRole('heading', { name: 'Recommend approval' })).toBeVisible()
    expect(screen.getByText('Recorded')).toBeVisible()
    expect(screen.getByText('Recorded by')).toBeVisible()
    expect(screen.getByText('Deni Loan Officer')).toBeVisible()
    expect(screen.getByText('officer@meridian.local')).toBeVisible()
  })

  it('reconciles a lost POST response by exact cycle and action without a second POST', async () => {
    let recorded = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/review-recommendations') && (options as RequestInit | undefined)?.method === 'POST') {
        recorded = true
        throw new NetworkError('connection lost')
      }
      return recommendationCase(recorded)
    })
    const queryClient = createQueryClient()
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    render(<QueryClientProvider client={queryClient}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review recommendation' }))
    await user.click(screen.getByRole('button', { name: 'Recommend approval' }))

    expect(await screen.findByText(/recommendation was confirmed for the displayed review cycle/i)).toBeVisible()
    const posts = vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/review-recommendations') && (options as RequestInit | undefined)?.method === 'POST')
    expect(posts).toHaveLength(1)
    expect(invalidateQueries).not.toHaveBeenCalledWith({ queryKey: staffApplicationKeys.all })
  })

  it('keeps mutations locked after a failed reconciliation GET and resolves only through explicit Refresh', async () => {
    let recorded = false
    let readsAvailable = true
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const isPost = String(path).endsWith('/review-recommendations')
        && (options as RequestInit | undefined)?.method === 'POST'
      if (isPost) {
        recorded = true
        readsAvailable = false
        throw new NetworkError('connection lost')
      }
      if (!readsAvailable) throw new NetworkError('read unavailable')
      return recommendationCase(recorded)
    })
    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review recommendation' }))
    await user.click(screen.getByRole('button', { name: 'Recommend approval' }))

    expect(await screen.findByText(/recommendation result is not confirmed/i, {}, { timeout: 3_000 })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review recommendation' })).not.toBeInTheDocument()
    const posts = () => vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/review-recommendations') && (options as RequestInit | undefined)?.method === 'POST')
    expect(posts()).toHaveLength(1)

    readsAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText(/recommendation was confirmed for the displayed review cycle/i)).toBeVisible()
    expect(posts()).toHaveLength(1)
  })

  it('preserves memory-only form state and requires re-review when the review cycle becomes stale', async () => {
    const nextCycleId = '77777777-7777-4777-8777-777777777777'
    let postAttempted = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/review-recommendations') && (options as RequestInit | undefined)?.method === 'POST') {
        postAttempted = true
        throw new ApiError(409, 'STALE_REVIEW_CYCLE', 'cycle changed', '/review-recommendations', 'request-1')
      }
      const value = recommendationCase()
      return postAttempted ? {
        ...value,
        evidence: { ...value.evidence, currentReviewCycle: { ...value.evidence.currentReviewCycle, reviewCycleId: nextCycleId, cycleNumber: 2 } },
      } : value
    })
    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)
    const user = userEvent.setup()
    const notes = await screen.findByLabelText('Internal credit note')
    await user.type(notes, 'preserve this draft')
    await user.click(screen.getByRole('button', { name: 'Review recommendation' }))
    await user.click(screen.getByRole('button', { name: 'Recommend approval' }))

    expect(await screen.findByRole('heading', { name: 'Review evidence changed' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review recommendation' })).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/review-recommendations')
      && (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'I reviewed the updated cycle' }))
    expect(await screen.findByRole('button', { name: 'Review recommendation' })).toBeVisible()
    expect(screen.getByDisplayValue('preserve this draft')).toBeVisible()
  })

  it('renders an unknown recorded recommendation action neutrally and fails closed', async () => {
    const value = recommendationCase(true)
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...value,
      recommendationAvailable: true,
      recommendation: { ...value.recommendation!, action: 'FUTURE_RECOMMENDATION' },
    })
    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)

    const heading = await screen.findByRole('heading', { name: 'Recommendation action unavailable' })
    expect(heading.closest('[role="alert"]')).toHaveTextContent('Recommendation action unavailable')
    expect(screen.queryByRole('button', { name: 'Review recommendation' })).not.toBeInTheDocument()
  })

  it('returns keyboard focus to the command trigger when confirmation is cancelled', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(recommendationCase())
    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)
    const user = userEvent.setup()
    const trigger = await screen.findByRole('button', { name: 'Review recommendation' })
    await user.click(trigger)
    expect(screen.getByRole('dialog')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(trigger).toHaveFocus())
  })

  it('keeps restricted internal notes local until explicit confirmation', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(recommendationCase())
    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RecommendationPanel loanApplicationId={applicationId} /></AuthProvider></QueryClientProvider>)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Internal credit note'), 'restricted assessment')

    expect(vi.mocked(api.apiRequest).mock.calls.some(([, options]) =>
      (JSON.stringify((options as { body?: unknown } | undefined)?.body) ?? '').includes('restricted assessment'))).toBe(false)
  })
})
