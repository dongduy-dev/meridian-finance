import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { accountFixture, applicationId, historyFixture, terminalAccountFixture } from '../api/contracts.test'

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
  const router = createTestRouter([`/staff/applications/${applicationId}/loan-account`])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Staff LoanAccount workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).includes('/repayments?') ? historyFixture() : accountFixture())
  })

  it('renders authoritative balances, permanently masked destination, final schedule, and immutable history', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'LA-20260910-000001' })).toBeVisible()
    expect(screen.getByText('Vietcombank (VCB)')).toBeVisible()
    expect(screen.getByText('********')).toBeVisible()
    expect(screen.queryByText('1234567890')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Current servicing summary' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Immutable final schedule' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Immutable repayment history' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Record repayment' })).toBeVisible()
    expect(screen.queryByRole('button', { name: /Evaluate overdue/i })).not.toBeInTheDocument()
  })

  it('shows closure only to an authorized Accounting Officer for coherent SETTLED evidence', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff,
      permissions: ['loan:read', 'loan:account:close'],
    })
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).includes('/repayments?') ? historyFixture() : terminalAccountFixture('SETTLED'))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Financially settled account' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Record repayment' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Administrative closure' })).toBeVisible()
  })

  it('shows settlement only to an Approver with the exact capability', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff,
      roles: ['APPROVER'],
      permissions: ['loan:read', 'loan:settlement:approve'],
    })
    renderPage()
    expect(await screen.findByRole('link', { name: 'Administrative Full-Balance Settlement' }))
      .toBeVisible()
    expect(screen.queryByRole('link', { name: 'Administrative closure' })).not.toBeInTheDocument()
  })
})
