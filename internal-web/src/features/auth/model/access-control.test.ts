import { describe, expect, it } from 'vitest'
import { hasAllPermissions, hasAnyPermission, hasPermission, hasRole, hasStaffWebAccess, type StaffActor } from './access-control'

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
})
