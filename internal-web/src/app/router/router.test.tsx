import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
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
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})

const staff = (permissions: string[] = ['loan:read']): AuthResponse => ({
  tokenType: 'Bearer',
  accessToken: 'staff-token',
  expiresAt: '2026-09-01T01:00:00Z',
  userId: '11111111-1111-4111-8111-111111111111',
  email: 'staff@meridian.local',
  userType: 'STAFF',
  customerId: null,
  roles: ['LOAN_OFFICER'],
  permissions,
})

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

  it('redirects authenticated Staff away from login to the permitted Staff destination', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    const router = renderRoute('/login')
    expect(await screen.findByRole('heading', { name: 'Internal operations' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/staff')
  })

  it('renders the Staff foundation and metadata-derived navigation for an exact supported capability', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['document:review']))
    renderRoute('/staff')
    expect(await screen.findByRole('heading', { name: 'Internal operations' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Internal operations' })).toHaveAttribute('href', '/staff')
    expect(screen.getByText('Secure session established')).toBeVisible()
  })

  it('shows no operational access, no data, and no operational navigation for unsupported Staff', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['identity:user:manage']))
    renderRoute('/staff')
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
    expect(screen.queryByRole('navigation', { name: 'Staff navigation' })).not.toBeInTheDocument()
    expect(screen.queryByText('Secure session established')).not.toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Sign out' }).length).toBeGreaterThan(0)
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
  ] as const)('allows %s only through its exact capability', async (path, permissions, heading) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff([...permissions]))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]', { status: 200 })))
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()
  })

  it.each([
    ['/staff/work/documents', ['document:review:all']],
    ['/staff/work/corrections', ['loan:correction']],
  ] as const)('does not grant CP3 route %s through a permission prefix', async (path, permissions) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff([...permissions]))
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

  it.each(['/admin/users', '/not-a-route'])('renders the safe unavailable state for %s', async (path) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeVisible()
    expect(screen.queryByText(/Admin workspace|User administration/)).not.toBeInTheDocument()
  })
})
