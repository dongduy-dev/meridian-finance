import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { AppProviders } from '@/app/providers/AppProviders'
import { queryClient } from '@/app/providers/query-client'
import { createAuthApiMock, createTestAuthManager, customerAuthResponse } from '@/test/auth'
import { createTestRouter } from './router'

const id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const version = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const customer = { customerId: '22222222-2222-4222-8222-222222222222', customerNumber: 'CUS-001', status: 'ACTIVE', verificationStatus: 'UNVERIFIED', profileCompletionStatus: 'COMPLETE', primaryActiveBankAccountPresent: false, profile: null }
const pending = { verificationId: id, sequence: 1, customerNumber: 'CUS-001', fullName: 'Ari Fictional', source: 'CUSTOMER_DIGITAL', method: 'MANUAL_STAFF_DOCUMENT_REVIEW', status: 'PENDING_REVIEW', rejectionReason: null, submittedAt: '2026-10-03T10:00:00', completedAt: null,
  evidence: { versionId: version, versionNumber: 1, filename: 'identity.pdf', mimeType: 'application/pdf', byteSize: 40, uploadedAt: '2026-10-03T10:00:00' } }
const response = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })
function renderRoute(path: string, fetcher: typeof fetch, permissions = ['customer:identity:read:own', 'customer:identity:write:own']) {
  vi.stubGlobal('fetch', vi.fn(fetcher))
  const api = createAuthApiMock(); vi.mocked(api.refresh).mockResolvedValue(customerAuthResponse({ permissions }))
  render(<AppProviders router={createTestRouter([path])} authManager={createTestAuthManager(api)} />)
}
afterEach(() => { queryClient.clear(); vi.unstubAllGlobals(); vi.restoreAllMocks() })
it('uploads before bank setup, shows pending rather than verified, and never sends a Customer ownership ID', async () => {
  let history: unknown[] = []; let body: FormData | undefined; let posts = 0
  renderRoute('/account/identity-verification', async (input, init) => {
    if (String(input).endsWith('/customers/me')) return response(customer)
    if (init?.method === 'POST') { posts++; body = init.body as FormData; history = [pending]; return response(pending) }
    return response(history)
  })
  const user = userEvent.setup()
  const file = await screen.findByLabelText(/Identity document/)
  await user.upload(file, new File(['%PDF-1.4 identity'], 'identity.pdf', { type: 'application/pdf' }))
  expect(screen.getByRole('button', { name: 'Submit identity evidence' })).toBeEnabled()
  await user.click(screen.getByRole('button', { name: 'Submit identity evidence' }))
  expect(await screen.findByRole('heading', { name: 'Pending review' })).toBeVisible()
  expect(screen.queryByText('Identity verified')).not.toBeInTheDocument()
  expect(posts).toBe(1); expect(body?.get('customerId')).toBeNull(); expect(body?.get('uploadRequestId')).toBeTruthy()
  expect(JSON.stringify(queryClient.getQueryCache().getAll().map(q => q.state.data))).not.toContain('identityReference')
})
it('shows controlled rejection and preserves history after replacement', async () => {
  const rejected = { ...pending, status: 'REJECTED', rejectionReason: 'UNREADABLE_EVIDENCE', completedAt: '2026-10-03T11:00:00' }
  let history: unknown[] = [rejected]; const replacement = { ...pending, verificationId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', sequence: 2, evidence: { ...pending.evidence, versionId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', versionNumber: 2 } }
  renderRoute('/account/identity-verification', async (input, init) => {
    if (String(input).endsWith('/customers/me')) return response({ ...customer, verificationStatus: init?.method ? 'UNVERIFIED' : 'REJECTED' })
    if (init?.method === 'POST') { history = [replacement, rejected]; return response(replacement) }
    return response(history)
  })
  const user = userEvent.setup()
  expect(await screen.findByText('The document could not be read clearly.')).toBeVisible()
  await user.upload(screen.getByLabelText(/Identity document/), new File(['%PDF-1.4 replacement'], 'replacement.pdf', { type: 'application/pdf' }))
  await user.click(screen.getByRole('button', { name: 'Submit replacement evidence' }))
  await waitFor(() => expect(screen.getByText('Attempt 2 · Pending review')).toBeVisible())
  expect(screen.getByText('Attempt 1 · Rejected')).toBeVisible()
})
it('shows verified identity and no upload form', async () => {
  renderRoute('/account/identity-verification', async input => String(input).endsWith('/customers/me') ? response({ ...customer, verificationStatus: 'VERIFIED' }) : response([{ ...pending, status: 'VERIFIED' }]))
  expect(await screen.findByRole('heading', { name: 'Identity verified' })).toBeVisible()
  expect(screen.queryByLabelText(/Identity document/)).not.toBeInTheDocument()
})
it.each(['/products/unsecured-consumer-loan/apply', '/products/collateral-loan/apply'])('blocks %s before loading the product form', async path => {
  let requests = 0
  renderRoute(path, async input => { requests++; expect(String(input)).toContain('/customers/me'); return response(customer) })
  expect(await screen.findByRole('heading', { name: 'Complete identity verification before applying' })).toBeVisible()
  expect(screen.getByRole('link', { name: 'Open identity verification' })).toHaveAttribute('href', '/account/identity-verification')
  expect(requests).toBe(1)
})
it('guards own identity queries before a missing capability', async () => {
  const fetcher = vi.fn<typeof fetch>()
  renderRoute('/account/identity-verification', fetcher, [])
  expect(await screen.findByText('Own identity verification access is required.')).toBeVisible(); expect(fetcher).not.toHaveBeenCalled()
})
it('refreshes an uncertain upload result without automatically retrying POST', async () => {
  let posts = 0
  renderRoute('/account/identity-verification', async (input, init) => {
    if (String(input).endsWith('/customers/me')) return response(customer)
    if (init?.method === 'POST') { posts++; throw new TypeError('Network failure') }
    return response([])
  })
  const user = userEvent.setup()
  await user.upload(await screen.findByLabelText(/Identity document/), new File(['%PDF-1.4'], 'identity.pdf', { type: 'application/pdf' }))
  expect(screen.getByRole('button', { name: 'Submit identity evidence' })).toBeEnabled()
  await user.click(screen.getByRole('button', { name: 'Submit identity evidence' }))
  expect(await screen.findByText(/Submission was not confirmed/)).toBeVisible(); expect(posts).toBe(1)
  expect(screen.getByRole('button', { name: 'Submit identity evidence' })).toBeDisabled()
})

it('fails closed for an unknown verification state', async () => {
  renderRoute('/account/identity-verification', async input => String(input).endsWith('/customers/me') ? response(customer) : response([{ ...pending, status: 'FUTURE_STATE' }]))
  expect(await screen.findByRole('alert')).toHaveTextContent('Verification state is unavailable')
  expect(screen.queryByLabelText(/Identity document/)).not.toBeInTheDocument()
})
