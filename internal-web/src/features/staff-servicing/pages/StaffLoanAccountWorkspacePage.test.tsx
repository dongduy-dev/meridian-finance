import { QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { accountFixture, applicationId, customerContextFixture, provenanceFixture, terminalAccountFixture } from '../api/contracts.test'

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
      String(path).includes('/servicing-context') ? customerContextFixture() : String(path).includes('/servicing-provenance?') ? provenanceFixture() : accountFixture())
  })

  it('renders authoritative balances, permanently masked destination, final schedule, and immutable history', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'LA-20260910-000001' })).toBeVisible()
    expect(screen.getByText('Vietcombank (VCB)')).toBeVisible()
    expect(screen.getByText('********')).toBeVisible()
    expect(screen.queryByText('1234567890')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Current servicing summary' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Final repayment schedule' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Repayment history' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Account activity' })).toBeVisible()
    expect(screen.getByText(/Confirmed by Mina Accounting/)).toBeVisible()
    expect(screen.getByText('Overdue evaluated').closest('li')).toHaveTextContent('Meridian system')
    expect(screen.queryByText(/System User|Unknown Staff/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Record repayment' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Customer' })).toBeVisible()
    expect(screen.getByText('Customer number')).toBeVisible()
    expect(screen.getByText('CUS-000001')).toBeVisible()
    expect(screen.getByText('Full name')).toBeVisible()
    expect(screen.getByText('Ari Customer')).toBeVisible()
    expect(screen.getByText('Current phone')).toBeVisible()
    expect(screen.getByText('0901234567')).toBeVisible()
    expect(screen.queryByText('Identity Reference')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Administrative Full-Balance Settlement' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Evaluate overdue/i })).not.toBeInTheDocument()
  })

  it('shows closure only to an authorized Accounting Officer for coherent SETTLED evidence', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff,
      permissions: ['loan:read', 'loan:account:close'],
    })
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).includes('/servicing-context') ? customerContextFixture() : String(path).includes('/servicing-provenance?') ? provenanceFixture('SETTLED') : terminalAccountFixture('SETTLED'))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Financially settled account' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Record repayment' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Administrative closure' })).toBeVisible()
    expect(await screen.findByText('Ari Customer')).toBeVisible()
    expect(screen.getByText('0901234567')).toBeVisible()
    expect(screen.getByText(/Approved by Mina Accounting/)).toBeVisible()
    expect(screen.queryByText(/Closed by Mina Accounting/)).not.toBeInTheDocument()
  })

  it('shows exact recorded-by attribution on the financial repayment row', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'Repayment history' })
    fireEvent.click(document.querySelector('details summary')!)
    expect(await screen.findByText(/Recorded by Mina Accounting/)).toBeVisible()
  })

  it('shows closure only when durable closure provenance exists', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).includes('/servicing-context') ? customerContextFixture() : String(path).includes('/servicing-provenance?') ? provenanceFixture('CLOSED') : terminalAccountFixture('CLOSED'))
    renderPage()
    expect(await screen.findByText(/Closed by Mina Accounting/)).toBeVisible()
  })

  it('neutralizes an unknown event and actor type', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/servicing-context')) return customerContextFixture()
      if (String(path).includes('/servicing-provenance?')) {
        const fixture = provenanceFixture()
        fixture.statusHistory[1]!.action = 'FUTURE_EVENT'
        fixture.statusHistory[1]!.actor.type = 'FUTURE_ACTOR'
        fixture.statusHistory[1]!.toStatus = 'FUTURE_STATUS'
        return fixture
      }
      return accountFixture()
    })
    renderPage()
    expect((await screen.findByText('Activity unavailable')).closest('li'))
      .toHaveTextContent('Actor unavailable')
    expect(screen.getByText('Activity unavailable').closest('li'))
      .toHaveTextContent('Status unavailable')
    expect(document.body.textContent).not.toContain('FUTURE_STATUS')
  })

  it('does not query Staff provenance for a Customer session', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff, userType: 'CUSTOMER', customerId: '88888888-8888-4888-8888-888888888888',
      permissions: ['loan:read:own'], roles: ['CUSTOMER'],
    })
    renderPage()
    expect(await screen.findByRole('heading', { name: /Access denied|Sign in|Not found/i }))
      .toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).includes('/servicing-provenance') || String(path).includes('/servicing-context'))).toBe(false)
  })

  it('shows settlement only to an Approver with the exact capability', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff,
      roles: ['APPROVER'],
      permissions: ['loan:read', 'loan:settlement:approve'],
    })
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/servicing-context')
      ? customerContextFixture(null) : String(path).includes('/servicing-provenance?') ? provenanceFixture() : accountFixture())
    renderPage()
    expect(await screen.findByText('CUS-000001')).toBeVisible()
    expect(screen.getByText('Ari Customer')).toBeVisible()
    expect(screen.queryByText('Current phone')).not.toBeInTheDocument()
    expect(screen.queryByText('0901234567')).not.toBeInTheDocument()
    expect(screen.queryByText('Identity Reference')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Record repayment' })).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Administrative Full-Balance Settlement' }))
      .toBeVisible()
    expect(screen.queryByRole('link', { name: 'Administrative closure' })).not.toBeInTheDocument()
  })
  it.each([
    { roles: [], permissions: ['loan:read'] },
    { roles: ['ACCOUNTING_OFFICER', 'APPROVER'], permissions: ['loan:read'] },
    { roles: [], permissions: ['loan:read', 'repayment:update', 'loan:settlement:approve', 'loan:account:close'] },
  ])('does not query or render Customer context without a qualifying purpose: $permissions', async (actor) => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, ...actor })
    renderPage()
    await screen.findByRole('heading', { name: 'Current servicing summary' })
    expect(screen.queryByRole('heading', { name: 'Customer' })).not.toBeInTheDocument()
    expect(screen.queryByText('Ari Customer')).not.toBeInTheDocument()
    expect(screen.queryByText('0901234567')).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('/servicing-context') || String(path).includes('/customers'))).toBe(false)
  })

  it('keeps narrow current contact for Staff with customer:read', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, roles: ['LOAN_OFFICER'], permissions: ['loan:read', 'customer:read'] })
    renderPage()
    expect(await screen.findByText('Ari Customer')).toBeVisible()
    expect(screen.getByText('0901234567')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Record repayment' })).not.toBeInTheDocument()
  })

  it('shows context loading independently of existing financial actions', async () => {
    let resolveContext!: (value: ReturnType<typeof customerContextFixture>) => void
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/servicing-context')
      ? new Promise((resolve) => { resolveContext = resolve }) : String(path).includes('/servicing-provenance?') ? provenanceFixture() : accountFixture())
    renderPage()
    expect(await screen.findByText('Loading Customer context…')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Record repayment' })).toBeVisible()
    await act(async () => resolveContext(customerContextFixture()))
    expect(await screen.findByText('Ari Customer')).toBeVisible()
  })

  it('fails closed on Customer context errors and retries only that read without hiding financial evidence', async () => {
    let unavailable = true
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/servicing-context')) {
        if (unavailable) throw new ApiError(409, 'SYSTEM_STATE_CONFLICT', 'unavailable', String(path), 'now')
        return customerContextFixture()
      }
      return String(path).includes('/servicing-provenance?') ? provenanceFixture() : accountFixture()
    })
    renderPage()
    expect(await screen.findByText('Customer context unavailable')).toBeVisible()
    expect(screen.queryByText('Ari Customer')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Record repayment' })).toBeVisible()
    unavailable = false
    const before = vi.mocked(api.apiRequest).mock.calls.length
    fireEvent.click(screen.getByRole('button', { name: 'Retry Customer context' }))
    expect(await screen.findByText('Ari Customer')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.slice(before).map(([path]) => path)).toEqual([`/staff/loan-applications/${applicationId}/servicing-context`])
  })

  it('rejects context for a different account and renders no card for null context', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/servicing-context')
      ? { ...customerContextFixture(), loanAccountId: applicationId } : String(path).includes('/servicing-provenance?') ? provenanceFixture() : accountFixture())
    renderPage()
    expect(await screen.findByText('Customer context unavailable')).toBeVisible()
    expect(screen.queryByText('Ari Customer')).not.toBeInTheDocument()
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/servicing-context')
      ? { ...customerContextFixture(), customer: null } : String(path).includes('/servicing-provenance?') ? provenanceFixture() : accountFixture())
    fireEvent.click(screen.getByRole('button', { name: 'Retry Customer context' }))
    await waitFor(() => expect(screen.queryByText('Customer context unavailable')).not.toBeInTheDocument())
    expect(screen.queryByRole('heading', { name: 'Customer' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Record repayment' })).toBeVisible()
  })

  it('refreshes the Customer context with the existing account and provenance reads', async () => {
    renderPage()
    await screen.findByText('Ari Customer')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled())
    const before = vi.mocked(api.apiRequest).mock.calls.length
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.length).toBe(before + 3))
    expect(vi.mocked(api.apiRequest).mock.calls.slice(before).map(([path]) => path).sort()).toEqual([
      `/loan-applications/${applicationId}/loan-account`,
      `/staff/loan-applications/${applicationId}/servicing-provenance?page=0&size=20`,
      `/staff/loan-applications/${applicationId}/servicing-context`,
    ].sort())
  })

})
