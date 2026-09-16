import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getAssignableInternalRoles, getInternalUsers } from './admin-users-api'

export const adminUserKeys = {
  all: ['admin-users'] as const,
  list: () => [...adminUserKeys.all, 'list'] as const,
  assignableRoles: () => [...adminUserKeys.all, 'assignable-roles'] as const,
}

export const internalUsersQuery = (manager: AuthSessionManager, enabled: boolean) => queryOptions({
  queryKey: adminUserKeys.list(),
  queryFn: () => getInternalUsers(manager),
  enabled,
  staleTime: 0,
  gcTime: 0,
})

export const assignableInternalRolesQuery = (manager: AuthSessionManager, enabled: boolean) => queryOptions({
  queryKey: adminUserKeys.assignableRoles(),
  queryFn: () => getAssignableInternalRoles(manager),
  enabled,
  staleTime: 0,
  gcTime: 0,
})
