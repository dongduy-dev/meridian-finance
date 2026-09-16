import {
  hasBackOfficeAccess,
  hasStaffWebAccess,
  type StaffActor,
} from '@/features/auth/model/access-control'

export const STAFF_HOME_PATH = '/staff' as const
export const ADMIN_HOME_PATH = '/admin' as const

function belongsToArea(destination: string, areaRoot: typeof STAFF_HOME_PATH | typeof ADMIN_HOME_PATH): boolean {
  const queryStart = destination.indexOf('?')
  const pathname = queryStart === -1 ? destination : destination.slice(0, queryStart)
  return pathname === areaRoot || pathname.startsWith(`${areaRoot}/`)
}

export function preferredInternalDestination(actor: StaffActor): typeof STAFF_HOME_PATH | typeof ADMIN_HOME_PATH {
  if (hasStaffWebAccess(actor)) return STAFF_HOME_PATH
  if (hasBackOfficeAccess(actor)) return ADMIN_HOME_PATH
  return STAFF_HOME_PATH
}

export function resolvePostLoginDestination(actor: StaffActor, requested?: unknown): string {
  if (typeof requested === 'string') {
    if (belongsToArea(requested, STAFF_HOME_PATH) && hasStaffWebAccess(actor)) return requested
    if (belongsToArea(requested, ADMIN_HOME_PATH) && hasBackOfficeAccess(actor)) return requested
  }
  return preferredInternalDestination(actor)
}
