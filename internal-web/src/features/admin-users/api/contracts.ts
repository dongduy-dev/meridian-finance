import { z } from 'zod'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

const code = z.string().trim().min(1)

export const internalUserSchema = z.object({
  userId: meridianUuidSchema,
  email: z.string().email(),
  displayName: z.string().trim().min(1),
  status: code,
  assignedRoleCodes: z.array(code),
})

export const assignableInternalRoleSchema = z.object({
  code,
  name: z.string().trim().min(1),
})

export const knownInternalUserStatuses = ['ACTIVE', 'SUSPENDED', 'DISABLED'] as const

export type InternalUser = z.infer<typeof internalUserSchema>
export type AssignableInternalRole = z.infer<typeof assignableInternalRoleSchema>
export type InternalUserStatus = typeof knownInternalUserStatuses[number]
