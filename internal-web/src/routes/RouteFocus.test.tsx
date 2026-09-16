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
    ['/admin', 'Back-Office Administration | Meridian'],
    ['/admin/products', 'Loan Product Administration | Meridian'],
    ['/staff/work/documents', 'Document review | Meridian'],
    ['/staff/work/corrections', 'Staff corrections | Meridian'],
    ['/staff/work/contracts', 'Contract and readiness queue | Meridian'],
    ['/staff/work/disbursements', 'Ready-disbursement queue | Meridian'],
    ['/staff/work/servicing', 'LoanAccount servicing queue | Meridian'],
    ['/staff/work/settlements', 'Settlement work queue | Meridian'],
    ['/staff/work/closures', 'Closure work queue | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/documents', 'Application documents | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/corrections', 'Application corrections | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/contract', 'Contract and readiness | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/disbursement', 'Disbursement and activation | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/loan-account', 'LoanAccount servicing | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/repayments/new', 'Record repayment | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/settlement', 'Administrative Full-Balance Settlement | Meridian'],
    ['/staff/applications/11111111-1111-4111-8111-111111111111/closure', 'Administrative closure | Meridian'],
  ])('publishes an accurate title for %s', async (path, title) => {
    render(<MemoryRouter initialEntries={[path]}><RouteFocus /><DeferredHeading /></MemoryRouter>)
    await waitFor(() => expect(document.title).toBe(title))
  })
})
