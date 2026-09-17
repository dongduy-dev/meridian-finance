import { describe, expect, it } from 'vitest'
import {
  BACK_OFFICE_ADMINISTRATION_PERMISSIONS,
  hasAllPermissions,
  hasAnyPermission,
  hasBackOfficeAccess,
  hasPermission,
  hasRole,
  hasStaffWebAccess,
  type StaffActor,
} from './access-control'

const actor: StaffActor = {
  userId: 'staff-1', email: 'officer@meridian.local', roles: ['LOAN_OFFICER', 'DOCUMENT_REVIEWER'], permissions: ['loan:read', 'document:review'],
}

describe('staff access control', () => {
  it('supports multi-role and exact permission checks', () => {
    expect(hasRole(actor, 'DOCUMENT_REVIEWER')).toBe(true)
    expect(hasPermission(actor, 'loan:read')).toBe(true)
    expect(hasPermission(actor, 'loan:*')).toBe(false)
    expect(hasAnyPermission(actor, ['approval:decide', 'document:review'])).toBe(true)
    expect(hasAllPermissions(actor, ['loan:read', 'document:review'])).toBe(true)
  })

  it('excludes administrative-only permissions from operational access', () => {
    expect(hasStaffWebAccess({ ...actor, permissions: ['identity:user:manage', 'partner:manage'] })).toBe(false)
    expect(hasStaffWebAccess(actor)).toBe(true)
  })

  it.each(BACK_OFFICE_ADMINISTRATION_PERMISSIONS)('grants Back-Office access through exact %s', (permission) => {
    expect(hasBackOfficeAccess({ ...actor, permissions: [permission] })).toBe(true)
  })

  it.each([
    'loan:product',
    'loan:product:*',
    'partner',
    'partner:*',
    'identity:user',
    'identity:user:*',
    'admin:*',
    '*',
  ])('does not grant Back-Office access through %s', (permission) => {
    expect(hasBackOfficeAccess({ ...actor, permissions: [permission] })).toBe(false)
  })

  it.each(['audit:read', 'document:upload:staff'])('does not treat %s as Back-Office membership', (permission) => {
    expect(hasBackOfficeAccess({ ...actor, permissions: [permission] })).toBe(false)
  })

  it('keeps administration capabilities outside Staff operational access', () => {
    expect(hasStaffWebAccess({ ...actor, permissions: [...BACK_OFFICE_ADMINISTRATION_PERMISSIONS] })).toBe(false)
  })

  it.each(['customer:intake:manage', 'loan:originate:staff', 'document:upload:intake'])(
    'recognizes assisted-origination capability %s as Staff operational access',
    (permission) => expect(hasStaffWebAccess({ ...actor, permissions: [permission] })).toBe(true),
  )
})
