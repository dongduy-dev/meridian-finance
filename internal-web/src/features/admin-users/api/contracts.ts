import { z } from 'zod'

const code = z.string().trim().min(1)

export const internalUserSchema = z.object({
  userId: z.string().uuid(),
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
