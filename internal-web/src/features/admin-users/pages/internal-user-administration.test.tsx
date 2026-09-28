import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError, NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const userId = '00000000-0000-0000-0000-000000000304'
const internalUser = {
  userId,
  email: 'loan.officer@meridian.local',
  displayName: 'Loan Officer Demo',
  status: 'ACTIVE',
  assignedRoleCodes: ['LOAN_OFFICER'],
}
const roles = [
  { code: 'APPROVER', name: 'Approver' },
  { code: 'LOAN_OFFICER', name: 'Loan Officer' },
]
const actor = (permissions: string[] = ['identity:user:manage']): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'token', expiresAt: '2026-09-17T12:00:00Z',
  userId, email: internalUser.email, userType: 'STAFF', customerId: null,
  roles: ['BACK_OFFICE_ADMIN'], permissions,
})

function renderPage() {
  const queryClient = createQueryClient()
  const router = createTestRouter(['/admin/users'])
  render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return { queryClient, router }
}

describe('Internal User administration page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(actor())
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).endsWith('/assignable-roles') ? roles : [internalUser])
  })

  it('renders a deterministic-ID user without entering the unavailable state', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles')
      ? [...roles, { code: 'FUTURE_ROLE', name: 'Future Role' }]
      : [{ ...internalUser, status: 'FUTURE_STATUS', assignedRoleCodes: ['LOAN_OFFICER', 'LEGACY_ROLE'] }])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Internal Users' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Loan Officer Demo' })).toBeVisible()
    expect(screen.getAllByText(internalUser.email).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Unknown status \(FUTURE_STATUS\)/).length).toBeGreaterThan(0)
    expect(screen.getByText(/Meridian returned an unrecognized status/i)).toBeVisible()
    expect(screen.getByText(/Roles group predefined Staff access/i)).toBeVisible()
    expect(screen.getByText('Other assigned roles')).toBeVisible()
    expect(document.body.textContent).not.toMatch(/backend-owned|Other assigned backend roles/i)
    expect(screen.getByText('LEGACY_ROLE')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Customer/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Internal Users unavailable' })).not.toBeInTheDocument()
  })

  it('renders loading, empty, and protected-query error states with retry', async () => {
    let resolveUsers!: (value: unknown) => void
    vi.mocked(api.apiRequest).mockImplementation((path) => String(path).endsWith('/assignable-roles')
      ? Promise.resolve(roles)
      : new Promise((done) => { resolveUsers = done }))
    const first = renderPage()
    expect(await screen.findByText('Loading Internal Users…')).toBeVisible()
    resolveUsers([])
    expect(await screen.findByText('No internal Staff Users are available.')).toBeVisible()
    first.router.dispose()
    cleanup()

    vi.mocked(api.apiRequest).mockRejectedValue(new ApiError(
      400, 'UNEXPECTED_RESPONSE', 'failed', '/admin/internal-users', 'now', 'request-1',
    ))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Internal Users unavailable' })).toBeVisible()
    expect(screen.getByText(/request-1/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('confirms suspension before the exact target-state command and restores focus on cancel', async () => {
    let authoritative = internalUser
    let confirm!: (value: unknown) => void
    let reads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if (!(options as RequestInit | undefined)?.method) {
        reads += 1
        return [authoritative]
      }
      if (String(path).endsWith('/status')) return new Promise((done) => {
        confirm = (value) => { authoritative = value as typeof internalUser; done(value) }
      })
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Loan Officer Demo' })
    await user.selectOptions(screen.getByLabelText('Target status'), 'SUSPENDED')
    const trigger = screen.getByRole('button', { name: 'Apply status' })
    await user.click(trigger)
    let dialog = screen.getByRole('dialog', { name: 'Confirm User access change' })
    expect(dialog).toHaveTextContent('Loan Officer Demo (loan.officer@meridian.local)')
    expect(dialog).toHaveTextContent('Active → Suspended')
    expect(dialog).toHaveTextContent(/lose access and all active sessions will be revoked/i)
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(trigger).toHaveFocus())
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toHaveLength(0)

    await user.click(trigger)
    dialog = screen.getByRole('dialog', { name: 'Confirm User access change' })
    await user.click(within(dialog).getByRole('button', { name: 'Apply Suspended status' }))
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0)
    const statusCommand = vi.mocked(api.apiRequest).mock.calls.find(([path, options]) =>
      String(path).endsWith('/status') && (options as RequestInit | undefined)?.method === 'PUT')
    expect((statusCommand?.[1] as { body: unknown }).body).toEqual({ status: 'SUSPENDED' })
    confirm({ ...internalUser, status: 'SUSPENDED' })
    await waitFor(() => expect(reads).toBeGreaterThan(1))
    expect((await screen.findAllByText('Suspended')).length).toBeGreaterThan(0)
  })

  it('does not retry an unknown status result and preserves the last confirmed state while refreshing', async () => {
    let reads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if (!(options as RequestInit | undefined)?.method) { reads += 1; return [internalUser] }
      throw new NetworkError('response lost')
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Loan Officer Demo' })
    await user.selectOptions(screen.getByLabelText('Target status'), 'DISABLED')
    await user.click(screen.getByRole('button', { name: 'Apply status' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Confirm User access change' })).getByRole('button', { name: 'Apply Disabled status' }))
    expect(await screen.findByRole('heading', { name: 'Status was not confirmed' })).toBeVisible()
    expect(screen.getByText(/could not confirm the User status change.*latest information was refreshed/i)).toBeVisible()
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0)
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toHaveLength(1)
    await waitFor(() => expect(reads).toBeGreaterThan(1))
  })

  it('assigns and removes one role only after backend confirmation', async () => {
    let authoritative = internalUser
    let confirmAssignment!: (value: unknown) => void
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if (!(options as RequestInit | undefined)?.method) return [authoritative]
      const assigned = (options as { body: { assigned: boolean } }).body.assigned
      if (assigned) return new Promise((done) => {
        confirmAssignment = (value) => { authoritative = value as typeof internalUser; done(value) }
      })
      authoritative = {
        ...authoritative,
        assignedRoleCodes: authoritative.assignedRoleCodes.filter((code) => code !== 'APPROVER'),
      }
      return authoritative
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Loan Officer Demo' })
    await user.click(screen.getByRole('button', { name: 'Assign Approver' }))
    expect(screen.queryByRole('button', { name: 'Remove Approver' })).not.toBeInTheDocument()
    confirmAssignment({ ...internalUser, assignedRoleCodes: [...internalUser.assignedRoleCodes, 'APPROVER'] })
    expect(await screen.findByRole('button', { name: 'Remove Approver' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Remove Approver' }))
    expect(await screen.findByRole('button', { name: 'Assign Approver' })).toBeVisible()
    const puts = vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')
    expect(puts.map(([, options]) => (options as { body: unknown }).body)).toEqual([{ assigned: true }, { assigned: false }])
  })

  it('keeps reactivation direct without a confirmation dialog', async () => {
    const suspendedUser = { ...internalUser, status: 'SUSPENDED' }
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if (!(options as RequestInit | undefined)?.method) return [suspendedUser]
      return { ...suspendedUser, status: 'ACTIVE' }
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Loan Officer Demo' })
    await user.selectOptions(screen.getByLabelText('Target status'), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: 'Apply status' }))

    expect(screen.queryByRole('dialog', { name: 'Confirm User access change' })).not.toBeInTheDocument()
    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.some(([path, options]) =>
      String(path).endsWith('/status')
      && (options as RequestInit | undefined)?.method === 'PUT'
      && JSON.stringify((options as { body?: unknown }).body) === JSON.stringify({ status: 'ACTIVE' }))).toBe(true))
  })

  it('does not retry an unknown role result and preserves the confirmed assignment while refreshing', async () => {
    let reads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if (!(options as RequestInit | undefined)?.method) { reads += 1; return [internalUser] }
      throw new NetworkError('response lost')
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Loan Officer Demo' })
    await user.click(screen.getByRole('button', { name: 'Assign Approver' }))
    expect(await screen.findByRole('heading', { name: 'Role assignment was not confirmed' })).toBeVisible()
    expect(screen.getByText(/could not confirm the role assignment change.*latest information was refreshed/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Assign Approver' })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toHaveLength(1)
    await waitFor(() => expect(reads).toBeGreaterThan(1))
  })

  it('re-evaluates route access when a self-change refresh removes authority', async () => {
    let listReads = 0
    vi.mocked(authApi.refresh).mockResolvedValueOnce(actor()).mockResolvedValueOnce(actor([]))
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if (!(options as RequestInit | undefined)?.method) {
        listReads += 1
        if (listReads === 2) throw new ApiError(401, 'INVALID_TOKEN', 'Invalid token.', String(path), 'now')
        return [internalUser]
      }
      return { ...internalUser, assignedRoleCodes: [] }
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Loan Officer Demo' })
    await user.click(screen.getByRole('button', { name: 'Remove Loan Officer' }))
    expect(await screen.findByRole('heading', { name: 'No administrative access' })).toBeVisible()
  })

  it('returns to anonymous sign-in when a self-status change cannot refresh', async () => {
    let listReads = 0
    vi.mocked(authApi.refresh)
      .mockResolvedValueOnce(actor())
      .mockRejectedValueOnce(new ApiError(401, 'INVALID_REFRESH_TOKEN', 'Refresh authentication failed', '/auth/refresh', 'now'))
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if (!(options as RequestInit | undefined)?.method) {
        listReads += 1
        if (listReads === 2) throw new ApiError(401, 'INVALID_TOKEN', 'Invalid token.', String(path), 'now')
        return [internalUser]
      }
      return { ...internalUser, status: 'DISABLED' }
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Loan Officer Demo' })
    await user.selectOptions(screen.getByLabelText('Target status'), 'DISABLED')
    await user.click(screen.getByRole('button', { name: 'Apply status' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Confirm User access change' })).getByRole('button', { name: 'Apply Disabled status' }))
    expect(await screen.findByRole('heading', { name: 'Staff sign in' })).toBeVisible()
  })
})
