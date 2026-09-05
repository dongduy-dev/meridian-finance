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
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-05T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'officer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['loan:review'],
}

function reviewCase(started: boolean) {
  return {
    loanApplicationId: applicationId, applicationNumber: 'UCL-20260905-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'PERSONAL', requestedAmount: 20_000_000,
    requestedTermMonths: 12, applicationStatus: started ? 'UNDER_REVIEW' : 'VERIFIED', submittedAt: '2026-09-05T08:00:00',
    documentReadiness: { uploadComplete: true, processingReady: true },
    productReadiness: { productVerificationResult: 'VERIFIED', readyForReview: true },
    reviewStartAvailable: !started,
    currentReviewCycle: started ? {
      reviewCycleId: '33333333-3333-4333-8333-333333333333', cycleNumber: 1,
      status: 'ACTIVE', startedAt: '2026-09-05T08:10:00', endedAt: null,
    } : null,
  }
}

describe('Staff review workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('reconciles a lost review-start response without retrying POST or exposing recommendation controls', async () => {
    let started = false
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/review/start')) {
        started = true
        throw new NetworkError('connection lost')
      }
      return reviewCase(started)
    })
    const router = createTestRouter([`/staff/applications/${applicationId}/review`])
    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Start review' }))
    await user.click(screen.getByRole('button', { name: 'Confirm review start' }))

    expect(await screen.findByText(/authoritative read confirms that review started/i)).toBeVisible()
    expect(screen.getByText(/Recommendation and Approver decision are intentionally outside Staff FE-CP4/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: /recommend|approve|reject/i })).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path]) => String(path).endsWith('/review/start'))).toHaveLength(1)
    expect(screen.queryByRole('link', { name: /Application overview/ })).not.toBeInTheDocument()
  })

  it('renders an unknown product-verification value neutrally and fails closed', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...reviewCase(false),
      productReadiness: { productVerificationResult: 'FUTURE_RESULT', readyForReview: true },
      reviewStartAvailable: true,
    })
    const router = createTestRouter([`/staff/applications/${applicationId}/review`])
    render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)

    expect(await screen.findByText('State unavailable')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Start review' })).not.toBeInTheDocument()
  })
})
