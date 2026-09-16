import { describe, expect, it } from 'vitest'
import type { StaffActor } from '@/features/auth/model/access-control'
import { preferredInternalDestination, resolvePostLoginDestination } from './internal-destination'

const actor = (permissions: readonly string[]): StaffActor => ({
  userId: 'staff-1',
  email: 'staff@meridian.local',
  roles: [],
  permissions,
})

describe('Internal Web destination resolution', () => {
  it('preserves Staff as the default when Staff and administration capabilities coexist', () => {
    expect(preferredInternalDestination(actor(['loan:read', 'partner:read']))).toBe('/staff')
  })

  it('uses administration as the admin-only default and keeps neither-capability actors safe', () => {
    expect(preferredInternalDestination(actor(['partner:read']))).toBe('/admin')
    expect(preferredInternalDestination(actor([]))).toBe('/staff')
  })

  it('preserves only a requested Internal Web area the actor can enter', () => {
    expect(resolvePostLoginDestination(actor(['partner:read']), '/admin/users?status=ACTIVE')).toBe('/admin/users?status=ACTIVE')
    expect(resolvePostLoginDestination(actor(['loan:read']), '/admin')).toBe('/staff')
    expect(resolvePostLoginDestination(actor(['partner:read']), '/staff/applications')).toBe('/admin')
  })

  it.each([
    'https://example.test/admin',
    '//example.test/admin',
    '/administrator',
    '/not-an-internal-area',
  ])('rejects unsafe or unrelated requested destination %s', (requested) => {
    expect(resolvePostLoginDestination(actor(['partner:read']), requested)).toBe('/admin')
  })
})
