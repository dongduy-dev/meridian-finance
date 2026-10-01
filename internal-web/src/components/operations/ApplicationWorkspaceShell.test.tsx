import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { applicationWorkspaceCase, expectCanonicalApplicationHeader } from '@/test/application-workspace-fixture'
import { ApplicationWorkspaceShell, applicationWorkspaceCaseFacts, type ApplicationWorkspaceSection } from './ApplicationWorkspaceShell'

const applicationId = '11111111-1111-4111-8111-111111111111'
const actor = { userId: applicationId, email: 'staff@meridian.local', roles: ['LOAN_OFFICER'], permissions: [] as string[] }

function renderShell(permissions: string[], activeSection: ApplicationWorkspaceSection = 'overview') {
  const onRefresh = vi.fn()
  render(<MemoryRouter><ApplicationWorkspaceShell actor={{ ...actor, permissions }}
    context={{ source: 'case', facts: applicationWorkspaceCaseFacts(applicationWorkspaceCase) }}
    activeSection={activeSection} updatedAt={0} refreshing={false} stale={false} onRefresh={onRefresh}>
    <p>Feature evidence</p>
  </ApplicationWorkspaceShell></MemoryRouter>)
  return onRefresh
}

describe('Application workspace presentation', () => {
  it.each([
    ['loan:read', ['Overview', 'History'], 'overview'],
    ['loan:review', ['Verification', 'Review'], 'verification'],
    ['document:review', ['Documents'], 'documents'],
    ['loan:correction:staff', ['Corrections'], 'corrections'],
    ['document:read', [], 'overview'],
  ] as const)('uses route capabilities for %s without implying access to other sections', (permission, labels, activeSection) => {
    renderShell([permission], activeSection)
    const navigation = screen.queryByRole('navigation', { name: 'Application sections' })
    if (labels.length) {
      expect(navigation).toBeVisible()
      expect(within(navigation!).getAllByRole('link').map((link) => link.textContent)).toEqual(labels)
      expect(within(navigation!).getByRole('link', { current: 'page' })).toHaveTextContent(labels[0]!)
    } else expect(navigation).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Back to applications/ }) !== null).toBe(permission === 'loan:read')
  })

  it('keeps the six sections in order and calls only the supplied route refresh', async () => {
    const refresh = renderShell(['loan:read', 'loan:review', 'document:review', 'loan:correction:staff'], 'review')
    const navigation = screen.getByRole('navigation', { name: 'Application sections' })
    expect(within(navigation).getAllByRole('link').map((link) => link.textContent)).toEqual(['Overview', 'History', 'Verification', 'Review', 'Documents', 'Corrections'])
    expect(within(navigation).getByRole('link', { current: 'page' })).toHaveTextContent('Review')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('CL-20261001-000005')
    expect(screen.getByText('Returned for revision')).toBeVisible()
    expectCanonicalApplicationHeader()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('renders reduced authorized facts with neutral unknown values without incidental ID exposure', async () => {
    render(<MemoryRouter><ApplicationWorkspaceShell actor={{ ...actor, permissions: ['document:review'] }}
      context={{ source: 'feature', facts: { loanApplicationId: applicationId, applicationStatus: 'FUTURE_STATUS', productCode: 'FUTURE_PRODUCT', originationChannel: 'FUTURE_CHANNEL' } }}
      activeSection="documents" updatedAt={0} refreshing={false} stale={true} onRefresh={() => undefined}>
      <p>Document evidence</p>
    </ApplicationWorkspaceShell></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/^Application$/)
    expect(screen.getByText('Status unavailable')).toBeVisible()
    expect(screen.getByText('Product unavailable')).toBeVisible()
    expect(screen.queryByText('Submitted')).not.toBeInTheDocument()
    expect(screen.getByText('Information unavailable')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Copy application ID' })).not.toBeInTheDocument()
  })

  it('copies the subordinate ID consistently for the canonical case header', async () => {
    const user = userEvent.setup()
    renderShell(['loan:read'], 'corrections')
    expectCanonicalApplicationHeader()
    await user.click(screen.getByRole('button', { name: 'Copy application ID' }))
    expect(await navigator.clipboard.readText()).toBe(applicationId)
    expect(screen.getByRole('button', { name: 'Copy application ID' })).toHaveTextContent('ID copied')
  })
})
