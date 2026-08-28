import { vi } from 'vitest'

import type { AuthApi, AuthResponse } from '@/features/auth/auth-api'
import { AuthSessionManager } from '@/features/auth/auth-session'

export function customerAuthResponse(overrides: Partial<AuthResponse> = {}): AuthResponse {
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
export function createAuthApiMock(): AuthApi {
  return {
    register: vi.fn().mockResolvedValue(undefined),
    requestEmailVerification: vi.fn().mockResolvedValue(undefined),
    confirmEmailVerification: vi.fn().mockResolvedValue(undefined),
    requestPasswordReset: vi.fn().mockResolvedValue(undefined),
    confirmPasswordReset: vi.fn().mockResolvedValue(undefined),
    login: vi.fn().mockResolvedValue(customerAuthResponse()),
    refresh: vi.fn().mockResolvedValue(customerAuthResponse()),
    logout: vi.fn().mockResolvedValue(undefined),
  }
}

export function createTestAuthManager(api = createAuthApiMock()) {
  return new AuthSessionManager(api, vi.fn())
}
