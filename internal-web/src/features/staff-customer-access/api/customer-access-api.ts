import { z } from 'zod'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { searchCustomer } from '@/features/staff-origination/api/staff-origination-api'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

export { searchCustomer }

export const digitalAccessSchema = z.object({
  customerId: meridianUuidSchema,
  enabled: z.boolean(),
  email: z.string().nullable(),
  emailVerified: z.boolean(),
}).strict()

export type DigitalAccess = z.infer<typeof digitalAccessSchema>

export async function getDigitalAccess(manager: AuthSessionManager, customerId: string): Promise<DigitalAccess> {
  return digitalAccessSchema.parse(await manager.protectedRequest(`/staff/customers/${customerId}/digital-access`))
}

export async function enableDigitalAccess(manager: AuthSessionManager, customerId: string,
  input: { email: string; identityReference: string }): Promise<DigitalAccess> {
  return digitalAccessSchema.parse(await manager.protectedRequest(`/staff/customers/${customerId}/digital-access`, {
    method: 'POST', body: input,
  }))
}
