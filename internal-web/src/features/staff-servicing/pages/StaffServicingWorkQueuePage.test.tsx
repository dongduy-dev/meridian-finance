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
  userId: '77777777-7777-4777-8777-777777777777', email: 'servicing@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'], permissions: ['loan:read', 'repayment:update'],
}

function renderPage() {
  const router = createTestRouter(['/staff/work/servicing'])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Staff servicing work queue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('uses only the dedicated server queue with product, status, and paging filters', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => queueFixture(String(path).includes('page=1') ? 1 : 0))
    renderPage()
    const user = userEvent.setup()

    expect(await screen.findByText('UCL-20260910-000001')).toBeVisible()
    await user.selectOptions(screen.getByLabelText('Product'), 'UNSECURED_CONSUMER_LOAN')
    await user.selectOptions(screen.getByLabelText('Serviceable status'), 'OVERDUE')
    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => {
      const value = String(path)
      return value.includes('/staff/servicing-work?')
        && value.includes('productCode=UNSECURED_CONSUMER_LOAN')
        && value.includes('accountStatus=OVERDUE')
        && value.includes('page=1')
    })).toBe(true)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('/loan-accounts'))).toBe(false)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('/staff/loan-applications?'))).toBe(false)
  })

  it('renders ACTIVE and OVERDUE server states and fails closed for terminal or unknown values', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(queueFixture(0, 'OVERDUE'))
    renderPage()
    expect(await screen.findByRole('link', { name: 'Open LoanAccount' }))
      .toHaveAttribute('href', '/staff/applications/11111111-1111-4111-8111-111111111111/loan-account')
    expect(screen.getAllByText('Overdue').length).toBeGreaterThan(0)
    expect(screen.queryByText(/Evaluate overdue/i)).not.toBeInTheDocument()
  })
})
