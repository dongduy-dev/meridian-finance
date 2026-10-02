import { productAssessmentFixture } from '@/test/staff-verification-fixture'
import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import * as api from '@/lib/api'
import { ApiError, NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { reviewHistoryFixture } from '@/test/staff-review-history-fixture'

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
const cycleId = '33333333-3333-4333-8333-333333333333'
const successorCycleId = '88888888-8888-4888-8888-888888888888'
const assignedOfficer = {
  userId: '99999999-9999-4999-8999-999999999999',
  displayName: 'Deni Loan Officer',
  email: 'officer@meridian.local',
}
const decidingActor = {
  userId: '22222222-2222-4222-8222-222222222222',
  displayName: 'Ari Approver',
  email: 'approver@meridian.local',
}
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-06T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'approver@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['APPROVER'], permissions: ['approval:decide'],
}

function decisionCase(decided = false, eligible = true) {
  const decision = decided ? { decisionId: '77777777-7777-4777-8777-777777777777', reviewRecommendationId: recommendationId,
    action: 'APPROVE', reason: null, reasonCode: null, recordedBy: decidingActor,
    decidedAt: '2026-09-06T08:30:00' } : null
  return {
    loanApplicationId: applicationId, applicationNumber: 'UCL-1', productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'PERSONAL', requestedAmount: 10_000_000, requestedTermMonths: 6,
    applicationStatus: decided ? 'CUSTOMER_ACCEPTANCE_PENDING' : 'APPROVAL_PENDING', submittedAt: '2026-09-06T08:00:00',
    customer: { customerNumber: 'CUS-000001', fullName: 'Ari Customer' },
    evidence: { uploadComplete: true, processingReady: true, productVerificationResult: 'VERIFIED', readyForDecision: true,
      currentReviewCycle: { reviewCycleId: cycleId, cycleNumber: 1,
        assignedLoanOfficer: assignedOfficer,
        status: decided ? 'COMPLETED' : 'ACTIVE', startedAt: '2026-09-06T08:10:00', endedAt: decided ? '2026-09-06T08:30:00' : null } },
    recommendation: { recommendationId, reviewCycleId: cycleId,
      action: 'RECOMMEND_APPROVAL', reason: null, reasonCode: null, recordedBy: assignedOfficer,
      submittedAt: '2026-09-06T08:20:00' },
    makerCheckerEligible: eligible, decisionAvailable: eligible && !decided,
    latestDecision: decision, decisionHistory: decision ? [decision] : [],
    correctionReasonCodes: ['DOCUMENT_REPLACEMENT_REQUIRED', 'DOCUMENT_REVIEW_REQUIRED'],
    correctionOptions: [{ documentType: 'INCOME_PROOF', checklistItemId: '55555555-5555-4555-8555-555555555555',
      currentDocumentVersionId: '66666666-6666-4666-8666-666666666666', allowedScopes: ['DOCUMENT_REPLACEMENT', 'DOCUMENT_REVIEW'] }],
  }
}

function returnedToReviewCase() {
  const value = decisionCase()
  const decision = {
    decisionId: '77777777-7777-4777-8777-777777777777',
    reviewRecommendationId: recommendationId,
    action: 'RETURN_TO_LOAN_OFFICER_REVIEW',
    reason: 'Review the case again.',
    reasonCode: null,
    recordedBy: decidingActor,
    decidedAt: '2026-09-06T08:30:00',
  }
  return {
    ...value,
    applicationStatus: 'RETURNED_TO_REVIEW',
    evidence: {
      ...value.evidence,
      currentReviewCycle: {
        reviewCycleId: successorCycleId,
        cycleNumber: 2,
        assignedLoanOfficer: assignedOfficer,
        status: 'ACTIVE',
        startedAt: '2026-09-06T08:30:00',
        endedAt: null,
      },
    },
    decisionAvailable: false,
    latestDecision: decision,
    decisionHistory: [decision],
  }
}

