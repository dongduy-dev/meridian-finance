import {
  STAFF_OPERATIONAL_PERMISSIONS,
  hasAnyPermission,
  type StaffActor,
  type StaffOperationalPermission,
} from '@/features/auth/model/access-control'

export type StaffRouteDefinition = {
  path: `/staff${string}`
  label: string
  requiredPermissions: readonly StaffOperationalPermission[]
}

export const STAFF_HOME_ROUTE = {
  path: '/staff',
  label: 'Internal operations',
  requiredPermissions: STAFF_OPERATIONAL_PERMISSIONS,
} as const satisfies StaffRouteDefinition

export const STAFF_APPLICATIONS_ROUTE = {
  path: '/staff/applications',
  label: 'Applications',
  requiredPermissions: ['loan:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_APPLICATION_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId',
  label: 'Application case',
  requiredPermissions: ['loan:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_DOCUMENT_QUEUE_ROUTE = {
  path: '/staff/work/documents',
  label: 'Document review',
  requiredPermissions: ['document:review'],
} as const satisfies StaffRouteDefinition

export const STAFF_CORRECTION_QUEUE_ROUTE = {
  path: '/staff/work/corrections',
  label: 'Corrections',
  requiredPermissions: ['loan:correction:staff'],
} as const satisfies StaffRouteDefinition

export const STAFF_DOCUMENT_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/documents',
  label: 'Application documents',
  requiredPermissions: ['document:review'],
} as const satisfies StaffRouteDefinition

export const STAFF_CORRECTION_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/corrections',
  label: 'Application corrections',
  requiredPermissions: ['loan:correction:staff'],
} as const satisfies StaffRouteDefinition

export const STAFF_ROUTES = [
  STAFF_HOME_ROUTE,
  STAFF_APPLICATIONS_ROUTE,
  STAFF_DOCUMENT_QUEUE_ROUTE,
  STAFF_CORRECTION_QUEUE_ROUTE,
] as const satisfies readonly StaffRouteDefinition[]

export function canAccessStaffRoute(actor: StaffActor, route: StaffRouteDefinition): boolean {
  return hasAnyPermission(actor, route.requiredPermissions)
}

export function permittedStaffRoutes(actor: StaffActor): readonly StaffRouteDefinition[] {
  return STAFF_ROUTES.filter((route) => canAccessStaffRoute(actor, route))
}
