import { QueryClient } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError } from '@/lib/api'
import type { AuthResponse } from '../api/auth-api'
import * as authApi from '../api/auth-api'
import { getAccessToken } from './access-credential'
import { AuthSessionManager, InternalAccessRequiredError } from './auth-session'

vi.mock('../api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('../api/auth-api')>('../api/auth-api')
  return { ...actual, login: vi.fn(), refresh: vi.fn(), logout: vi.fn() }
})

const staff = (overrides: Partial<AuthResponse> = {}): AuthResponse => ({ tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-01T01:00:00Z', userId: '11111111-1111-4111-8111-111111111111', email: 'staff@meridian.local', userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['loan:read'], ...overrides })
const customer = (): AuthResponse => staff({ userType: 'CUSTOMER', customerId: '22222222-2222-4222-8222-222222222222', accessToken: 'customer-token' })

describe('staff session manager', () => {
  let queryClient: QueryClient
  let manager: AuthSessionManager

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    queryClient = new QueryClient()
    manager = new AuthSessionManager(queryClient)
  })

  afterEach(() => vi.unstubAllGlobals())

  it('accepts a staff login and keeps its credential only in memory', async () => {
    vi.mocked(authApi.login).mockResolvedValue(staff())
    await expect(manager.login('staff@meridian.local', 'secret')).resolves.toMatchObject({ email: 'staff@meridian.local' })
    expect(manager.getSnapshot().status).toBe('authenticated')
    expect(getAccessToken()).toBe('staff-token')
    expect(localStorage).toHaveLength(0)
  })

  it.each(['login', 'refresh'] as const)('rejects a Customer response during %s and revokes it best-effort', async (method) => {
    vi.mocked(authApi[method]).mockResolvedValue(customer())
    const action = method === 'login' ? manager.login('customer@meridian.local', 'secret') : manager.refresh()
    await expect(action).rejects.toBeInstanceOf(InternalAccessRequiredError)
    expect(authApi.logout).toHaveBeenCalledWith('customer-token')
    expect(manager.getSnapshot()).toMatchObject({ status: 'anonymous', reason: 'INTERNAL_ACCESS_REQUIRED' })
    expect(getAccessToken()).toBeUndefined()
  })

  it('coalesces concurrent refresh requests', async () => {
    let resolve!: (value: AuthResponse) => void
    vi.mocked(authApi.refresh).mockReturnValue(new Promise((done) => { resolve = done }))
    const one = manager.refresh(); const two = manager.refresh()
    expect(authApi.refresh).toHaveBeenCalledTimes(1)
    resolve(staff())
    await expect(Promise.all([one, two])).resolves.toHaveLength(2)
  })

  it('restores a valid Staff session through refresh', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    await manager.restore()
    expect(manager.getSnapshot()).toMatchObject({ status: 'authenticated', actor: { email: 'staff@meridian.local' } })
  })

  it('distinguishes definitive anonymous refresh from a transient restore failure', async () => {
    vi.mocked(authApi.refresh).mockRejectedValueOnce(new ApiError(401, 'INVALID_REFRESH_TOKEN', 'required', '/auth/refresh', 'now'))
    await manager.restore()
    expect(manager.getSnapshot()).toMatchObject({ status: 'anonymous', reason: 'SESSION_EXPIRED' })
    vi.mocked(authApi.refresh).mockRejectedValueOnce(new NetworkError())
    await manager.restore()
    expect(manager.getSnapshot()).toMatchObject({ status: 'checking', error: 'Session verification is temporarily unavailable.' })
  })

  it('preserves private query data when the actor and effective authority are unchanged', async () => {
    vi.mocked(authApi.login)
      .mockResolvedValueOnce(staff({ roles: ['LOAN_OFFICER', 'REVIEWER'], permissions: ['loan:read', 'document:review'] }))
      .mockResolvedValueOnce(staff({ roles: ['REVIEWER', 'LOAN_OFFICER'], permissions: ['document:review', 'loan:read'] }))
    await manager.login('one@meridian.local', 'secret')
    queryClient.setQueryData(['private'], { secret: true })
    await manager.login('one@meridian.local', 'secret')
    expect(queryClient.getQueryData(['private'])).toEqual({ secret: true })
  })

  it('clears private query data when permissions change for the same user', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce(staff()).mockResolvedValueOnce(staff({ permissions: ['document:review'] }))
    await manager.login('one@meridian.local', 'secret')
    queryClient.setQueryData(['private'], { secret: true })
    await manager.login('one@meridian.local', 'secret')
    expect(queryClient.getQueryData(['private'])).toBeUndefined()
  })

  it('clears private query data when roles change for the same user', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce(staff()).mockResolvedValueOnce(staff({ roles: ['APPROVER'] }))
    await manager.login('one@meridian.local', 'secret')
    queryClient.setQueryData(['private'], { secret: true })
    await manager.login('one@meridian.local', 'secret')
    expect(queryClient.getQueryData(['private'])).toBeUndefined()
  })

  it('clears private query data when the user changes', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce(staff()).mockResolvedValueOnce(staff({ userId: '33333333-3333-4333-8333-333333333333' }))
    await manager.login('one@meridian.local', 'secret')
    queryClient.setQueryData(['private'], { secret: true })
    await manager.login('two@meridian.local', 'secret')
    expect(queryClient.getQueryData(['private'])).toBeUndefined()
  })

  it('clears private query data on logout', async () => {
    vi.mocked(authApi.login).mockResolvedValue(staff())
    await manager.login('one@meridian.local', 'secret')
    queryClient.setQueryData(['private'], { secret: true })
    await manager.logout()
    expect(queryClient.getQueryData(['private'])).toBeUndefined()
    expect(manager.getSnapshot().status).toBe('anonymous')
  })

  it('refreshes and replays a protected request exactly once after token expiry', async () => {
    vi.mocked(authApi.login).mockResolvedValue(staff())
    vi.mocked(authApi.refresh).mockResolvedValue(staff({ accessToken: 'rotated-token' }))
    await manager.login('staff@meridian.local', 'secret')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ timestamp: 'now', status: 401, errorCode: 'TOKEN_EXPIRED', message: 'expired', path: '/loans' }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ value: 'ok' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(manager.protectedRequest<{ value: string }>('/loans')).resolves.toEqual({ value: 'ok' })
    expect(authApi.refresh).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(new Headers(fetchMock.mock.calls[1]?.[1].headers).get('Authorization')).toBe('Bearer rotated-token')
  })

  it('does not enter an infinite replay loop on a second session rejection', async () => {
    vi.mocked(authApi.login).mockResolvedValue(staff())
    vi.mocked(authApi.refresh).mockResolvedValue(staff({ accessToken: 'rotated-token' }))
    await manager.login('staff@meridian.local', 'secret')
    const expired = () => new Response(JSON.stringify({ timestamp: 'now', status: 401, errorCode: 'TOKEN_EXPIRED', message: 'expired', path: '/loans' }), { status: 401 })
    const fetchMock = vi.fn().mockImplementation(expired)
    vi.stubGlobal('fetch', fetchMock)
    await expect(manager.protectedRequest('/loans')).rejects.toMatchObject({ errorCode: 'TOKEN_EXPIRED' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(manager.getSnapshot().status).toBe('anonymous')
  })
})
