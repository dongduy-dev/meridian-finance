import { QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
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
import { adminUserKeys } from '../api/queries'

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

async function openManagement(name = 'Loan Officer Demo') {
  const trigger = await screen.findByRole('button', { name: 'Manage ' + name })
  await userEvent.setup().click(trigger)
  return screen.getByRole('dialog', { name: 'Manage ' + name })
}

function renderPage() {
  const queryClient = createQueryClient()
  const router = createTestRouter(['/admin/users'])
  render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return { queryClient, router }
}

function staffRecords(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    ...internalUser,
    userId: `00000000-0000-0000-0000-${String(index + 1000).padStart(12, '0')}`,
    displayName: `Staff ${String(index + 1).padStart(2, '0')}`,
    email: `staff${String(index + 1).padStart(2, '0')}@meridian.local`,
  }))
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

  it('creates a Staff User with backend roles and refreshes the authoritative list without a password field', async () => {
    const newUser = { userId: '00000000-0000-0000-0000-000000000306', email: 'new@meridian.local',
      displayName: 'New Staff', status: 'ACTIVE', assignedRoleCodes: ['APPROVER'] }
    let authoritative = [internalUser]
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'POST') { authoritative = [internalUser, newUser]; return newUser }
      return authoritative
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Manage Loan Officer Demo' })
    await user.click(screen.getByRole('button', { name: 'Create Internal User' }))
    expect(screen.getByText('The Staff member receives a secure link to set their password.')).toBeVisible()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/customer/i)).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'NEW@MERIDIAN.LOCAL')
    await user.type(screen.getByLabelText('Display name'), 'New Staff')
    await user.click(screen.getByLabelText('Approver'))
    await user.click(screen.getAllByRole('button', { name: 'Create Internal User' }).at(-1)!)
    expect(await screen.findByRole('button', { name: 'Manage New Staff' })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.find(([, options]) => (options as RequestInit | undefined)?.method === 'POST')?.[1])
      .toMatchObject({ body: { email: 'new@meridian.local', displayName: 'New Staff', roleCodes: ['APPROVER'] } })
  })

  it('sends a setup link only on an explicit action and never retries an unknown result', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'POST') throw new NetworkError('response lost')
      return [internalUser]
    })
    renderPage()
    await openManagement()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(0)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Send password setup link' }))
    expect(await screen.findByText(/Meridian could not confirm whether a new link was issued/i)).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
    expect(screen.queryByRole('button', { name: 'I reviewed the latest information' })).not.toBeInTheDocument()
    const retry = screen.getByRole('button', { name: 'Send replacement setup link' })
    expect(retry).toBeEnabled()
    await userEvent.setup().click(retry)
    await screen.findByRole('heading', { name: 'Password setup request not confirmed' })
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(2)
  })

  it('renders a deterministic-ID user without entering the unavailable state', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles')
      ? [...roles, { code: 'FUTURE_ROLE', name: 'Future Role' }]
      : [{ ...internalUser, status: 'FUTURE_STATUS', assignedRoleCodes: ['LOAN_OFFICER', 'LEGACY_ROLE'] }])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Internal Users' })).toBeVisible()
    await openManagement()
    expect(screen.getAllByText(internalUser.email).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Status unavailable').length).toBeGreaterThan(0)
    expect(screen.queryByText(/FUTURE_STATUS/)).not.toBeInTheDocument()
    expect(screen.getByText(/Current status is unavailable/i)).toBeVisible()
    expect(screen.getByText(/Assign roles to manage Staff responsibilities and access/i)).toBeVisible()
    expect(screen.getByText(/Other assigned roles remain listed above/)).toBeVisible()
    expect(document.body.textContent).not.toMatch(/backend-owned|Other assigned backend roles/i)
    expect(within(screen.getByRole('dialog')).getByText('Unrecognized role (LEGACY_ROLE)')).toBeVisible()
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
    await openManagement()
    await user.selectOptions(screen.getByLabelText('Target status'), 'SUSPENDED')
    const trigger = screen.getByRole('button', { name: 'Update access status' })
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
    await user.click(within(dialog).getByRole('button', { name: 'Update access status' }))
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
    await openManagement()
    await user.selectOptions(screen.getByLabelText('Target status'), 'DISABLED')
    await user.click(screen.getByRole('button', { name: 'Update access status' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Confirm User access change' })).getByRole('button', { name: 'Update access status' }))
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
    await openManagement()
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
    await openManagement()
    await user.selectOptions(screen.getByLabelText('Target status'), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: 'Update access status' }))

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
    await openManagement()
    await user.click(screen.getByRole('button', { name: 'Assign Approver' }))
    expect(await screen.findByRole('heading', { name: 'Role assignment was not confirmed' })).toBeVisible()
    expect(screen.getByText(/could not confirm the role assignment change.*latest information was refreshed/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Assign Approver' })).toBeEnabled()
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
    await openManagement()
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
    await openManagement()
    await user.selectOptions(screen.getByLabelText('Target status'), 'DISABLED')
    await user.click(screen.getByRole('button', { name: 'Update access status' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Confirm User access change' })).getByRole('button', { name: 'Update access status' }))
    expect(await screen.findByRole('heading', { name: 'Staff sign in' })).toBeVisible()
  })

  it('shows compact identities, all roles, semantic statuses, and explicit unknown fallbacks', async () => {
    const records = staffRecords(4).map((record, index) => ({ ...record,
      status: ['ACTIVE', 'SUSPENDED', 'DISABLED', '__proto__'][index],
      assignedRoleCodes: index === 0 ? ['APPROVER', 'LOAN_OFFICER', 'LEGACY_ROLE'] : [],
    }))
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles') ? roles : records)
    renderPage()
    const table = await screen.findByRole('table', { name: 'Staff directory' })
    expect(within(table).getAllByRole('row')).toHaveLength(5)
    records.forEach((record) => expect(within(table).getByText(record.email)).toBeVisible())
    const first = within(table).getByRole('row', { name: /Staff 01/ })
    expect(first).toHaveTextContent('Approver')
    expect(first).toHaveTextContent('Loan Officer')
    expect(first).toHaveTextContent('Unrecognized role (LEGACY_ROLE)')
    expect(within(table).getByText('Active')).toHaveClass('text-success')
    expect(within(table).getByText('Suspended')).toHaveClass('text-warning')
    expect(within(table).getByText('Disabled')).toHaveClass('text-danger')
    expect(within(table).getByText('Status unavailable')).toHaveClass('text-muted-foreground')
    expect(within(table).getAllByText('No roles assigned')).toHaveLength(3)
    expect(screen.queryByRole('button', { name: 'Send password setup link' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Target status' })).not.toBeInTheDocument()
  })

  it('searches names and email locally, combines status and assigned-role filters, and resets without persisting identity', async () => {
    const records = [internalUser, { ...staffRecords(1)[0], displayName: 'Avery Approver',
      status: 'SUSPENDED', assignedRoleCodes: ['APPROVER', 'LOAN_OFFICER'] }]
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles') ? roles : records)
    const { router, queryClient } = renderPage()
    await screen.findByRole('table')
    const calls = vi.mocked(api.apiRequest).mock.calls.length
    const user = userEvent.setup()
    const search = screen.getByRole('searchbox', { name: 'Search Staff' })
    await user.type(search, '  lOAN    Officer  ')
    expect(screen.getByText('1 of 2 Staff members')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Manage Loan Officer Demo' })).toBeVisible()
    await user.clear(search)
    await user.type(search, '  STAFF01@MERIDIAN.LOCAL  ')
    expect(screen.getByRole('button', { name: 'Manage Avery Approver' })).toBeVisible()
    await user.selectOptions(screen.getByLabelText('Assigned role'), 'APPROVER')
    await user.selectOptions(screen.getByLabelText('Status'), 'ACTIVE')
    expect(screen.getByText('0 of 2 Staff members')).toBeVisible()
    expect(screen.getByText('No Staff match these filters.')).toBeVisible()
    expect(screen.queryByText('No internal Staff Users are available.')).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Status'), 'SUSPENDED')
    expect(screen.getByText('1 of 2 Staff members')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Reset filters' }))
    expect(search).toHaveValue('')
    expect(screen.getByText('2 of 2 Staff members')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls).toHaveLength(calls)
    expect(router.state.location.search).toBe('')
    expect(JSON.stringify(queryClient.getQueryCache().getAll().map((query) => query.queryKey))).not.toMatch(/meridian.local|Officer|Avery/)
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toMatch(/meridian.local|Officer|Avery/)
    await user.selectOptions(screen.getByLabelText('Assigned role'), 'APPROVER')
    await act(async () => { queryClient.setQueryData(adminUserKeys.list(), records.map((record) => ({
      ...record, assignedRoleCodes: record.assignedRoleCodes.filter((code) => code !== 'APPROVER'),
    }))) })
    expect(await screen.findByText('0 of 2 Staff members')).toBeVisible()
    expect(screen.getByLabelText('Assigned role')).toHaveValue('APPROVER')
    await user.click(screen.getByRole('button', { name: 'Reset filters' }))
    expect(screen.getByText('2 of 2 Staff members')).toBeVisible()
    await act(async () => { await router.navigate('/admin') })
    await waitFor(() => expect(queryClient.getQueryData(adminUserKeys.list())).toBeUndefined())
  })

  it('pages the filtered complete list in server order and resets when filters or the authorized list change', async () => {
    let records = staffRecords(45)
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles') ? roles : records)
    renderPage()
    await screen.findByRole('table')
    const user = userEvent.setup()
    expect(screen.getByText('Page 1 of 3 · Showing 1–20 of 45')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(within(screen.getByRole('table')).getAllByRole('rowheader')[0]).toHaveTextContent('Staff 01')
    const reads = vi.mocked(api.apiRequest).mock.calls.length
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByText('Page 2 of 3 · Showing 21–40 of 45')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByText('Page 3 of 3 · Showing 41–45 of 45')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Previous' }))
    await user.type(screen.getByRole('searchbox'), 'Staff 4')
    expect(screen.getByText('Page 1 of 1 · Showing 1–6 of 6')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls).toHaveLength(reads)
    await user.click(screen.getByRole('button', { name: 'Reset filters' }))
    await user.click(screen.getByRole('button', { name: 'Next' }))
    records = records.map((record, index) => index === 0 ? { ...record, displayName: 'Refreshed Staff' } : record)
    await user.click(screen.getByRole('button', { name: 'Refresh Users' }))
    expect(await screen.findByRole('button', { name: 'Manage Refreshed Staff' })).toBeVisible()
    expect(screen.getByText('Page 1 of 3 · Showing 1–20 of 45')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    records = records.slice(0, 2)
    await user.click(screen.getByRole('button', { name: 'Refresh Users' }))
    expect(await screen.findByText('Page 1 of 1 · Showing 1–2 of 2')).toBeVisible()
  })

  it('uses the exact selected User ID after paging and returns focus to the directory if refresh removes its trigger', async () => {
    let records = staffRecords(21)
    const selectedId = records[20]!.userId
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'PUT') {
        records = records.map((record) => record.userId === selectedId ? { ...record, assignedRoleCodes: ['LOAN_OFFICER', 'APPROVER'] } : record)
        return records[20]
      }
      return records
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('table')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await user.click(screen.getByRole('button', { name: 'Edit Staff 21' }))
    await user.click(screen.getByRole('button', { name: 'Assign Approver' }))
    expect(await screen.findByRole('heading', { name: 'Staff access updated for Staff 21' })).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Remove Approver' })).toBeVisible()
    const put = vi.mocked(api.apiRequest).mock.calls.find(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')
    expect(put?.[0]).toBe(`/admin/internal-users/${selectedId}/roles/APPROVER`)
    await user.click(screen.getByRole('button', { name: 'Close Staff management' }))
    expect(await screen.findByRole('heading', { name: 'Staff directory' })).toHaveFocus()
    await user.type(screen.getByRole('searchbox'), 'Staff 02')
    await openManagement('Staff 02')
    expect(screen.getByRole('button', { name: 'Assign Approver' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Send password setup link' }))
    expect(await screen.findByRole('heading', { name: 'Password setup requested for Staff 02' })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.find(([, options]) => (options as RequestInit | undefined)?.method === 'POST')?.[0])
      .toBe(`/admin/internal-users/${records[1]!.userId}/password-setup`)
  })

  it('traps keyboard focus in one described dialog and restores name, action, and confirmation triggers', async () => {
    renderPage()
    const user = userEvent.setup()
    const trigger = await screen.findByRole('button', { name: 'Manage Loan Officer Demo' })
    trigger.focus()
    await user.keyboard('{Enter}')
    const dialog = screen.getByRole('dialog', { name: 'Manage Loan Officer Demo' })
    expect(dialog).toHaveAccessibleDescription('Manage password setup, Staff access status, and predefined roles.')
    expect(screen.getByRole('heading', { name: 'Manage Loan Officer Demo' })).toHaveFocus()
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
    const close = screen.getByRole('button', { name: 'Close Staff management' })
    close.focus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Send password setup link' })).toHaveFocus()
    await user.tab({ shift: true })
    expect(close).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(trigger).toHaveFocus())
    await user.click(screen.getByRole('button', { name: 'Edit Loan Officer Demo' }))
    await user.selectOptions(screen.getByLabelText('Target status'), 'DISABLED')
    await user.click(screen.getByRole('button', { name: 'Update access status' }))
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
    expect(screen.queryByRole('button', { name: 'Assign Approver' })).not.toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Update access status' })).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit Loan Officer Demo' })).toHaveFocus())
  })

  it('keeps confirmation and dismissal locked through the command and reconciliation, then focuses the result', async () => {
    let authoritative = internalUser
    let complete!: () => void
    let reconcile!: () => void
    let reads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'PUT') return new Promise((resolve) => {
        complete = () => { authoritative = { ...internalUser, status: 'DISABLED' }; resolve(authoritative) }
      })
      if (++reads > 1) return new Promise((resolve) => { reconcile = () => resolve([authoritative]) })
      return [authoritative]
    })
    renderPage()
    const user = userEvent.setup()
    await openManagement()
    expect(screen.getByRole('button', { name: 'Update access status' })).toBeDisabled()
    await user.selectOptions(screen.getByLabelText('Target status'), 'DISABLED')
    await user.click(screen.getByRole('button', { name: 'Update access status' }))
    await user.click(screen.getByRole('button', { name: 'Update access status' }))
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog', { name: 'Confirm User access change' })).toBeVisible()
    await act(async () => complete())
    await waitFor(() => expect(reads).toBe(2))
    await user.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Waiting for confirmation…' })).toBeDisabled()
    await act(async () => reconcile())
    expect(await screen.findByRole('heading', { name: 'Staff access updated for Loan Officer Demo' })).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Send password setup link' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Update access status' })).toBeDisabled()
  })

  it('requires explicit status selection for unknown state and rejects a stale confirmation', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles') ? roles : [{ ...internalUser, status: 'FUTURE' }])
    const { queryClient } = renderPage()
    await openManagement()
    const user = userEvent.setup()
    expect(screen.getByLabelText('Target status')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Update access status' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Send password setup link' })).toBeDisabled()
    await user.selectOptions(screen.getByLabelText('Target status'), 'SUSPENDED')
    await user.click(screen.getByRole('button', { name: 'Update access status' }))
    await act(async () => { queryClient.setQueryData(adminUserKeys.list(), [{ ...internalUser, status: 'SUSPENDED' }]) })
    expect(await screen.findByText(/Staff information changed/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Update access status' })).toBeDisabled()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByRole('button', { name: 'Update access status' })).toBeDisabled()
    expect(screen.getByRole('heading', { name: 'Manage Loan Officer Demo' })).toHaveFocus()
  })

  it('keeps unknown feedback through refresh failure and allows an explicit retry only after refresh succeeds', async () => {
    let failRead = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'PUT') { failRead = true; throw new NetworkError('lost') }
      if (failRead) throw new ApiError(400, 'UNEXPECTED_RESPONSE', 'failed', String(path), 'now')
      return [internalUser]
    })
    renderPage()
    const user = userEvent.setup()
    await openManagement()
    await user.click(screen.getByRole('button', { name: 'Assign Approver' }))
    expect(await screen.findByRole('heading', { name: 'Role assignment was not confirmed' })).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Assign Approver' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'I reviewed the latest information' })).not.toBeInTheDocument()
    failRead = false
    await user.click(screen.getByRole('button', { name: 'Refresh Staff information' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Assign Approver' })).toBeEnabled())
    expect(screen.getByRole('heading', { name: 'Role assignment was not confirmed' })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toHaveLength(1)
  })

  it('disables selected-user commands when authoritative discovery no longer returns that user', async () => {
    const { queryClient } = renderPage()
    await openManagement()
    await act(async () => { queryClient.setQueryData(adminUserKeys.list(), []) })
    expect(await screen.findByRole('dialog', { name: 'Staff member unavailable' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Send password setup link' })).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Close Staff management' }))
    expect(await screen.findByRole('heading', { name: 'Staff directory' })).toHaveFocus()
  })

  it('retains creation validation and reconciles an unknown create without double-creating or resending setup', async () => {
    let records = [internalUser]
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'POST') {
        records = [...records, { ...staffRecords(1)[0]!, displayName: 'Created Staff' }]
        throw new NetworkError('lost')
      }
      return records
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('table')
    await user.click(screen.getByRole('button', { name: 'Create Internal User' }))
    await user.type(screen.getByLabelText('Email'), 'created@meridian.local')
    await user.type(screen.getByLabelText('Display name'), 'Created Staff')
    await user.click(screen.getAllByRole('button', { name: 'Create Internal User' }).at(-1)!)
    expect(screen.getByText('Choose at least one predefined Staff role.')).toBeVisible()
    await user.click(screen.getByLabelText('Approver'))
    await user.click(screen.getAllByRole('button', { name: 'Create Internal User' }).at(-1)!)
    expect(await screen.findByText('Creation could not be confirmed. Refresh the Internal User list before trying again.')).toBeVisible()
    expect(await screen.findByRole('button', { name: 'Manage Created Staff' })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
  })

  it('uses the standard reset action for filters and pagination while keeping directory labeling visually hidden', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles') ? roles : staffRecords(21))
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('table', { name: 'Staff directory' })
    expect(screen.getByRole('heading', { name: 'Internal Users', level: 1 })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Staff directory' })).toHaveClass('sr-only')
    expect(screen.getByRole('region', { name: 'Staff directory' })).toBeInTheDocument()
    const reset = screen.getByRole('button', { name: 'Reset filters' })
    expect(reset).toBeDisabled()
    expect(reset).toHaveClass('border', 'border-border', 'bg-transparent')
    expect(reset.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(reset).toBeEnabled()
    expect(screen.getByText('Page 2 of 2 · Showing 21–21 of 21')).toBeVisible()
    await user.click(reset)
    expect(screen.getByText('Page 1 of 2 · Showing 1–20 of 21')).toBeVisible()
    expect(reset).toBeDisabled()
  })

  it('dismisses idle management from outside or Escape and restores each connected row invoker without requesting scroll', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/assignable-roles') ? roles : staffRecords(41))
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('table')
    for (const name of ['Edit Staff 01', 'Manage Staff 10', 'Edit Staff 20']) {
      const trigger = screen.getByRole('button', { name })
      const focus = vi.spyOn(trigger, 'focus')
      await user.click(trigger)
      expect(screen.getByRole('dialog')).toHaveAccessibleDescription('Manage password setup, Staff access status, and predefined roles.')
      await user.click(document.querySelector<HTMLElement>('[data-staff-management-backdrop]')!)
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(trigger).toHaveFocus()
      expect(focus).toHaveBeenLastCalledWith({ preventScroll: true })
      focus.mockRestore()
    }
    await user.click(screen.getByRole('button', { name: 'Next' }))
    const pagedTrigger = screen.getByRole('button', { name: 'Edit Staff 40' })
    await user.click(pagedTrigger)
    await user.keyboard('{Escape}')
    await waitFor(() => expect(pagedTrigger).toHaveFocus())
    expect(screen.getByText('Page 2 of 3 · Showing 21–40 of 41')).toBeVisible()
  })

  it('keeps backdrop dismissal blocked for confirmation and for an ordinary pending setup request', async () => {
    let finish!: () => void
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'POST') return new Promise<void>((resolve) => { finish = resolve })
      return [internalUser]
    })
    renderPage()
    const user = userEvent.setup()
    await openManagement()
    await user.selectOptions(screen.getByLabelText('Target status'), 'DISABLED')
    await user.click(screen.getByRole('button', { name: 'Update access status' }))
    await user.click(document.querySelector<HTMLElement>('[data-staff-management-backdrop]')!)
    expect(screen.getByRole('dialog', { name: 'Confirm User access change' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByRole('button', { name: 'Update access status' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Send password setup link' }))
    await user.click(document.querySelector<HTMLElement>('[data-staff-management-backdrop]')!)
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog', { name: 'Manage Loan Officer Demo' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Close Staff management' })).toBeDisabled()
    await act(async () => finish())
    expect(await screen.findByRole('heading', { name: 'Password setup requested for Loan Officer Demo' })).toHaveFocus()
    expect(screen.getByText(/request was accepted.*check their inbox and spam folder/)).toBeVisible()
    expect(screen.queryByText(/email was delivered/i)).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
  })

  it.each([
    [409, 'INTERNAL_USER_NOT_ACTIVE', /active Staff account with verified email.*Reactivation does not verify email/],
    [403, 'FORBIDDEN', /do not have access/],
    [404, 'INTERNAL_USER_NOT_FOUND', /not available in your current workspace/],
  ] as const)('presents a definitive setup rejection for HTTP %s without claiming delivery uncertainty', async (status, code, message) => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/assignable-roles')) return roles
      if ((options as RequestInit | undefined)?.method === 'POST') throw new ApiError(status, code, 'Private service detail', String(path), 'now')
      return [internalUser]
    })
    renderPage()
    await openManagement()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Send password setup link' }))
    expect(await screen.findByRole('heading', { name: 'Password setup request rejected' })).toHaveFocus()
    expect(screen.getByText(message)).toBeVisible()
    expect(screen.queryByText(/Private service detail|could not confirm|I reviewed the latest information/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Send replacement setup link' })).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
  })
})
