import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, NetworkError } from '@/lib/api'
import { clearAccessCredential } from '@/lib/auth/access-credential'

import type { AuthApi, AuthResponse } from './auth-api'
import { AuthSessionManager, CustomerSessionRequiredError } from './auth-session'

function customerResponse(overrides: Partial<AuthResponse> = {}): AuthResponse {
  return {
    tokenType: 'Bearer',
    accessToken: 'customer-access-token',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    userId: '11111111-1111-4111-8111-111111111111',
    email: 'customer@example.com',
    userType: 'CUSTOMER',
    customerId: '22222222-2222-4222-8222-222222222222',
    roles: ['CUSTOMER'],
    permissions: ['customer:read:own'],
    ...overrides,
  }
}

function apiMock(): AuthApi {
  return {
    register: vi.fn().mockResolvedValue(undefined),
    requestEmailVerification: vi.fn().mockResolvedValue(undefined),
    confirmEmailVerification: vi.fn().mockResolvedValue(undefined),
    requestPasswordReset: vi.fn().mockResolvedValue(undefined),
    confirmPasswordReset: vi.fn().mockResolvedValue(undefined),
    login: vi.fn().mockResolvedValue(customerResponse()),
    refresh: vi.fn().mockResolvedValue(customerResponse()),
    logout: vi.fn().mockResolvedValue(undefined),
  }
}

function apiError(status: number, errorCode: string) {
  return new ApiError({ status, errorCode, message: 'Safe message' })
}

beforeEach(() => clearAccessCredential())

describe('Customer auth session bootstrap', () => {
  it('authenticates a Customer from one bootstrap refresh', async () => {
    const api = apiMock()
    const manager = new AuthSessionManager(api, vi.fn())

    await Promise.all([manager.bootstrap(), manager.bootstrap()])

    expect(api.refresh).toHaveBeenCalledOnce()
    expect(manager.getSnapshot()).toMatchObject({
      status: 'authenticated',
      actor: { userType: 'CUSTOMER', email: 'customer@example.com' },
    })
  })

  it('treats INVALID_REFRESH_TOKEN as anonymous without a session-check error', async () => {
    const api = apiMock()
    vi.mocked(api.refresh).mockRejectedValue(apiError(401, 'INVALID_REFRESH_TOKEN'))
    const manager = new AuthSessionManager(api, vi.fn())

    await manager.bootstrap()

    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })

  it.each([
    ['a network failure', new NetworkError()],
    ['a server failure', apiError(503, 'SERVICE_TEMPORARILY_UNAVAILABLE')],
  ])('keeps %s retryable instead of declaring the session anonymous', async (_label, failure) => {
    const api = apiMock()
    vi.mocked(api.refresh).mockRejectedValue(failure)
    const manager = new AuthSessionManager(api, vi.fn())

    await manager.bootstrap()

    expect(manager.getSnapshot()).toMatchObject({ status: 'checking', error: {} })
  })

  it('preserves Retry-After and correlation for a rate-limited bootstrap check', async () => {
    const api = apiMock()
    vi.mocked(api.refresh).mockRejectedValue(
      new ApiError({
        status: 429,
        errorCode: 'RATE_LIMIT_EXCEEDED',
        message: 'Too many requests.',
        retryAfter: '17',
        requestId: '33333333-3333-4333-8333-333333333333',
      }),
    )
    const manager = new AuthSessionManager(api, vi.fn())

    await manager.bootstrap()

    expect(manager.getSnapshot()).toEqual({
      status: 'checking',
      error: {
        rateLimited: true,
        retryAfter: '17',
        requestId: '33333333-3333-4333-8333-333333333333',
      },
    })
  })

  it('rejects and best-effort revokes a Staff refresh result', async () => {
    const api = apiMock()
    vi.mocked(api.refresh).mockResolvedValue(
      customerResponse({ userType: 'STAFF', customerId: null, accessToken: 'staff-token' }),
    )
    const clearQueries = vi.fn()
    const manager = new AuthSessionManager(api, clearQueries)

    await manager.bootstrap()

    expect(api.logout).toHaveBeenCalledWith('staff-token')
    expect(clearQueries).toHaveBeenCalled()
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })
})

