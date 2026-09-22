export type StaffActor = {
  userId: string
  email: string
  roles: readonly string[]
  permissions: readonly string[]
}

export const STAFF_OPERATIONAL_PERMISSIONS = [
  'customer:intake:manage',
  'loan:originate:staff',
  'document:upload:intake',
  'document:upload:assisted',
  'document:upload:assisted-action',
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
  'loan:offer:respond:staff',
  'loan:contract:acknowledge:staff',
  'loan:disbursement:prepare',
  'loan:disburse',
  'repayment:update',
  'loan:settlement:approve',
  'loan:account:close',
] as const

export type StaffOperationalPermission = (typeof STAFF_OPERATIONAL_PERMISSIONS)[number]

export const BACK_OFFICE_ADMINISTRATION_PERMISSIONS = [
  'loan:product:manage',
  'partner:read',
  'partner:manage',
  'identity:user:manage',
  'admin:config',
] as const

export type BackOfficeAdministrationPermission = (typeof BACK_OFFICE_ADMINISTRATION_PERMISSIONS)[number]

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

export function hasBackOfficeAccess(actor: StaffActor): boolean {
  return hasAnyPermission(actor, BACK_OFFICE_ADMINISTRATION_PERMISSIONS)
}
