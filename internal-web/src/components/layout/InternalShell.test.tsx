import { render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InternalShell } from './InternalShell'

const session = vi.hoisted(() => ({ permissions: [] as string[] }))
vi.mock('@/features/auth/model/auth-context', () => ({
  useAuth: () => ({
    manager: { logout: vi.fn() },
    state: {
      status: 'authenticated',
      actor: { userType: 'STAFF', userId: 'staff-1', email: 'staff@meridian.local', customerId: null, roles: ['APPROVER', 'ACCOUNTING_OFFICER'], permissions: session.permissions },
    },
  }),
}))

function renderShell(path: string, area: 'staff' | 'admin' = 'staff') {
  const router = createMemoryRouter([{
    element: <InternalShell area={area} />,
    children: [{ path: '*', element: <h1>Case workspace</h1> }],
  }], { initialEntries: [path] })
  render(<RouterProvider router={router} />)
}

describe('Internal Web navigation context', () => {
  beforeEach(() => {
    session.permissions = ['loan:read', 'loan:originate:staff', 'document:review', 'approval:decide', 'loan:contract:read', 'loan:disburse', 'repayment:update', 'loan:settlement:approve', 'loan:account:close', 'partner:read']
  })

  it.each([
    ['/staff/origination/case-1', 'Assisted origination'],
    ['/staff/applications/case-1/review', 'Applications'],
    ['/staff/applications/case-1/decision', 'Credit decisions'],
    ['/staff/applications/case-1/documents', 'Document review'],
    ['/staff/applications/case-1/contract', 'Contracts'],
    ['/staff/applications/case-1/disbursement', 'Disbursements'],
    ['/staff/applications/case-1/repayments/new', 'Account servicing'],
    ['/staff/applications/case-1/settlement', 'Settlements'],
    ['/staff/applications/case-1/closure', 'Closures'],
  ])('selects the work area for a direct visit to %s', (path, label) => {
    renderShell(path)
    const navigation = within(screen.getByRole('navigation', { name: 'Staff navigation' }))
    expect(navigation.getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page')
    expect(navigation.getAllByRole('link').filter(link => link.getAttribute('aria-current') === 'page')).toHaveLength(1)
    expect(within(screen.getByRole('navigation', { name: 'Internal areas' })).getByRole('link', { name: 'Staff Operations' })).toHaveAttribute('aria-current', 'page')
  })

  it('selects Applications when the actor cannot enter the specialist queue', () => {
    session.permissions = ['loan:read']
    renderShell('/staff/applications/case-1/documents')
    const navigation = within(screen.getByRole('navigation', { name: 'Staff navigation' }))
    expect(navigation.getByRole('link', { name: 'Applications' })).toHaveAttribute('aria-current', 'page')
    expect(navigation.queryByRole('link', { name: 'Document review' })).not.toBeInTheDocument()
  })

  it('selects Partners and the administrative area for a Partner detail link', () => {
    renderShell('/admin/partners/partner-1', 'admin')
    expect(within(screen.getByRole('navigation', { name: 'Administration navigation' })).getByRole('link', { name: 'Partners' })).toHaveAttribute('aria-current', 'page')
    expect(within(screen.getByRole('navigation', { name: 'Internal areas' })).getByRole('link', { name: 'Back-Office Administration' })).toHaveAttribute('aria-current', 'page')
  })
})
