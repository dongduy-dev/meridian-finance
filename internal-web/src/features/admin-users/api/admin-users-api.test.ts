import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { createInternalUser, getInternalUsers, sendInternalUserPasswordSetup } from './admin-users-api'

describe('Internal User administration API', () => {
  it('accepts deterministic Meridian user IDs from the list response', async () => {
    const userId = '00000000-0000-0000-0000-000000000304'
    const protectedRequest = vi.fn().mockResolvedValue([{
      userId,
      email: 'accounting.officer@meridian.local',
      displayName: 'Accounting Officer Demo',
      status: 'ACTIVE',
      assignedRoleCodes: ['ACCOUNTING_OFFICER'],
    }])

    await expect(getInternalUsers({ protectedRequest } as unknown as AuthSessionManager))
      .resolves.toEqual([expect.objectContaining({ userId })])
    expect(protectedRequest).toHaveBeenCalledWith('/admin/internal-users')
  })

  it('posts only provisioning fields and sends setup recovery without a token', async () => {
    const userId = '00000000-0000-0000-0000-000000000304'
    const protectedRequest = vi.fn().mockResolvedValueOnce({
      userId, email: 'staff@meridian.local', displayName: 'Staff', status: 'ACTIVE', assignedRoleCodes: ['APPROVER'],
    }).mockResolvedValueOnce(undefined)
    const manager = { protectedRequest } as unknown as AuthSessionManager

    await createInternalUser(manager, { email: 'staff@meridian.local', displayName: 'Staff', roleCodes: ['APPROVER'] })
    await sendInternalUserPasswordSetup(manager, userId)

    expect(protectedRequest).toHaveBeenNthCalledWith(1, '/admin/internal-users', {
      method: 'POST', body: { email: 'staff@meridian.local', displayName: 'Staff', roleCodes: ['APPROVER'] },
    })
    expect(protectedRequest).toHaveBeenNthCalledWith(2,
      `/admin/internal-users/${userId}/password-setup`, { method: 'POST' })
  })
})
