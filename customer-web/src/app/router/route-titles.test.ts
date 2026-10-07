import { expect, it } from 'vitest'
import { customerRouteTitle } from './route-titles'

it.each([
  ['/', 'Home'], ['/products', 'Products'], ['/applications', 'Applications'], ['/loans', 'Loans'],
  ['/account', 'Account'], ['/account/profile', 'Profile'],
  ['/account/identity-verification', 'Identity verification'], ['/account/bank-accounts', 'Bank accounts'],
  ['/login', 'Login'], ['/register', 'Create Account'], ['/verify-email', 'Confirm Email'],
  ['/verify-email/pending', 'Email Confirmation Required'], ['/activate-access', 'Activate your access'],
  ['/forgot-password', 'Forgot Password'], ['/reset-password', 'Reset Password'],
  ['/products/salary-advance', 'Salary Advance'], ['/products/salary-advance/apply', 'Apply for Salary Advance'],
  ['/products/unsecured-consumer-loan', 'Unsecured Consumer Loan'],
  ['/products/unsecured-consumer-loan/apply', 'Apply for Unsecured Consumer Loan'],
  ['/products/collateral-loan', 'Collateral Loan'], ['/products/collateral-loan/apply', 'Apply for Collateral Loan'],
  ['/applications/example', 'Application details'], ['/applications/example/documents', 'Application documents'],
  ['/applications/example/corrections', 'Requested changes'], ['/applications/example/offer', 'Your offer'],
  ['/applications/example/contract', 'Your contract'], ['/loans/example', 'Loan details'],
  ['/account/profile/', 'Profile'], ['/not-a-route', 'Page unavailable'],
  ['/applications/example/unsupported', 'Page unavailable'], ['/foundation/flow', 'Page unavailable'],
])('gives %s a Customer-facing browser title', (path, title) => {
  expect(customerRouteTitle(path)).toBe(title)
})

it('identifies repayment history without putting query values in the title', () => {
  expect(customerRouteTitle('/loans/example', '?tab=repayments&page=2')).toBe('Repayment history')
  expect(customerRouteTitle('/loans/example', '?tab=unknown')).toBe('Loan details')
})
