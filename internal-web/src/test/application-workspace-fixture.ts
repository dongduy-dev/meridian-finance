import { screen, within } from '@testing-library/react'
import { expect } from 'vitest'
import type { StaffLoanApplicationCase } from '@/features/staff-applications/api/contracts'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'

export const applicationWorkspaceCase = {
  loanApplicationId: '11111111-1111-4111-8111-111111111111',
  applicationNumber: 'CL-20261001-000005',
  status: 'RETURNED_FOR_REVISION', productCode: 'COLLATERAL_LOAN', productType: 'SECURED',
  requestedAmount: 180_000_000, requestedTermMonths: 18, submittedAt: '2026-10-01T08:00:00Z',
  originationChannel: 'CUSTOMER_DIGITAL',
  customerContext: null,
  customerReadiness: { active: true, profileComplete: true, hasPrimaryActiveBankAccount: true, verificationStatus: 'VERIFIED' },
  collateralContext: { collateralType: 'CAR', description: 'Fictional vehicle', estimatedValue: 250_000_000, ownershipStatus: 'CUSTOMER_OWNED', conditionNote: 'Serviceable' },
  formalReviewRecorded: false, assignedLoanOfficer: null, lifecycleHistory: [],
} satisfies StaffLoanApplicationCase

export function expectCanonicalApplicationHeader() {
  const header = screen.getByRole('heading', { level: 1, name: applicationWorkspaceCase.applicationNumber }).closest('header')!
  const view = within(header)
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  expect(view.getByText('APPLICATION CASE')).toBeVisible()
  expect(view.getByText('Returned for revision')).toBeVisible()
  expect(view.getByText('Collateral Loan')).toBeVisible()
  expect(view.getByText(applicationWorkspaceCase.loanApplicationId)).toBeVisible()
  expect(view.getByRole('button', { name: 'Copy application ID' })).toBeVisible()
  expect(view.queryByText('Product type')).not.toBeInTheDocument()
  expect(Array.from(header.querySelectorAll('dt')).map((item) => item.textContent))
    .toEqual(['Requested amount', 'Requested term', 'Submitted', 'Origination channel'])
  expect(Array.from(header.querySelectorAll('dd')).map((item) => item.textContent))
    .toEqual([formatVnd(applicationWorkspaceCase.requestedAmount), '18 months', formatTimestamp(applicationWorkspaceCase.submittedAt), 'Customer digital'])
  return header
}
