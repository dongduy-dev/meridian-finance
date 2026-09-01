import { afterEach, describe, expect, it, vi } from 'vitest'
import { login, logout, refresh } from './auth-api'

const staffResponse = { tokenType: 'Bearer', accessToken: 'token', expiresAt: '2026-09-01T01:00:00Z', userId: '11111111-1111-1111-1111-111111111111', email: 'staff@meridian.local', userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['loan:read'] }

afterEach(() => vi.unstubAllGlobals())

describe('auth API', () => {
  it('calls only login, refresh, and logout with cookie credentials', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(staffResponse), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(staffResponse), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    await login('staff@meridian.local', 'secret')
    await refresh()
    await logout('token')
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout'])
    expect(fetchMock.mock.calls.every(([, options]) => options.credentials === 'include')).toBe(true)
    expect(new Headers(fetchMock.mock.calls[2]?.[1].headers).get('Authorization')).toBe('Bearer token')
  })

  it('fails closed on a malformed authentication response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ accessToken: 'token' }), { status: 200 })))
    await expect(refresh()).rejects.toThrow('invalid session')
  })
})