describe('Customer login and protected request recovery', () => {
  it('establishes a Customer session without routing public login errors through refresh', async () => {
    const api = apiMock()
    const manager = new AuthSessionManager(api, vi.fn())

    await manager.login({ email: 'customer@example.com', password: 'not-retained' })

    expect(manager.getSnapshot().status).toBe('authenticated')
    expect(api.refresh).not.toHaveBeenCalled()

    vi.mocked(api.login).mockRejectedValue(apiError(401, 'INVALID_CREDENTIALS'))
    await expect(
      manager.login({ email: 'customer@example.com', password: 'not-retained' }),
    ).rejects.toMatchObject({ errorCode: 'INVALID_CREDENTIALS' })
    expect(api.refresh).not.toHaveBeenCalled()
  })

  it('rejects a Staff login and keeps Customer Web anonymous', async () => {
    const api = apiMock()
    vi.mocked(api.login).mockResolvedValue(
      customerResponse({ userType: 'STAFF', customerId: null, accessToken: 'staff-token' }),
    )
    const manager = new AuthSessionManager(api, vi.fn())

    await expect(
      manager.login({ email: 'staff@example.com', password: 'not-retained' }),
    ).rejects.toBeInstanceOf(CustomerSessionRequiredError)
    expect(api.logout).toHaveBeenCalledWith('staff-token')
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })

  it('shares one refresh across concurrent 401s and replays each request once', async () => {
    const api = apiMock()
    const manager = new AuthSessionManager(api, vi.fn())
    await manager.login({ email: 'customer@example.com', password: 'not-retained' })

    let resolveRefresh!: (response: AuthResponse) => void
    vi.mocked(api.refresh).mockReturnValue(
      new Promise<AuthResponse>((resolve) => {
        resolveRefresh = resolve
      }),
    )
    const firstOperation = vi
      .fn<(token: string) => Promise<string>>()
      .mockRejectedValueOnce(apiError(401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce('first-replayed')
    const secondOperation = vi
      .fn<(token: string) => Promise<string>>()
      .mockRejectedValueOnce(apiError(401, 'AUTHENTICATION_REQUIRED'))
      .mockResolvedValueOnce('second-replayed')

    const first = manager.requestProtected(firstOperation)
    const second = manager.requestProtected(secondOperation)
    await vi.waitFor(() => expect(api.refresh).toHaveBeenCalledOnce())
    resolveRefresh(customerResponse({ accessToken: 'rotated-access-token' }))

    await expect(Promise.all([first, second])).resolves.toEqual([
      'first-replayed',
      'second-replayed',
    ])
    expect(firstOperation).toHaveBeenCalledTimes(2)
    expect(secondOperation).toHaveBeenCalledTimes(2)
    expect(firstOperation).toHaveBeenLastCalledWith('rotated-access-token')
    expect(secondOperation).toHaveBeenLastCalledWith('rotated-access-token')
  })

  it('stops after a second 401 and clears local private state', async () => {
    const api = apiMock()
    const clearQueries = vi.fn()
    const manager = new AuthSessionManager(api, clearQueries)
    await manager.login({ email: 'customer@example.com', password: 'not-retained' })
    const operation = vi.fn().mockRejectedValue(apiError(401, 'INVALID_TOKEN'))

    await expect(manager.requestProtected(operation)).rejects.toMatchObject({
      errorCode: 'INVALID_TOKEN',
    })

    expect(operation).toHaveBeenCalledTimes(2)
    expect(api.refresh).toHaveBeenCalledOnce()
    expect(clearQueries).toHaveBeenCalled()
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })

  it.each([
    [403, 'ACCESS_DENIED'],
    [404, 'NOT_FOUND'],
    [409, 'CONFLICT'],
    [422, 'BUSINESS_REJECTION'],
  ])('does not refresh a protected %i response', async (status, code) => {
    const api = apiMock()
    const manager = new AuthSessionManager(api, vi.fn())
    await manager.login({ email: 'customer@example.com', password: 'not-retained' })

    await expect(
      manager.requestProtected(() => Promise.reject(apiError(status, code))),
    ).rejects.toMatchObject({ status })
    expect(api.refresh).not.toHaveBeenCalled()
  })

  it.each([
    ['a network failure', new NetworkError()],
    ['an abort', new DOMException('Aborted', 'AbortError')],
  ])('does not refresh %s from a protected operation', async (_label, failure) => {
    const api = apiMock()
    const manager = new AuthSessionManager(api, vi.fn())
    await manager.login({ email: 'customer@example.com', password: 'not-retained' })

    await expect(
      manager.requestProtected(() => Promise.reject(failure)),
    ).rejects.toBe(failure)
    expect(api.refresh).not.toHaveBeenCalled()
  })

  it('refreshes before sending a knowingly expired access token', async () => {
    const api = apiMock()
    const manager = new AuthSessionManager(api, vi.fn())
    await manager.login({
      email: 'customer@example.com',
      password: 'not-retained',
    })
    vi.mocked(api.login).mockResolvedValue(customerResponse())
    clearAccessCredential()
    const operation = vi.fn().mockResolvedValue('ok')

    await expect(manager.requestProtected(operation)).resolves.toBe('ok')

    expect(api.refresh).toHaveBeenCalledOnce()
    expect(operation).toHaveBeenCalledWith('customer-access-token')
  })
})

describe('local session clearing', () => {
  it('clears auth and Query state even when backend logout fails', async () => {
    const api = apiMock()
    const clearQueries = vi.fn()
    const manager = new AuthSessionManager(api, clearQueries)
    await manager.login({ email: 'customer@example.com', password: 'not-retained' })
    vi.mocked(api.logout).mockRejectedValue(new NetworkError())

    await expect(manager.logout()).rejects.toBeInstanceOf(NetworkError)

    expect(clearQueries).toHaveBeenCalled()
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })

  it('clears auth and Query state after successful password reset confirmation', async () => {
    const api = apiMock()
    const clearQueries = vi.fn()
    const manager = new AuthSessionManager(api, clearQueries)
    await manager.login({ email: 'customer@example.com', password: 'not-retained' })

    await manager.confirmPasswordReset('reset-token', 'a-password-with-12-chars')

    expect(api.confirmPasswordReset).toHaveBeenCalledWith(
      'reset-token',
      'a-password-with-12-chars',
    )
    expect(clearQueries).toHaveBeenCalled()
    expect(manager.getSnapshot()).toEqual({ status: 'anonymous' })
  })
})
