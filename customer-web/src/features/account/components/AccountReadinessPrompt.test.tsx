import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { Customer } from '../account-api'
import { AccountReadinessPrompt } from './AccountReadinessPrompt'

const ready: Customer = {
  customerId: '22222222-2222-4222-8222-222222222222', customerNumber: 'CUS-000000001',
  status: 'ACTIVE', verificationStatus: 'VERIFIED', profileCompletionStatus: 'COMPLETE',
  primaryActiveBankAccountPresent: true, profile: null,
}

describe('Account readiness guidance', () => {
  it.each([
    [{ profileCompletionStatus: 'INCOMPLETE' }, 'Complete profile', '/account/profile'],
    [{ verificationStatus: 'UNVERIFIED' }, 'Open identity verification', '/account/identity-verification'],
    [{ primaryActiveBankAccountPresent: false }, 'Manage bank accounts', '/account/bank-accounts'],
  ] as const)('represents each missing fact independently', (missing, label, route) => {
    render(<MemoryRouter><AccountReadinessPrompt customer={{ ...ready, ...missing }} /></MemoryRouter>)
    expect(screen.getByRole('link', { name: label })).toHaveAttribute('href', route)
    expect(screen.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByText(/Finishing setup does not confirm loan eligibility/)).toBeVisible()
  })
  it('has no missing setup prompt once all three facts are ready', () => {
    const { container } = render(<MemoryRouter><AccountReadinessPrompt customer={ready} /></MemoryRouter>)
    expect(container).toBeEmptyDOMElement()
  })
})
