import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RecordedCustomerActionPanel } from './RecordedCustomerActionPanel'

function renderAction(action: string) {
  render(<RecordedCustomerActionPanel title="Recorded Customer action" value={{
    action,
    recordedBy: { displayName: 'Deni Loan Officer', email: 'deni@meridian.local' },
    recordedAt: '2026-10-01T08:00:00',
    evidence: { documentVersionId: '11111111-1111-4111-8111-111111111111', versionNumber: 2 },
  }} />)
}

describe('Recorded Customer action presentation', () => {
  it.each([['ACCEPT', 'Accepted'], ['DECLINE', 'Declined']])('presents %s as a Customer decision', (action, label) => {
    renderAction(action)
    expect(screen.getByText('Customer decision')).toBeVisible()
    expect(screen.getByText('Customer decision recorded from signed evidence')).toBeVisible()
    expect(screen.getByText(label)).toBeVisible()
    expect(screen.queryByText('Customer request')).not.toBeInTheDocument()
  })

  it('presents cancellation as a Customer request rather than a decision', () => {
    renderAction('CUSTOMER_REQUESTED_CANCELLATION')
    expect(screen.getByText('Customer request')).toBeVisible()
    expect(screen.getByText('Customer request recorded from signed evidence')).toBeVisible()
    expect(screen.getByText('Customer-requested cancellation')).toBeVisible()
    expect(screen.queryByText('Customer decision')).not.toBeInTheDocument()
    expect(screen.queryByText('Customer decision recorded from signed evidence')).not.toBeInTheDocument()
  })

  it('keeps unknown actions neutral and unavailable without action controls', () => {
    renderAction('FUTURE_ACTION')
    expect(screen.getByText('Decision unavailable')).toBeVisible()
    expect(screen.getByText('Customer action recorded from signed evidence')).toBeVisible()
    expect(screen.queryByText('Customer decision')).not.toBeInTheDocument()
    expect(screen.queryByText('Customer request')).not.toBeInTheDocument()
    expect(screen.queryByText('FUTURE_ACTION')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })
})
