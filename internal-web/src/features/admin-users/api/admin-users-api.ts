import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  assignableInternalRoleSchema,
  internalUserSchema,
  type AssignableInternalRole,
  type InternalUser,
  type InternalUserStatus,
} from './contracts'

export async function getInternalUsers(manager: AuthSessionManager): Promise<InternalUser[]> {
  return internalUserSchema.array().parse(
    await manager.protectedRequest<unknown>('/admin/internal-users'),
  )
}

export async function getAssignableInternalRoles(
  manager: AuthSessionManager,
): Promise<AssignableInternalRole[]> {
  return assignableInternalRoleSchema.array().parse(
    await manager.protectedRequest<unknown>('/admin/internal-users/assignable-roles'),
  )
}

export async function createInternalUser(
  manager: AuthSessionManager,
  request: { email: string; displayName: string; roleCodes: string[] },
): Promise<InternalUser> {
  return internalUserSchema.parse(await manager.protectedRequest<unknown>(
    '/admin/internal-users', { method: 'POST', body: request },
  ))
}

export async function sendInternalUserPasswordSetup(manager: AuthSessionManager, userId: string): Promise<void> {
  await manager.protectedRequest<void>(
    `/admin/internal-users/${encodeURIComponent(userId)}/password-setup`, { method: 'POST' },
  )
}

export async function changeInternalUserStatus(
  manager: AuthSessionManager,
  userId: string,
  status: InternalUserStatus,
): Promise<InternalUser> {
  return internalUserSchema.parse(await manager.protectedRequest<unknown>(
    `/admin/internal-users/${encodeURIComponent(userId)}/status`,
    { method: 'PUT', body: { status } },
  ))
}

export async function changeInternalUserRole(
  manager: AuthSessionManager,
  userId: string,
  roleCode: string,
  assigned: boolean,
): Promise<InternalUser> {
  return internalUserSchema.parse(await manager.protectedRequest<unknown>(
    `/admin/internal-users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(roleCode)}`,
    { method: 'PUT', body: { assigned } },
  ))
}
