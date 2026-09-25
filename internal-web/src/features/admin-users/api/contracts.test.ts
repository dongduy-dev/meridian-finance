import { describe, expect, it } from 'vitest'
import { assignableInternalRoleSchema, internalUserSchema } from './contracts'

describe('Internal User administration contracts', () => {
  it('accepts the narrow safe projections and strips unowned fields', () => {
    expect(internalUserSchema.parse({
      userId: '00000000-0000-0000-0000-000000000304',
      email: 'staff@meridian.local',
      displayName: 'Staff User',
      status: 'ACTIVE',
      assignedRoleCodes: ['LOAN_OFFICER'],
      passwordHash: 'must-not-survive',
      authorizationVersion: 3,
    })).toEqual({
      userId: '00000000-0000-0000-0000-000000000304',
      email: 'staff@meridian.local',
      displayName: 'Staff User',
      status: 'ACTIVE',
      assignedRoleCodes: ['LOAN_OFFICER'],
    })
    expect(assignableInternalRoleSchema.parse({ code: 'APPROVER', name: 'Approver' }))
      .toEqual({ code: 'APPROVER', name: 'Approver' })
  })

  it('accepts future nonempty status and role codes for safe presentation', () => {
    expect(internalUserSchema.parse({
      userId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      email: 'staff@meridian.local',
      displayName: 'Staff User',
      status: 'FUTURE_STATUS',
      assignedRoleCodes: ['FUTURE_ROLE'],
    }).status).toBe('FUTURE_STATUS')
  })

  it.each([
    'not-a-uuid',
    '123',
    '00000000-0000-0000',
  ])('rejects malformed user identifier %s', (userId) => {
    expect(internalUserSchema.safeParse({
      userId,
      email: 'staff@meridian.local',
      displayName: 'Staff User',
      status: 'ACTIVE',
      assignedRoleCodes: ['LOAN_OFFICER'],
    }).success).toBe(false)
  })
})
