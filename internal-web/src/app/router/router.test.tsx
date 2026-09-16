import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import { ApiError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { createTestRouter } from './router'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, login: vi.fn(), refresh: vi.fn(), logout: vi.fn() }
})

const staff = (permissions: string[] = ['loan:read'], roles: string[] = ['LOAN_OFFICER']): AuthResponse => ({
  tokenType: 'Bearer',
  accessToken: 'staff-token',
  expiresAt: '2026-09-01T01:00:00Z',
  userId: '11111111-1111-4111-8111-111111111111',
  email: 'staff@meridian.local',
  userType: 'STAFF',
  customerId: null,
  roles,
  permissions,
})

const admin = (permissions: string[] = ['partner:read'], roles: string[] = ['BACK_OFFICE_ADMIN']): AuthResponse => staff(permissions, roles)

function renderRoute(path: string) {
  const router = createTestRouter([path])
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider><RouterProvider router={router} /></AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

describe('internal router access contract', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => vi.unstubAllGlobals())

  it('redirects an anonymous direct /staff visit to login and focuses its deferred heading', async () => {
    vi.mocked(authApi.refresh).mockRejectedValue(new ApiError(401, 'INVALID_REFRESH_TOKEN', 'required', '/auth/refresh', 'now'))
    const router = renderRoute('/staff')
    const heading = await screen.findByRole('heading', { name: 'Staff sign in', level: 1 })
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    await waitFor(() => expect(heading).toHaveFocus())
  })

  it('redirects an anonymous direct /admin visit to login and preserves the destination safely', async () => {
    vi.mocked(authApi.refresh).mockRejectedValue(new ApiError(401, 'INVALID_REFRESH_TOKEN', 'required', '/auth/refresh', 'now'))
    const router = renderRoute('/admin')
    await screen.findByRole('heading', { name: 'Staff sign in', level: 1 })
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.state).toEqual({ from: '/admin' })
  })

  it('returns an authorized admin login to the preserved /admin destination', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.refresh).mockRejectedValue(new ApiError(401, 'INVALID_REFRESH_TOKEN', 'required', '/auth/refresh', 'now'))
    vi.mocked(authApi.login).mockResolvedValue(admin())
    const router = renderRoute('/admin')
    await user.type(await screen.findByLabelText('Email'), 'backoffice.admin@meridian.local')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { name: 'Back-Office Administration' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin')
  })

  it('redirects authenticated Staff away from login to the permitted Staff destination', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    const router = renderRoute('/login')
    expect(await screen.findByRole('heading', { name: 'Internal operations' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/staff')
  })

  it.each(['/login', '/'])('resolves %s to /admin for an authenticated admin-only actor', async (path) => {
    vi.mocked(authApi.refresh).mockResolvedValue(admin())
    const router = renderRoute(path)
    expect(await screen.findByRole('heading', { name: 'Back-Office Administration' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin')
  })

  it('preserves /staff as the root default for a Staff-operational actor', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    const router = renderRoute('/')
    expect(await screen.findByRole('heading', { name: 'Internal operations' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/staff')
  })

  it('renders the Staff foundation and metadata-derived navigation for an exact supported capability', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['document:review']))
    renderRoute('/staff')
    expect(await screen.findByRole('heading', { name: 'Internal operations' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Internal operations' })).toHaveAttribute('href', '/staff')
    expect(screen.getByText('Secure session established')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Back-Office Administration' })).not.toBeInTheDocument()
  })

  it('renders the administration foundation without Staff navigation or administration data requests', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    vi.mocked(authApi.refresh).mockResolvedValue(admin())
    renderRoute('/admin')
    expect(await screen.findByRole('heading', { name: 'Back-Office Administration' })).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Administration navigation' })).toBeVisible()
    expect(screen.queryByRole('navigation', { name: 'Staff navigation' })).not.toBeInTheDocument()
    expect(screen.getByText('Administrative session established')).toBeVisible()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('exposes Partner navigation and list only through exact partner:read', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    vi.mocked(authApi.refresh).mockResolvedValue(admin(['partner:read']))
    renderRoute('/admin/partners')
    expect(await screen.findByRole('heading', { name: 'Partner Companies' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Partners' })).toHaveAttribute('href', '/admin/partners')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('blocks partner:manage-only direct Partner entry before any Partner query runs', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    vi.mocked(authApi.refresh).mockResolvedValue(admin(['partner:manage']))
    renderRoute('/admin/partners')
    expect(await screen.findByRole('heading', { name: 'No administrative access' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Partners' })).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows both authorized areas without creating a persona switcher', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(admin(['loan:read', 'partner:read'], ['LOAN_OFFICER', 'BACK_OFFICE_ADMIN']))
    renderRoute('/admin')
    expect(await screen.findByRole('heading', { name: 'Back-Office Administration' })).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Internal areas' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Staff Operations' })).toHaveAttribute('href', '/staff')
    expect(screen.getByRole('link', { name: 'Back-Office Administration' })).toHaveAttribute('href', '/admin')
    expect(screen.getByRole('navigation', { name: 'Administration navigation' })).toBeVisible()
  })

  it('shows no operational access, no data, and no operational navigation for unsupported Staff', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['identity:user:manage']))
    renderRoute('/staff')
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
    expect(screen.queryByRole('navigation', { name: 'Staff navigation' })).not.toBeInTheDocument()
    expect(screen.queryByText('Secure session established')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to Back-Office Administration' })).toHaveAttribute('href', '/admin')
    expect(screen.getAllByRole('button', { name: 'Sign out' }).length).toBeGreaterThan(0)
  })

  it('shows a safe administrative no-access state to a Staff-only actor without loading hidden data', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    const router = renderRoute('/admin')
    expect(await screen.findByRole('heading', { name: 'No administrative access' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin')
    expect(screen.getByText(/No Partner, Product, User, configuration, or audit data has been loaded/)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Return to Staff Operations' })).toHaveAttribute('href', '/staff')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it.each([
    [['partner:read:all'], []],
    [['partner:*'], []],
    [[], ['BACK_OFFICE_ADMIN']],
  ] as const)('does not bypass the administration guard through permissions %j or roles %j', async (permissions, roles) => {
    vi.mocked(authApi.refresh).mockResolvedValue(admin([...permissions], [...roles]))
    renderRoute('/admin')
    expect(await screen.findByRole('heading', { name: 'No administrative access' })).toBeVisible()
    expect(screen.queryByRole('navigation', { name: 'Administration navigation' })).not.toBeInTheDocument()
  })

  it('does not grant navigation or direct-route access through a permission prefix', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:read:all']))
    const router = renderRoute('/staff/applications')
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/staff/applications')
    expect(screen.queryByRole('link', { name: 'Applications' })).not.toBeInTheDocument()
  })

  it('refuses direct case access without the exact loan read permission', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['document:review']))
    const router = renderRoute('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee')
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee')
  })

  it.each([
    '/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/verification',
    '/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/review',
  ])('allows the CP4 direct route %s with loan:review and without loan:read', async (path) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:review']))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })))
    const router = renderRoute(path)
    await screen.findByRole('heading', { name: /product verification|Loan Officer review/i })
    expect(router.state.location.pathname).toBe(path)
    expect(screen.queryByRole('heading', { name: 'No operational access' })).not.toBeInTheDocument()
  })

  it.each([
    'loan:read',
    'approval:recommend',
    'loan:review:all',
  ])('does not grant CP4 direct-route access through %s', async (permission) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff([permission]))
    renderRoute('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/verification')
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
  })

  it('preserves a safe exact-evidence query string through the authentication redirect', async () => {
    vi.mocked(authApi.refresh).mockRejectedValue(new ApiError(401, 'INVALID_REFRESH_TOKEN', 'required', '/auth/refresh', 'now'))
    const path = '/staff/applications/11111111-1111-4111-8111-111111111111/documents?checklistItemId=22222222-2222-4222-8222-222222222222&documentVersionId=44444444-4444-4444-8444-444444444444'
    const router = renderRoute(path)
    await screen.findByRole('heading', { name: 'Staff sign in' })
    expect(router.state.location.state).toEqual({ from: path })
  })

  it.each([
    ['/staff/work/documents', ['document:review'], 'Document review'],
    ['/staff/work/corrections', ['loan:correction:staff'], 'Staff corrections'],
    ['/staff/work/approvals', ['approval:decide'], 'Independent decision queue'],
    ['/staff/work/contracts', ['loan:contract:read'], 'Contract and readiness queue'],
    ['/staff/work/disbursements', ['loan:disburse'], 'Ready-disbursement queue'],
    ['/staff/work/servicing', ['loan:read'], 'LoanAccount servicing queue'],
  ] as const)('allows %s only through its exact capability', async (path, permissions, heading) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff([...permissions]))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]', { status: 200 })))
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()
  })

  it.each([
    ['/staff/work/documents', ['document:review:all']],
    ['/staff/work/corrections', ['loan:correction']],
    ['/staff/work/approvals', ['approval:decide:all']],
    ['/staff/work/contracts', ['loan:contract:read:all']],
    ['/staff/work/disbursements', ['loan:disburse:all']],
    ['/staff/work/servicing', ['loan:read:all']],
  ] as const)('does not grant CP3 route %s through a permission prefix', async (path, permissions) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff([...permissions]))
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
  })

  it('protects the independent decision route with exact approval:decide', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['approval:decide']))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })))
    renderRoute('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/decision')
    expect(await screen.findByRole('heading', { name: 'Loading independent decision' })).toBeVisible()
  })

  it('protects the contract workspace with exact loan:contract:read', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:contract:read']))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })))
    renderRoute('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/contract')
    expect(await screen.findByRole('heading', { name: 'Loading contract workspace' })).toBeVisible()
  })

  it('protects the disbursement workspace with exact loan:disburse', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:disburse']))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })))
    renderRoute('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/disbursement')
    expect(await screen.findByRole('heading', { name: 'Loading disbursement workspace' })).toBeVisible()
  })

  it('protects the LoanAccount workspace with loan:read and repayment entry with repayment:update', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:read']))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })))
    renderRoute('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/loan-account')
    expect(await screen.findByRole('heading', { name: 'Loading LoanAccount workspace' })).toBeVisible()

    vi.mocked(authApi.refresh).mockResolvedValue(staff(['repayment:update']))
    renderRoute('/staff/applications/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee/repayments/new')
    expect(await screen.findByRole('heading', { name: 'Repayment workspace unavailable' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'LoanAccount read authority required' })).toBeVisible()
  })

  it.each([
    ['/staff/work/settlements', 'loan:settlement:approve', 'APPROVER', 'Settlement work queue'],
    ['/staff/work/closures', 'loan:account:close', 'ACCOUNTING_OFFICER', 'Closure work queue'],
  ] as const)('requires both the exact permission and business role for %s', async (path, permission, role, heading) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff([permission], [role]))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })))
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()

    cleanup()
    vi.mocked(authApi.refresh).mockResolvedValue(staff([permission], ['LOAN_OFFICER']))
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
  })

  it('rejects a Customer-shaped session before it can reach Staff routes', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff(),
      userType: 'CUSTOMER',
      customerId: '22222222-2222-4222-8222-222222222222',
    })
    const router = renderRoute('/staff/applications')
    expect(await screen.findByRole('heading', { name: 'Staff sign in' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('renders the normal not-found state for an authorized admin visiting an unimplemented Admin child', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(admin())
    renderRoute('/admin/users')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeVisible()
    expect(screen.queryByText(/Admin workspace|User administration/)).not.toBeInTheDocument()
  })

  it('renders the safe unavailable state for an unrelated unknown route', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    renderRoute('/not-a-route')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeVisible()
  })
})
