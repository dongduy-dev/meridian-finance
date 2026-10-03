import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { customerContextFixture, applicationId } from './contracts.test'
import { staffServicingCustomerContextQuery } from './queries'

describe('Staff servicing Customer-context query', () => {
  it('uses only the application ID in its query key and calls the Staff-purpose read', async () => {
    const protectedRequest = vi.fn().mockResolvedValue(customerContextFixture())
    const manager = { protectedRequest } as unknown as AuthSessionManager
    const query = staffServicingCustomerContextQuery(manager, applicationId, true)
    expect(query.queryKey).toEqual(['staff-servicing', 'customer-context', applicationId])
    expect(query.enabled).toBe(true)
    expect(await query.queryFn!({} as never)).toEqual(customerContextFixture())
    expect(protectedRequest).toHaveBeenCalledWith(`/staff/loan-applications/${applicationId}/servicing-context`)
    expect(staffServicingCustomerContextQuery(manager, applicationId, false).enabled).toBe(false)
  })
  it('rejects prohibited Customer fields before entering query data', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({ ...customerContextFixture(),
      customer: { ...customerContextFixture().customer, identityReference: 'RESTRICTED' } })
    const query = staffServicingCustomerContextQuery({ protectedRequest } as unknown as AuthSessionManager, applicationId, true)
    await expect(query.queryFn!({} as never)).rejects.toThrow()
  })
})
