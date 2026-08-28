import { z } from 'zod'

import { apiClient, type ApiClient } from '@/lib/api'

const authResponseSchema = z.object({
  tokenType: z.string().min(1),
  accessToken: z.string().min(1),
  expiresAt: z.string().min(1),
  userId: z.string().uuid(),
  email: z.string().email(),
  userType: z.string().min(1),
  customerId: z.string().uuid().nullable(),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
})

const registrationResponseSchema = z.object({
  emailVerificationRequired: z.literal(true),
})

export type AuthResponse = z.infer<typeof authResponseSchema>

export interface LoginInput {
  email: string
  password: string
}

export interface RegistrationInput extends LoginInput {
  displayName: string
}

export interface AuthApi {
  register(input: RegistrationInput): Promise<void>
  requestEmailVerification(email: string): Promise<void>
  confirmEmailVerification(token: string): Promise<void>
  requestPasswordReset(email: string): Promise<void>
  confirmPasswordReset(token: string, newPassword: string): Promise<void>
  login(input: LoginInput): Promise<AuthResponse>
  refresh(): Promise<AuthResponse>
  logout(accessToken?: string): Promise<void>
}

export function createAuthApi(client: ApiClient = apiClient): AuthApi {
  return {
    async register(input) {
      const response = await client.request('/auth/register', {
        method: 'POST',
        credentials: 'omit',
        json: input,
      })
      registrationResponseSchema.parse(response)
    },
    async requestEmailVerification(email) {
      await client.request('/auth/email-verification/request', {
        method: 'POST',
        credentials: 'omit',
        json: { email },
      })
    },
    async confirmEmailVerification(token) {
      await client.request('/auth/email-verification/confirm', {
        method: 'POST',
        credentials: 'omit',
        json: { token },
      })
    },
    async requestPasswordReset(email) {
      await client.request('/auth/password-reset/request', {
        method: 'POST',
        credentials: 'omit',
        json: { email },
      })
    },
    async confirmPasswordReset(token, newPassword) {
      await client.request('/auth/password-reset/confirm', {
        method: 'POST',
        credentials: 'include',
        json: { token, newPassword },
      })
    },
    async login(input) {
      const response = await client.request('/auth/login', {
        method: 'POST',
        credentials: 'include',
        json: input,
      })
      return authResponseSchema.parse(response)
    },
    async refresh() {
      const response = await client.request('/auth/refresh', {
        method: 'POST',
        credentials: 'include',
      })
      return authResponseSchema.parse(response)
    },
    async logout(accessToken) {
      await client.request('/auth/logout', {
        method: 'POST',
        credentials: 'include',
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
      })
    },
  }
}

export const authApi = createAuthApi()
