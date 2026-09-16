import {
  BACK_OFFICE_ADMINISTRATION_PERMISSIONS,
  hasAnyPermission,
  type BackOfficeAdministrationPermission,
  type StaffActor,
} from '@/features/auth/model/access-control'

export type AdminRouteDefinition = {
  path: `/admin${string}`
  label: string
  requiredPermissions: readonly BackOfficeAdministrationPermission[]
}

export const ADMIN_HOME_ROUTE = {
  path: '/admin',
  label: 'Administration',
  requiredPermissions: BACK_OFFICE_ADMINISTRATION_PERMISSIONS,
} as const satisfies AdminRouteDefinition

export const ADMIN_ROUTES = [ADMIN_HOME_ROUTE] as const satisfies readonly AdminRouteDefinition[]

export function canAccessAdminRoute(actor: StaffActor, route: AdminRouteDefinition): boolean {
  return hasAnyPermission(actor, route.requiredPermissions)
}

export function permittedAdminRoutes(actor: StaffActor): readonly AdminRouteDefinition[] {
  return ADMIN_ROUTES.filter((route) => canAccessAdminRoute(actor, route))
}
