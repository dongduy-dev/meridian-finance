import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { historyApplicationId, reviewHistoryFixture } from '@/test/staff-review-history-fixture'
import { ReviewHistoryPanel } from './ReviewHistoryPanel'

vi.mock('@/features/auth/api/auth-api', async () => ({
  ...await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api'),
  refresh: vi.fn(), logout: vi.fn(),
}))
vi.mock('@/lib/api', async () => ({
  ...await vi.importActual<typeof import('@/lib/api')>('@/lib/api'), apiRequest: vi.fn(),
}))
const staff: AuthResponse = { tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-10-01T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'officer@meridian.local',
  userType: 'STAFF', customerId: null, roles: [], permissions: ['loan:review'] }
function renderPanel() {
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><ReviewHistoryPanel loanApplicationId={historyApplicationId} /></AuthProvider></QueryClientProvider>)
}

describe('ReviewHistoryPanel', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(authApi.refresh).mockResolvedValue(staff) })

  it.each(['loan:review', 'approval:recommend', 'approval:decide'])(
    'renders seeded Staff actors in ordered read-only history with %s and distinct rationale and credit-note labels', async (permission) => {
      vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: [permission] })
      vi.mocked(api.apiRequest).mockResolvedValue(reviewHistoryFixture())
      renderPanel()
      await screen.findByText('Synthetic decision analysis.')
      expect(screen.getAllByRole('heading', { level: 3, name: /^Review Cycle/ }).map((heading) => heading.textContent)).toEqual(['Review Cycle 1', 'Review Cycle 2', 'Review Cycle 3'])
      const first = within(screen.getByRole('region', { name: 'Review Cycle 1' }))
      expect(first.getAllByText('Reason')).toHaveLength(2)
      expect(first.getAllByText('Internal credit note')).toHaveLength(2)
      expect(first.getByText('Earlier recommendation rationale.')).toBeVisible()
      expect(first.getByText('Earlier decision rationale.')).toBeVisible()
      expect(first.getAllByText('History Loan Officer')).toHaveLength(2)
      expect(first.getByText('History Approver')).toBeVisible()
      expect(screen.getByText('Assignment unavailable')).toBeVisible()
      expect(screen.getByText('Staff member unavailable')).toBeVisible()
      expect(screen.getByText('Restricted to the responsible Loan Officer and authorized Approvers.')).toBeVisible()
      expect(screen.getByText('No recommendation recorded for this cycle.')).toBeVisible()
      expect(screen.queryByRole('button')).not.toBeInTheDocument()
      expect(api.apiRequest).toHaveBeenCalledTimes(1)
      expect(api.apiRequest).toHaveBeenCalledWith(`/staff/loan-applications/${historyApplicationId}/review-history`, expect.anything())
    },
  )

  it('shows loading and authoritative empty history', async () => {
    let resolve!: (value: unknown) => void
    vi.mocked(api.apiRequest).mockImplementation(() => new Promise((done) => { resolve = done }))
    renderPanel()
    expect(await screen.findByRole('status')).toHaveTextContent('Loading review history')
    resolve({ ...reviewHistoryFixture(), cycles: [] })
    expect(await screen.findByText('No review cycles have been recorded.')).toBeVisible()
  })

  it('recovers a failed history read through an explicit retry', async () => {
    vi.mocked(api.apiRequest).mockRejectedValueOnce(new Error('unavailable')).mockResolvedValue(reviewHistoryFixture())
    renderPanel()
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Synthetic decision analysis.')).toBeVisible()
  })

  it.each(['loan:read', 'repayment:update', 'partner:read', 'approval:decide:all'])(
    'does not query history for %s alone', async (permission) => {
      vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: [permission] })
      renderPanel()
      await vi.waitFor(() => expect(authApi.refresh).toHaveBeenCalled())
      expect(api.apiRequest).not.toHaveBeenCalled()
      expect(screen.queryByRole('heading', { name: 'Review and decision history' })).not.toBeInTheDocument()
    },
  )

  it('shows changed access without rendering notes after a forbidden read', async () => {
    vi.mocked(api.apiRequest).mockRejectedValue(new ApiError(403, 'ACCESS_DENIED', 'Denied', '/review-history', '2026-10-01T08:00:00'))
    renderPanel()
    expect(await screen.findByText('Application access changed')).toBeVisible()
    expect(screen.queryByText('Synthetic decision analysis.')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('handles unknown action and cycle status without making actions available', async () => {
    const value = reviewHistoryFixture()
    value.cycles[0]!.status = 'FUTURE_STATUS'
    value.cycles[0]!.recommendation!.action = 'FUTURE_ACTION'
    vi.mocked(api.apiRequest).mockResolvedValue(value)
    renderPanel()
    expect(await screen.findByText('Recommendation action unavailable')).toBeVisible()
    expect(screen.getByText(/Review status unavailable/)).toBeVisible()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
