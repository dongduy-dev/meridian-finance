import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import { AccountErrorFeedback } from '@/features/account/components/AccountFeedback'
import { OriginationSubmissionError } from '@/features/applications/components/OriginationSupport'
import { correctionErrorMessage } from '@/features/corrections/correction-presentation'
import { applicationStatusPresentation } from '@/features/applications/application-presentation'
import { contractStatusPresentation } from '@/features/contracts/contract-presentation'
import { eligibilityNoteLabel } from '@/features/loan-products/loan-product-presentation'
import { QueryErrorFeedback } from './QueryErrorFeedback'

const internalMessage = 'Persistence DTO conflict in source batch 123 with internal verificationStatus'
const error = new ApiError({ status: 500, errorCode: 'FUTURE_INTERNAL_ERROR', message: internalMessage, requestId: 'support-example' })

it.each(['query', 'account', 'application'] as const)('keeps %s failures useful without displaying the raw API message', surface => {
  render(surface === 'query' ? <QueryErrorFeedback title="Records could not be loaded" error={error} onRetry={() => undefined} />
    : surface === 'account' ? <AccountErrorFeedback title="Update was not confirmed" error={error} />
      : <OriginationSubmissionError error={error} />)
  expect(screen.queryByText(internalMessage)).not.toBeInTheDocument()
  expect(screen.getByText(/Support reference: support-example/)).toBeVisible()
  expect(screen.getByRole('alert')).toHaveTextContent(surface === 'application' ? /Check your application list/ : /connection|try again/)
})

it('maps a known account conflict without revealing another Customer association', () => {
  render(<AccountErrorFeedback title="Save was not confirmed" error={new ApiError({ status: 409, errorCode: 'IDENTITY_REFERENCE_ALREADY_IN_USE', message: internalMessage })} />)
  expect(screen.getByText(/Check it against your identity document/)).toBeVisible()
  expect(screen.queryByText(internalMessage)).not.toBeInTheDocument()
})

it('uses a safe correction fallback for an unrecognized code', () => {
  expect(correctionErrorMessage(error, 'Check your requested changes.')).toBe('Check your requested changes.')
})

it('keeps unknown statuses neutral while naming known stages for Customers', () => {
  expect(applicationStatusPresentation('APPROVAL_PENDING').label).toBe('Awaiting decision')
  expect(applicationStatusPresentation('DISBURSEMENT_PENDING').label).toBe('Funds transfer pending')
  expect(applicationStatusPresentation('FUTURE_STATUS').label).toBe('Status unavailable')
  expect(contractStatusPresentation('ACKNOWLEDGED').label).toBe('Review confirmed')
  expect(contractStatusPresentation('FUTURE_STATUS').label).toBe('Status unavailable')
})

it('translates only verified policy notes and preserves unfamiliar returned policy information', () => {
  expect(eligibilityNoteLabel('Estimated collateral value does not produce an automated loan-to-value decision.')).toBe('Your estimated collateral value does not automatically determine how much you can borrow.')
  expect(eligibilityNoteLabel('A new Customer policy note.')).toBe('A new Customer policy note.')
})
