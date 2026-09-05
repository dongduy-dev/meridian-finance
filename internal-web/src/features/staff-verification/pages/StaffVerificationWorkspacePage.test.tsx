import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
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

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const applicationId = '11111111-1111-4111-8111-111111111111'
const verificationId = '22222222-2222-4222-8222-222222222222'
const replacementVerificationId = '33333333-3333-4333-8333-333333333333'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-05T10:00:00Z',
  userId: '44444444-4444-4444-8444-444444444444', email: 'officer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['loan:review'],
}

const common = {
  loanApplicationId: applicationId, applicationNumber: 'UCL-20260905-000001', productType: 'PERSONAL',
  requestedAmount: 20_000_000, requestedTermMonths: 12, submittedAt: '2026-09-05T08:00:00',
  documentReadiness: { uploadComplete: true, processingReady: true }, correctionTargets: [],
}

function uclCase(result = 'PENDING_MANUAL_REVIEW') {
  const cycle = {
    verificationId, verificationSequence: 1, productVerificationResult: result,
    createdAt: '2026-09-05T08:01:00', reviewedAt: result === 'PENDING_MANUAL_REVIEW' ? null : '2026-09-05T08:05:00',
  }
  return {
    ...common, productCode: 'UNSECURED_CONSUMER_LOAN',
    applicationStatus: result === 'PENDING_MANUAL_REVIEW' ? 'VERIFICATION_PENDING' : 'VERIFIED',
    actions: { startAvailable: false, completeAvailable: result === 'PENDING_MANUAL_REVIEW' },
    productVerification: { currentCycle: cycle, history: [cycle], collateral: null },
  }
}

function collateralCase(currentId = verificationId) {
  const cycle = { ...uclCase().productVerification.currentCycle, verificationId: currentId }
  return {
    ...common, applicationNumber: 'COL-20260905-000001', productCode: 'COLLATERAL_LOAN',
    applicationStatus: 'VERIFICATION_PENDING', actions: { startAvailable: false, completeAvailable: true },
    productVerification: {
      currentCycle: cycle, history: [cycle],
      collateral: { collateralType: 'CAR', description: 'Fictional vehicle', estimatedValue: 90_000_000, ownershipStatus: 'CUSTOMER_OWNED', conditionNote: 'Serviceable' },
    },
  }
}

describe('Staff verification workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('renders Salary Advance snapshots as read-only and does not expose manual actions', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...common, productCode: 'SALARY_ADVANCE', applicationNumber: 'SA-20260905-000001', applicationStatus: 'VERIFIED',
      actions: { startAvailable: false, completeAvailable: false },
      productVerification: {
        verificationSequence: 1, employeeVerificationOutcome: 'ELIGIBLE', productVerificationResult: 'VERIFIED',
        totalLimitSnapshot: 10_000_000, usedAmountSnapshot: 1_000_000, reservedAmountSnapshot: 2_000_000,
        availableLimitSnapshot: 7_000_000, verifiedAt: '2026-09-05T08:01:00',
      },
    })
    renderWorkspace()
    expect(await screen.findByRole('heading', { name: 'Immutable Salary Advance verification' }, { timeout: 5_000 })).toBeVisible()
    expect(screen.getByText(/not live Partner values/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: /Start manual verification|Review verification completion/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Application overview/ })).not.toBeInTheDocument()
  })

  it('reconciles an unknown completion result with GET and never retries the POST', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: ['loan:review', 'loan:read'] })
    let completed = false
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/unsecured-consumer-loan-verification/complete')) {
        completed = true
        throw new NetworkError('connection lost')
      }
      return completed ? uclCase('VERIFIED') : uclCase()
    })
    const queryClient = createQueryClient()
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    renderWorkspace(queryClient)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Assessment note'), 'Evidence is complete.')
    await user.click(screen.getByRole('button', { name: 'Review verification completion' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText(/authoritative read confirms the completed verification outcome/i)).toBeVisible()
    const posts = vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/unsecured-consumer-loan-verification/complete')
      && (options as RequestInit | undefined)?.method === 'POST')
    expect(posts).toHaveLength(1)
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: staffApplicationKeys.all })
  })

  it('keeps completion unresolved when reconciliation GET fails and unlocks only after a successful Refresh', async () => {
    let completionPosted = false
    let authoritativeReadAvailable = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const isCompletionPost = String(path).endsWith('/unsecured-consumer-loan-verification/complete')
        && (options as RequestInit | undefined)?.method === 'POST'
      if (isCompletionPost) {
        completionPosted = true
        throw new NetworkError('connection lost')
      }
      if (completionPosted && !authoritativeReadAvailable) throw new NetworkError('authoritative read unavailable')
      return completionPosted ? uclCase('VERIFIED') : uclCase()
    })
    renderWorkspace()
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Assessment note'), 'Evidence is complete.')
    await user.click(screen.getByRole('button', { name: 'Review verification completion' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText(/operation result is still unknown because authoritative state could not be refreshed/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review verification completion' })).not.toBeInTheDocument()
    const completionPosts = () => vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/unsecured-consumer-loan-verification/complete')
      && (options as RequestInit | undefined)?.method === 'POST')
    expect(completionPosts()).toHaveLength(1)

    authoritativeReadAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByText(/authoritative read confirms the completed verification outcome/i)).toBeVisible()
    expect(completionPosts()).toHaveLength(1)
  })

  it('preserves the form and blocks confirmation after a stale Collateral cycle', async () => {
    let refreshed = false
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/collateral-loan-verification/complete')) {
        refreshed = true
        throw new ApiError(409, 'STALE_COLLATERAL_VERIFICATION', 'stale', String(path), 'now')
      }
      return collateralCase(refreshed ? replacementVerificationId : verificationId)
    })
    renderWorkspace()
    const user = userEvent.setup()
    const note = await screen.findByLabelText('Assessment note')
    await user.type(note, 'Retain this assessment.')
    await user.click(screen.getByRole('button', { name: 'Review verification completion' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByRole('heading', { name: 'Verification cycle changed' })).toBeVisible()
    expect(note).toHaveValue('Retain this assessment.')
    expect(screen.queryByRole('button', { name: 'Review verification completion' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Review updated cycle' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Review verification completion' })).toBeVisible())
  })
})

function renderWorkspace(queryClient = createQueryClient()) {
  const router = createTestRouter([`/staff/applications/${applicationId}/verification`])
  render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}
