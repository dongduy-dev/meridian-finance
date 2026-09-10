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
import { queueFixture } from '../api/contracts.test'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-10T10:00:00Z',
  userId: '77777777-7777-4777-8777-777777777777', email: 'accounting@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'], permissions: ['loan:disburse'],
}

function renderPage() {
  const router = createTestRouter(['/staff/work/disbursements'])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Staff ready-disbursement queue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('uses server-side product filtering and paging without scanning the Staff application index', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => ({
      ...queueFixture(String(path).includes('page=1') ? 1 : 0),
      totalElements: 26,
      totalPages: 2,
    }))
    renderPage()
    const user = userEvent.setup()

    expect(await screen.findByText('UCL-20260910-000001')).toBeVisible()
    await user.selectOptions(screen.getByLabelText('Product'), 'UNSECURED_CONSUMER_LOAN')
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).includes('/staff/disbursement-work?')
      && String(path).includes('productCode=UNSECURED_CONSUMER_LOAN'))).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).includes('/staff/disbursement-work?')
      && String(path).includes('page=1'))).toBe(true)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).includes('/staff/loan-applications?'))).toBe(false)
  })

  it('renders the ready stage and exact workspace link', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(queueFixture())
    renderPage()

    expect(await screen.findByText('Ready to disburse')).toBeVisible()
    expect(screen.getByText(/\*{4}7890/)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Open disbursement workspace' }))
      .toHaveAttribute('href', '/staff/applications/11111111-1111-4111-8111-111111111111/disbursement')
  })

  it('fails closed for unknown stage or lifecycle values', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(queueFixture(0, {
      workStage: 'FUTURE_STAGE',
      applicationStatus: 'FUTURE_STATUS',
    }))
    renderPage()

    expect(await screen.findByText('Work stage unavailable')).toBeVisible()
    expect(screen.getByText(/Unknown operational evidence requires an authoritative refresh/)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Inspect unavailable state' })).toBeVisible()
  })
})
