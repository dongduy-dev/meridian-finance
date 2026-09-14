import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { closureQueueFixture, settlementQueueFixture } from '../api/contracts.test'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const actor = (role: string, permission: string): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-14T10:00:00Z',
  userId: '77777777-7777-4777-8777-777777777777', email: 'staff@meridian.local',
  userType: 'STAFF', customerId: null, roles: [role], permissions: [permission],
})

function renderRoute(path: string) {
  const router = createTestRouter([path])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Staff CP9 work queues', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })
  afterEach(() => cleanup())

  it('uses only the backend settlement queue and sends product/paging filters', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor('APPROVER', 'loan:settlement:approve'))
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      settlementQueueFixture(String(path).includes('page=1') ? 1 : 0))
    renderRoute('/staff/work/settlements')
    const user = userEvent.setup()
    expect(await screen.findByText('UCL-20260910-000001')).toBeVisible()
    await user.selectOptions(screen.getByLabelText('Product'), 'UNSECURED_CONSUMER_LOAN')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes(
      '/staff/settlement-work?page=1&size=25&productCode=UNSECURED_CONSUMER_LOAN',
    ))).toBe(true)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).includes('/staff/servicing-work'))).toBe(false)
  })

  it('uses only the backend closure queue and renders payoff provenance', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor('ACCOUNTING_OFFICER', 'loan:account:close'))
    vi.mocked(api.apiRequest).mockResolvedValue(closureQueueFixture())
    renderRoute('/staff/work/closures')
    expect(await screen.findByRole('heading', { name: 'Closure work queue' })).toBeVisible()
    expect(await screen.findByText(/Administrative settlement/)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Open closure' })).toHaveAttribute(
      'href', '/staff/applications/11111111-1111-4111-8111-111111111111/closure',
    )
    expect(vi.mocked(api.apiRequest).mock.calls.every(([path]) =>
      String(path).includes('/staff/closure-work'))).toBe(true)
  })

  it.each([
    ['/staff/work/settlements', 'ACCOUNTING_OFFICER', 'loan:settlement:approve'],
    ['/staff/work/closures', 'APPROVER', 'loan:account:close'],
  ] as const)('does not expose %s to the wrong business role', async (path, role, permission) => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(role, permission))
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
    expect(vi.mocked(api.apiRequest)).not.toHaveBeenCalled()
  })

  it.each([
    ['/staff/work/settlements', 'APPROVER', 'loan:settlement:approve', 'No settlement work'],
    ['/staff/work/closures', 'ACCOUNTING_OFFICER', 'loan:account:close', 'No closure work'],
  ] as const)('renders the server-owned empty state for %s', async (path, role, permission, emptyTitle) => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(role, permission))
    vi.mocked(api.apiRequest).mockResolvedValue({
      page: 0, size: 25, totalElements: 0, totalPages: 0, items: [],
    })
    renderRoute(path)
    expect(await screen.findByText(emptyTitle, {}, { timeout: 10_000 })).toBeVisible()
  })

  it('fails closed when the settlement queue read is unavailable', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor('APPROVER', 'loan:settlement:approve'))
    vi.mocked(api.apiRequest).mockRejectedValue(new ApiError(
      503, 'SERVICE_UNAVAILABLE', 'unavailable', '/staff/settlement-work', 'now',
    ))
    renderRoute('/staff/work/settlements')
    expect(await screen.findByText('Work queue unavailable', {}, { timeout: 10_000 })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Open settlement' })).not.toBeInTheDocument()
  })
})
