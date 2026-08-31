import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it } from 'vitest'

import type { CustomerApplicationSummary } from '../application-api'
import { RequiredActionCard } from './RequiredActionCard'

const application: CustomerApplicationSummary = {
  loanApplicationId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', applicationNumber: 'UCL-001',
  productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'UNSECURED', requestedAmount: 5_000_000,
  requestedTermMonths: 6, status: 'DOCUMENTS_PENDING', submittedAt: '2026-08-31T09:00:00',
  lifecycleActive: true, requiredAction: 'UPLOAD_DOCUMENTS',
}

it('links only implemented Customer actions to their authoritative workspaces', () => {
  const { rerender } = render(<MemoryRouter><RequiredActionCard application={application} /></MemoryRouter>)
  expect(screen.getByRole('link', { name: 'Upload documents' })).toHaveAttribute('href', `/applications/${application.loanApplicationId}/documents`)

  rerender(<MemoryRouter><RequiredActionCard application={{ ...application, requiredAction: 'COMPLETE_CORRECTIONS' }} /></MemoryRouter>)
  expect(screen.getByRole('link', { name: 'Complete corrections' })).toHaveAttribute('href', `/applications/${application.loanApplicationId}/corrections`)

  for (const requiredAction of ['REVIEW_APPROVED_OFFER', 'ACKNOWLEDGE_CONTRACT', 'FUTURE_ACTION']) {
    rerender(<MemoryRouter><RequiredActionCard application={{ ...application, requiredAction }} /></MemoryRouter>)
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  }
})
