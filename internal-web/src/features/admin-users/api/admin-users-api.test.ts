import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getInternalUsers } from './admin-users-api'

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
})
