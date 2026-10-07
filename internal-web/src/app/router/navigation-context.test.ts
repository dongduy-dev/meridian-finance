import { describe, expect, it } from 'vitest'
import { navigationContextPath } from './navigation-context'

describe('deep-linked navigation context', () => {
  it.each([
    ['documents', 'documents'], ['corrections', 'corrections'], ['decision', 'approvals'],
    ['contract', 'contracts'], ['disbursement', 'disbursements'], ['loan-account', 'servicing'],
    ['repayments/new', 'servicing'], ['settlement', 'settlements'], ['closure', 'closures'],
  ])('keeps %s in its operational queue', (workspace, queue) => {
    expect(navigationContextPath(`/staff/applications/case-id/${workspace}`)).toBe(`/staff/work/${queue}`)
  })
  it('preserves ordinary and unsupported destinations without inferring a workspace', () => {
    for (const path of ['/admin/partners/company-id', '/staff/origination/case-id', '/staff/applications/case-id/unsupported']) {
      expect(navigationContextPath(path)).toBe(path)
    }
  })
})
