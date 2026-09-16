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
  navigation?: boolean
}

export const ADMIN_HOME_ROUTE = {
  path: '/admin',
  label: 'Administration',
  requiredPermissions: BACK_OFFICE_ADMINISTRATION_PERMISSIONS,
} as const satisfies AdminRouteDefinition

export const ADMIN_PARTNERS_ROUTE = {
  path: '/admin/partners',
  label: 'Partners',
  requiredPermissions: ['partner:read'],
} as const satisfies AdminRouteDefinition

export const ADMIN_PARTNER_DETAIL_ROUTE = {
  path: '/admin/partners/:partnerCompanyId',
  label: 'Partner detail',
  requiredPermissions: ['partner:read'],
  navigation: false,
} as const satisfies AdminRouteDefinition

export const ADMIN_PRODUCTS_ROUTE = {
  path: '/admin/products',
  label: 'Loan Products',
  requiredPermissions: ['loan:product:manage'],
} as const satisfies AdminRouteDefinition

export const ADMIN_USERS_ROUTE = {
  path: '/admin/users',
  label: 'Internal Users',
  requiredPermissions: ['identity:user:manage'],
} as const satisfies AdminRouteDefinition

export const ADMIN_ROUTES = [
  ADMIN_HOME_ROUTE,
  ADMIN_PARTNERS_ROUTE,
  ADMIN_PARTNER_DETAIL_ROUTE,
  ADMIN_PRODUCTS_ROUTE,
  ADMIN_USERS_ROUTE,
] as const satisfies readonly AdminRouteDefinition[]

export function canAccessAdminRoute(actor: StaffActor, route: AdminRouteDefinition): boolean {
  return hasAnyPermission(actor, route.requiredPermissions)
}

export function permittedAdminRoutes(actor: StaffActor): readonly AdminRouteDefinition[] {
  return ADMIN_ROUTES.filter((route) => (route as AdminRouteDefinition).navigation !== false && canAccessAdminRoute(actor, route))
}
