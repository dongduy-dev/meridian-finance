import { useEffect, useState } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { RouteFocus } from './RouteFocus'

function DeferredHeading() {
  const [ready, setReady] = useState(false)
  useEffect(() => { queueMicrotask(() => setReady(true)) }, [])
  return ready ? <h1 data-route-heading tabIndex={-1}>Deferred workspace</h1> : null
}

describe('route focus', () => {
  it('focuses a route heading that mounts after the route frame', async () => {
    render(<MemoryRouter initialEntries={['/staff']}><RouteFocus /><DeferredHeading /></MemoryRouter>)
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Deferred workspace' })).toHaveFocus())
    expect(document.title).toBe('Internal operations | Meridian')
  })

  it.each([
    ['/login', 'Staff sign in | Meridian'],
    ['/admin', 'Back-Office Administration | Meridian'],
    ['/admin/partners', 'Partners | Meridian'],
    ['/admin/partners/22222222-2222-4222-8222-222222222222', 'Partner detail | Meridian'],
    ['/admin/partner-eligibility-reviews', 'Eligibility reviews | Meridian'],
    ['/admin/users', 'Internal User Administration | Meridian'],
    ['/admin/products', 'Loan Product Administration | Meridian'],
    ['/staff', 'Internal operations | Meridian'],
    ['/staff/applications', 'Applications | Meridian'],
    ['/staff/origination', 'Assisted origination | Meridian'],
    ['/staff/origination/33333333-3333-4333-8333-333333333333', 'Assisted origination case | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111', 'Application case | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/offer-response', 'Customer offer response | Meridian'],
    ['/staff/work/documents', 'Document review | Meridian'],
    ['/staff/work/corrections', 'Staff corrections | Meridian'],
    ['/staff/work/approvals', 'Approval decisions | Meridian'],
    ['/staff/work/contracts', 'Contract and readiness queue | Meridian'],
    ['/staff/work/disbursements', 'Ready-disbursement queue | Meridian'],
    ['/staff/work/servicing', 'Loan account servicing queue | Meridian'],
    ['/staff/work/settlements', 'Settlement work queue | Meridian'],
    ['/staff/work/closures', 'Closure work queue | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/documents', 'Application documents | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/corrections', 'Application corrections | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/verification', 'Product assessment | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/review', 'Loan Officer review | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/decision', 'Independent decision | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/contract', 'Contract and readiness | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/disbursement', 'Disbursement and activation | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/loan-account', 'Loan account servicing | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/repayments/new', 'Record repayment | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/settlement', 'Administrative Full-Balance Settlement | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/closure', 'Administrative closure | Meridian'],
    ['/not-an-internal-route', 'Page not found | Meridian'],
  ])('publishes an accurate title for %s', async (path, title) => {
    render(<MemoryRouter initialEntries={[path]}><RouteFocus /><DeferredHeading /></MemoryRouter>)
    await waitFor(() => expect(document.title).toBe(title))
  })
})
