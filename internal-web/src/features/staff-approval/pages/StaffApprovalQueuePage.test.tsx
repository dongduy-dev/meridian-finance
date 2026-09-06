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

const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-06T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'approver@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['APPROVER'], permissions: ['approval:decide'],
}

function page(number: number, empty = false) {
  return { page: number, size: 25, totalElements: empty ? 0 : 26, totalPages: empty ? 0 : 2,
    items: empty ? [] : [{ loanApplicationId: '11111111-1111-4111-8111-111111111111', applicationNumber: `UCL-${number + 1}`,
      productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'PERSONAL', requestedAmount: 10_000_000,
      requestedTermMonths: 6, applicationStatus: 'APPROVAL_PENDING', submittedAt: '2026-09-06T08:00:00',
      recommendationId: '44444444-4444-4444-8444-444444444444', recommendationAction: 'RECOMMEND_APPROVAL',
      recommendationSubmittedAt: '2026-09-06T08:20:00', makerCheckerEligible: true, decisionAvailable: true }] }
}

function renderPage() {
  const router = createTestRouter(['/staff/work/approvals'])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Staff approval queue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('shows loading then the authoritative empty state', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(page(0, true))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Independent decision queue' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'No approval work' })).toBeVisible()
  })

  it('requests the next server-owned page', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => page(String(path).includes('page=1') ? 1 : 0))
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByText('UCL-1')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /Next/ }))
    expect(await screen.findByText('UCL-2')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('page=1'))).toBe(true)
  })

  it('renders an authoritative queue error without invented membership', async () => {
    vi.mocked(api.apiRequest).mockRejectedValue(new NetworkError('unavailable'))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Work queue unavailable' }, { timeout: 5_000 })).toBeVisible()
    expect(screen.queryByText(/UCL-/)).not.toBeInTheDocument()
  })
})
