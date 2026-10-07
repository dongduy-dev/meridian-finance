import { act, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { AppProviders } from '@/app/providers/AppProviders'
import { queryClient } from '@/app/providers/query-client'
import { applicationKeys } from '@/features/applications/application-queries'
import { loanKeys } from '@/features/loans/loan-queries'
import { loanProductKeys } from '@/features/loan-products/loan-product-queries'
import { createTestAuthManager } from '@/test/auth'
import { createTestRouter } from './router'

const customer = { customerId: '22222222-2222-4222-8222-222222222222', customerNumber: 'CUS-001', status: 'ACTIVE', verificationStatus: 'VERIFIED', profileCompletionStatus: 'COMPLETE', primaryActiveBankAccountPresent: true, profile: null }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { queryClient.clear(); vi.unstubAllGlobals() })

it.each([
  ['/products', '/loan-products', loanProductKeys.list(), 'No products available', 'Products could not be loaded'],
  ['/applications', '/loan-applications', applicationKeys.index(), 'No applications yet', 'Applications could not be loaded'],
  ['/loans', '/loan-accounts', loanKeys.index(), 'No loans yet', 'Loans could not be loaded'],
  ['/', '/loan-applications', applicationKeys.index(), "You're up to date online", 'Next steps could not be loaded'],
  ['/', '/loan-accounts', loanKeys.index(), 'No active loans', 'Loans could not be loaded'],
] as const)('does not turn a failed %s refresh into an empty-record claim', async (path, endpoint, key, empty, failure) => {
  let failed = false
  vi.stubGlobal('fetch', vi.fn(async input => String(input).endsWith('/customers/me') ? json(customer)
    : failed && String(input).endsWith(endpoint)
      ? json({ status: 403, errorCode: 'FORBIDDEN', message: 'Internal read failure' }, 403) : json([])))
  render(<AppProviders router={createTestRouter([path])} authManager={createTestAuthManager()} />)
  expect(await screen.findByText(empty)).toBeVisible()
  failed = true
  await act(async () => { await queryClient.refetchQueries({ queryKey: key, exact: true }) })
  expect(await screen.findByText(failure)).toBeVisible()
  expect(screen.queryByText(empty)).not.toBeInTheDocument()
  expect(screen.queryByText('Internal read failure')).not.toBeInTheDocument()
  expect(queryClient.getQueryData(key)).toEqual([])
})
