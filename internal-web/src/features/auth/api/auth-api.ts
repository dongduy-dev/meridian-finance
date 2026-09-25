import { z } from 'zod'
import { apiRequest } from '@/lib/api'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

const authResponseSchema = z.object({
  tokenType: z.literal('Bearer'),
  accessToken: z.string().min(1),
  expiresAt: z.string(),
  userId: meridianUuidSchema,
  email: z.string().email(),
  userType: z.enum(['CUSTOMER', 'STAFF']),
  customerId: meridianUuidSchema.nullable(),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
})

export type AuthResponse = z.infer<typeof authResponseSchema>

function parseAuthResponse(payload: unknown): AuthResponse {
  const parsed = authResponseSchema.safeParse(payload)
  if (!parsed.success) throw new Error('The authentication service returned an invalid session.')
  return parsed.data
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  return parseAuthResponse(await apiRequest('/auth/login', {
    method: 'POST',
    body: { email, password, expectedUserType: 'STAFF' },
  }))
}

export async function refresh(): Promise<AuthResponse> {
  return parseAuthResponse(await apiRequest('/auth/refresh', { method: 'POST' }))
}

export async function logout(token?: string): Promise<void> {
  await apiRequest('/auth/logout', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  })
}
