import { describe, expect, it, vi } from 'vitest'

import type { ApiClient } from '@/lib/api'

import { createAuthApi } from './auth-api'

function validResponse() {
  return {
    tokenType: 'Bearer',
    accessToken: 'access-token',
    expiresAt: '2026-08-28T15:00:00Z',
    userId: '11111111-1111-4111-8111-111111111111',
    email: 'customer@example.com',
    userType: 'CUSTOMER',
    customerId: '22222222-2222-4222-8222-222222222222',
    roles: ['CUSTOMER'],
    permissions: ['customer:read:own'],
  }
}

describe('auth API boundary', () => {
  it('uses credentialed requests only for login, refresh, logout, and reset confirmation', async () => {
    const request = vi.fn().mockResolvedValue(validResponse())
    const api = createAuthApi({ request: request as ApiClient['request'] })

    await api.login({ email: 'customer@example.com', password: 'not-retained' })
    await api.refresh()
    await api.logout('access-token')
    request.mockResolvedValueOnce(undefined)
    await api.confirmPasswordReset('reset-token', 'a-password-with-12-chars')
    request.mockResolvedValueOnce({ emailVerificationRequired: true })
    await api.register({
      displayName: 'Customer',
      email: 'customer@example.com',
      password: 'a-password-with-12-chars',
    })

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/auth/login',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/auth/refresh',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/auth/logout',
      expect.objectContaining({
        credentials: 'include',
        headers: { Authorization: 'Bearer access-token' },
      }),
    )
    expect(request).toHaveBeenNthCalledWith(
      4,
      '/auth/password-reset/confirm',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(request).toHaveBeenNthCalledWith(
      5,
      '/auth/register',
      expect.objectContaining({ credentials: 'omit' }),
    )
  })
})
