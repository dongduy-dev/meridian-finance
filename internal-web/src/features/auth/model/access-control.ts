export type StaffActor = {
  userId: string
  email: string
  roles: readonly string[]
  permissions: readonly string[]
}

export const STAFF_OPERATIONAL_PERMISSIONS = [
  'loan:read',
  'loan:review',
  'approval:recommend',
  'approval:decide',
  'document:review',
  'document:waive',
  'document:upload:staff',
  'loan:correction:staff',
  'loan:contract:prepare',
  'loan:contract:read',
  'loan:disbursement:prepare',
  'loan:disburse',
  'repayment:update',
  'loan:settlement:approve',
  'loan:account:close',
] as const

export function hasRole(actor: StaffActor, role: string): boolean {
  return actor.roles.includes(role)
}

export function hasPermission(actor: StaffActor, permission: string): boolean {
  return actor.permissions.includes(permission)
}

export function hasAnyPermission(actor: StaffActor, permissions: readonly string[]): boolean {
  return permissions.some((permission) => hasPermission(actor, permission))
}

export function hasAllPermissions(actor: StaffActor, permissions: readonly string[]): boolean {
  return permissions.every((permission) => hasPermission(actor, permission))
}

export function hasStaffWebAccess(actor: StaffActor): boolean {
  return hasAnyPermission(actor, STAFF_OPERATIONAL_PERMISSIONS)
}