function renderPage(queryClient = createQueryClient()) {
  const router = createTestRouter([`/staff/applications/${applicationId}/decision`])
  render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return queryClient
}

describe('Staff decision workspace', () => {
  it('shows only Customer business identity to an approval-only actor', async () => {
    const customerId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    const value = { ...decisionCase(), customer: {
      ...decisionCase().customer, customerId, phoneNumber: '0901234567',
      identityReference: 'SYNTHETIC-IDENTITY', email: 'customer@meridian.local',
    } }
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/verification')
      ? productAssessmentFixture() : String(path).endsWith('/review-history') ? reviewHistoryFixture() : value)
    renderPage()
    expect(await screen.findByText('Ari Customer')).toBeVisible()
    expect(screen.getByText('CUS-000001')).toBeVisible()
    for (const forbidden of ['0901234567', 'SYNTHETIC-IDENTITY', customerId, 'customer@meridian.local', 'Identity Reference', 'Current phone']) {
      expect(screen.queryByText(forbidden)).not.toBeInTheDocument()
    }
    expect(vi.mocked(api.apiRequest).mock.calls.every(([path]) => !String(path).includes('/customers'))).toBe(true)
  })

  it('lets an approval-only actor read assessment before recommendation without verification controls', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/verification')
      ? productAssessmentFixture() : String(path).endsWith('/review-history') ? reviewHistoryFixture() : decisionCase())
    renderPage()
    const assessment = await screen.findByText('Synthetic product assessment evidence.')
    expect(assessment.compareDocumentPosition(screen.getByRole('heading', { name: 'Loan Officer recommendation' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.queryByLabelText('Assessment note')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Start manual verification|Review verification completion|Start review/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Open product assessment workspace' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Review decision' })).toBeVisible()
  })
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('shows linked earlier cycles separately while confirming only the current expected identities', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/verification') ? productAssessmentFixture() : String(path).endsWith('/review-history')
      ? reviewHistoryFixture() : decisionCase())
    renderPage()
    expect(await screen.findByText('Earlier recommendation rationale.')).toBeVisible()
    expect(screen.getByText('Synthetic recommendation analysis.')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Review Cycle 3' })).toBeVisible()
    const historical = within(screen.getByRole('region', { name: 'Review Cycle 1' }))
    expect(historical.queryByRole('button')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Review decision' }))
    const confirmation = within(screen.getByRole('dialog'))
    expect(confirmation.getByText('Cycle 1')).toBeVisible()
    expect(confirmation.queryByText('Earlier recommendation rationale.')).not.toBeInTheDocument()
    expect(confirmation.queryByText('Synthetic recommendation analysis.')).not.toBeInTheDocument()
  })

  it('shows current actor provenance and only the linked historical presentation', async () => {
    const value = decisionCase(true)
    const history = reviewHistoryFixture()
    history.cycles = [{ ...history.cycles[0]!,
      recommendation: { ...value.recommendation, internalNoteReadable: true },
      decision: { ...value.latestDecision!, internalNoteReadable: true },
    }]
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/verification') ? productAssessmentFixture() : String(path).endsWith('/review-history')
      ? history : value)
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Review Cycle 1' })).toBeVisible()
    expect(screen.getAllByText('Deni Loan Officer')).toHaveLength(2)
    expect(screen.getAllByText('officer@meridian.local')).toHaveLength(2)
    expect(screen.getAllByText('Ari Approver')).toHaveLength(2)
    expect(within(screen.getByRole('heading', { name: 'Decision outcome' }).parentElement!.parentElement!).getByText('approver@meridian.local')).toBeVisible()
    expect(within(screen.getByRole('region', { name: 'Review Cycle 1' })).getByText('approver@meridian.local')).toBeVisible()
    expect(screen.getAllByRole('heading', { name: 'Review and decision history' })).toHaveLength(1)
    expect(screen.queryByRole('heading', { name: 'Decision history' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Recorded by')).toHaveLength(4)
    expect(screen.getAllByText('Recorded')).toHaveLength(4)
  })

  it.each(['RETURNED_TO_REVIEW', 'APPROVAL_PENDING'] as const)(
    'retains linked history without a current decision in %s across refresh and a fresh session',
    async (applicationStatus) => {
      const previous = returnedToReviewCase()
      const value = {
        ...previous,
        applicationStatus,
        latestDecision: null,
        decisionAvailable: applicationStatus === 'APPROVAL_PENDING',
        recommendation: applicationStatus === 'APPROVAL_PENDING' ? {
          ...previous.recommendation,
          recommendationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          reviewCycleId: successorCycleId,
        } : null,
        decisionHistory: [previous.decisionHistory[0]!, {
          ...previous.decisionHistory[0]!,
          decisionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          reviewRecommendationId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
          action: 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
          reason: null,
          reasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED',
          decidedAt: '2026-09-05T08:30:00',
        }],
      }
      value.evidence.currentReviewCycle.cycleNumber = 3
      const linked = reviewHistoryFixture()
      linked.applicationStatus = applicationStatus
      const historicalDecisions = [...value.decisionHistory].reverse()
      linked.cycles.slice(0, 2).forEach((cycle, index) => {
        const decision = historicalDecisions[index]!
        cycle.status = 'COMPLETED'
        cycle.recommendation!.recommendationId = decision.reviewRecommendationId
        cycle.decision = { ...decision, internalNoteReadable: true }
      })
      linked.cycles[2]!.recommendation = value.recommendation
        ? { ...value.recommendation, internalNoteReadable: true } : null
      vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/verification') ? productAssessmentFixture() : String(path).endsWith('/review-history')
        ? linked : value)
      const assertHistory = async () => {
        await screen.findByText('Review the case again.')
        const heading = screen.getByRole('heading', { name: 'Review and decision history' })
        const history = within(heading.parentElement!.parentElement!)
        expect(history.getAllByRole('heading', { level: 3, name: /^Review Cycle/ }).map((item) => item.textContent))
          .toEqual(['Review Cycle 1', 'Review Cycle 2', 'Review Cycle 3'])
        expect(within(screen.getByRole('region', { name: 'Review Cycle 1' })).getByRole('heading', { name: 'Request correction' })).toBeVisible()
        expect(within(screen.getByRole('region', { name: 'Review Cycle 2' })).getByRole('heading', { name: 'Return to Loan Officer' })).toBeVisible()
        expect(history.getAllByText('Ari Approver')).toHaveLength(2)
        expect(history.getAllByText('approver@meridian.local')).toHaveLength(2)
        expect(history.getByText('Review the case again.')).toBeVisible()
        expect(history.getByText('Controlled reason: Document replacement required')).toBeVisible()
        expect(screen.queryByRole('heading', { name: 'Decision outcome' })).not.toBeInTheDocument()
        expect(screen.queryByRole('heading', { name: 'Decision history' })).not.toBeInTheDocument()
        if (applicationStatus === 'APPROVAL_PENDING') {
          expect(screen.getByRole('button', { name: 'Review decision' })).toBeVisible()
        } else {
          expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
        }
        expect(screen.getByText(applicationStatus === 'APPROVAL_PENDING' ? 'Approval pending' : 'Returned to review')).toBeVisible()
      }
      renderPage()
      await assertHistory()
      await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
      await assertHistory()
      cleanup()
      renderPage(createQueryClient())
      await assertHistory()
      expect(vi.mocked(api.apiRequest).mock.calls.filter(([path]) => String(path).endsWith('/decision'))).toHaveLength(3)
    },
  )

  it('uses shared application identity and business evidence without loan:read', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(decisionCase())
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { level: 1, name: 'UCL-1' })
    expect(screen.getByText('APPLICATION CASE')).toBeVisible()
    expect(screen.getByRole('heading', { level: 2, name: 'Independent decision' })).toBeVisible()
    expect(screen.getByText('Cycle 1')).toBeVisible()
    expect(screen.queryByText(cycleId)).not.toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Application sections' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Documents' })).toHaveAttribute('href', `/staff/applications/${applicationId}/documents`)
    expect(screen.queryByRole('link', { name: 'Overview' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Review' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Review decision' }))
    const confirmation = within(screen.getByRole('dialog'))
    expect(confirmation.getByText('UCL-1')).toBeVisible()
    expect(confirmation.getByText('Recommend approval')).toBeVisible()
    expect(confirmation.getByText('Deni Loan Officer')).toBeVisible()
    expect(confirmation.getByText('officer@meridian.local')).toBeVisible()
    expect(confirmation.getByText('Cycle 1')).toBeVisible()
    expect(confirmation.getByText('Submitted').nextElementSibling).not.toBeEmptyDOMElement()
    expect(confirmation.queryByText(recommendationId)).not.toBeInTheDocument()
    expect(confirmation.queryByText(cycleId)).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.every(([path]) =>
      [`/staff/loan-applications/${applicationId}/decision`, `/staff/loan-applications/${applicationId}/review-history`, `/staff/loan-applications/${applicationId}/verification`].includes(String(path)))).toBe(true)
  })

  it('does not label a prior recommendation with the successor cycle number', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(returnedToReviewCase())
    renderPage()
    expect(await screen.findByText('Cycle number unavailable')).toBeVisible()
    expect(screen.queryByText(cycleId)).not.toBeInTheDocument()
    expect(screen.getByText('Cycle 2 · Active')).toBeVisible()
  })

  it.each([
    'APPROVE',
    'REJECT',
    'RETURN_TO_LOAN_OFFICER_REVIEW',
    'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
  ] as const)('sends the displayed recommendation and cycle for %s', async (selectedAction) => {
    let submittedBody: Record<string, unknown> | undefined
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/approval-decisions')
        && (options as RequestInit | undefined)?.method === 'POST') {
        submittedBody = (options as { body?: Record<string, unknown> }).body
        throw new ApiError(409, 'STALE_REVIEW_RECOMMENDATION', 'recommendation changed', '/approval-decisions', 'request-1')
      }
      return decisionCase()
    })
    renderPage()
    const user = userEvent.setup()
    if (selectedAction !== 'APPROVE') {
      await user.click(await screen.findByLabelText(
        selectedAction === 'REJECT' ? 'Reject'
          : selectedAction === 'RETURN_TO_LOAN_OFFICER_REVIEW' ? 'Return to Loan Officer review'
            : 'Request Customer and Staff correction',
      ))
    }
    if (selectedAction === 'REJECT' || selectedAction === 'RETURN_TO_LOAN_OFFICER_REVIEW') {
      await user.type(screen.getByLabelText('Decision reason'), 'Decision reason.')
    }
    if (selectedAction === 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION') {
      await user.click(screen.getByRole('button', { name: 'Add task' }))
      await user.click(screen.getByRole('button', { name: 'Add task' }))
      await user.selectOptions(screen.getAllByLabelText('Task type')[1]!, 'DOCUMENT_REVIEW')
      await user.type(screen.getByLabelText('Customer instruction'), 'Replace the evidence.')
      await user.type(screen.getByLabelText('Staff instruction'), 'Review the replacement.')
    }
    await user.click(await screen.findByRole('button', { name: 'Review decision' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    await screen.findByRole('heading', { name: 'Decision evidence changed' })
    await waitFor(() => expect(screen.getByRole('heading', { name: /Decision result for UCL-1/i })).toHaveFocus())
    expect(submittedBody).toMatchObject({
      action: selectedAction,
      expectedReviewRecommendationId: recommendationId,
      expectedReviewCycleId: cycleId,
    })
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/approval-decisions')
      && (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
  })

  it('preserves the draft and requires explicit re-review after a stale recommendation', async () => {
    const nextRecommendationId = '88888888-8888-4888-8888-888888888888'
    const nextCycleId = '99999999-9999-4999-8999-999999999999'
    let postAttempted = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/approval-decisions')
        && (options as RequestInit | undefined)?.method === 'POST') {
        postAttempted = true
        throw new ApiError(409, 'STALE_REVIEW_RECOMMENDATION', 'recommendation changed', '/approval-decisions', 'request-2')
      }
      const value = decisionCase()
      return postAttempted ? {
        ...value,
        evidence: {
          ...value.evidence,
          currentReviewCycle: { ...value.evidence.currentReviewCycle, reviewCycleId: nextCycleId, cycleNumber: 2 },
        },
        recommendation: {
          ...value.recommendation,
          recommendationId: nextRecommendationId,
          reviewCycleId: nextCycleId,
        },
      } : value
    })
    renderPage()
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Internal credit note'), 'preserve this decision draft')
    await user.click(screen.getByRole('button', { name: 'Review decision' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByRole('heading', { name: 'Decision evidence changed' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'I reviewed the updated evidence' }))
    expect(await screen.findByRole('button', { name: 'Review decision' })).toBeVisible()
    expect(screen.getByDisplayValue('preserve this decision draft')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/approval-decisions')
      && (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
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
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText(/decision result is not confirmed/i, undefined, { timeout: 3_000 })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
    const posts = () => vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/approval-decisions') && (options as RequestInit | undefined)?.method === 'POST')
    expect(posts()).toHaveLength(1)

    readsAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByText(/Decision recorded. Application status:/i)).toBeVisible()
    expect(posts()).toHaveLength(1)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('/approved-offer'))).toBe(false)
    expect(invalidateQueries).not.toHaveBeenCalledWith({ queryKey: staffApplicationKeys.all })
  })

  it('reconciles a lost return-to-review decision against the new active successor cycle', async () => {
    let posted = false
    let readsAvailable = true
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const isPost = String(path).endsWith('/approval-decisions')
        && (options as RequestInit | undefined)?.method === 'POST'
      if (isPost) {
        posted = true
        readsAvailable = false
        throw new NetworkError('connection lost')
      }
      if (posted && !readsAvailable) throw new NetworkError('read unavailable')
      return posted ? returnedToReviewCase() : decisionCase()
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByLabelText('Return to Loan Officer review'))
    await user.type(screen.getByLabelText('Decision reason'), 'Review the case again.')
    await user.click(screen.getByRole('button', { name: 'Review decision' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText(/decision result is not confirmed/i, undefined, { timeout: 3_000 })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
    const posts = () => vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/approval-decisions') && (options as RequestInit | undefined)?.method === 'POST')
    expect(posts()).toHaveLength(1)

    readsAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByText(/Decision recorded. Application status: Returned to review/i)).toBeVisible()
    expect(screen.queryByText(/Application status: RETURNED_TO_REVIEW/)).not.toBeInTheDocument()
    expect(posts()).toHaveLength(1)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('/approved-offer'))).toBe(false)
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
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText(
      /Decision recorded; updated details could not be loaded/i,
      undefined,
      { timeout: 3_000 },
    )).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/approval-decisions') && (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
  })

  it('renders maker-checker as a durable blocker', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(decisionCase(false, false))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Maker-checker requirement' })).toBeVisible()
    expect(screen.getByText(/different authorized Approver must make the decision/i)).toBeVisible()
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

    const recommendation = await screen.findByRole('heading', { name: 'Loan Officer recommendation' })
    const readiness = screen.getByRole('heading', { name: 'Decision readiness' })
    const decision = screen.getByRole('heading', { name: 'Record independent decision' })
    expect(recommendation.compareDocumentPosition(readiness) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0)
    expect(readiness.compareDocumentPosition(decision) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0)
  })
})
