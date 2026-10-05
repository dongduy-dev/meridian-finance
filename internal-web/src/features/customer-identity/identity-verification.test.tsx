import { onlineManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { IdentityVerificationPage } from './IdentityVerificationPage'
import { IntakeIdentityReadiness } from './IntakeIdentityReadiness'
import * as api from './api'
import { ApiError, NetworkError } from '@/lib/api/errors'

const auth = vi.hoisted(() => ({ permissions: ['customer:identity:verify'] as string[], role: 'LOAN_OFFICER' }))
vi.mock('@/features/auth/model/auth-context', () => ({ useAuth: () => ({ manager: {}, state: { status: 'authenticated', epoch: 1, actor: { userId: 'officer', permissions: auth.permissions, roles: [auth.role] } } }) }))
vi.mock('@/features/staff-documents/components/DocumentContentViewer', () => ({ DocumentContentViewer: ({ identityVerificationId }: { identityVerificationId: string }) => <button>View identity evidence {identityVerificationId}</button> }))
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), identityQueue: vi.fn(), identityDetail: vi.fn(), decideIdentity: vi.fn(), submitIntakeIdentity: vi.fn() }))
const id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const v = { verificationId: id, sequence: 1, customerNumber: 'CUS-001', fullName: 'Ari Fictional', source: 'CUSTOMER_DIGITAL', method: 'MANUAL_STAFF_DOCUMENT_REVIEW', status: 'PENDING_REVIEW', rejectionReason: null, submittedAt: '2026-10-03T10:00:00', completedAt: null,
  evidence: { versionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', versionNumber: 1, filename: 'identity.pdf', mimeType: 'application/pdf', byteSize: 40, uploadedAt: '2026-10-03T10:00:00' } }
let client: QueryClient
beforeEach(() => { auth.permissions = ['customer:identity:verify']; auth.role = 'LOAN_OFFICER'; client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); vi.mocked(api.identityQueue).mockResolvedValue([{ ...v, evidence: null }]); vi.mocked(api.identityDetail).mockResolvedValue(v) })
afterEach(() => { onlineManager.setOnline(true); client.clear(); vi.clearAllMocks() })
function detail() { render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[`/staff/customer-identity-verifications/${id}`]}><Routes><Route path="/staff/customer-identity-verifications/:verificationId" element={<IdentityVerificationPage />} /></Routes></MemoryRouter></QueryClientProvider>) }
it('keeps cached evidence readable but blocks decisions during and after a failed detail refresh', async () => {
  detail(); const user = userEvent.setup()
  const reference = await screen.findByLabelText('Identity Reference shown on document')
  await user.type(reference, 'TRANSIENT')
  let reject!: (error: Error) => void
  vi.mocked(api.identityDetail).mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
  let refresh!: Promise<void>
  await act(async () => {
    refresh = client.refetchQueries({ queryKey: ['customer-identity', id] })
    // Dispatch before the query observer can disable the previously rendered buttons.
    fireEvent.click(screen.getByRole('button', { name: 'Verify identity' }))
    fireEvent.click(screen.getByRole('button', { name: 'Reject evidence' }))
    expect(api.decideIdentity).not.toHaveBeenCalled()
  })
  await waitFor(() => expect(reference).toBeDisabled())
  expect(screen.getByRole('button', { name: 'Verify identity' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Reject evidence' })).toBeDisabled()
  await act(async () => { reject(new NetworkError()); await refresh })
  expect(await screen.findByText('Verification could not be loaded.')).toBeVisible()
  expect(screen.getByText('CUS-001 · Ari Fictional')).toBeVisible()
  expect(screen.getByText(/identity.pdf · Version 1/)).toBeVisible()
  expect(client.getQueryData(['customer-identity', id])).toEqual(v)
  await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  await user.click(screen.getByRole('button', { name: 'Reject evidence' }))
  expect(api.decideIdentity).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Refresh' }))
  await waitFor(() => expect(reference).toBeEnabled())
  expect(reference).toHaveValue('')
  await user.type(reference, 'CURRENT')
  expect(screen.getByRole('button', { name: 'Verify identity' })).toBeEnabled()
  expect(screen.getByRole('button', { name: 'Reject evidence' })).toBeEnabled()
})
it('blocks decisions while authoritative revalidation is paused offline despite cached successful data', async () => {
  detail(); const reference = await screen.findByLabelText('Identity Reference shown on document')
  onlineManager.setOnline(false)
  await act(async () => { await client.refetchQueries({ queryKey: ['customer-identity', id] }) })
  await waitFor(() => expect(reference).toBeDisabled())
  expect(client.getQueryState(['customer-identity', id])).toMatchObject({ status: 'success', fetchStatus: 'paused' })
  expect(screen.getByRole('button', { name: 'Reject evidence' })).toBeDisabled()
  expect(screen.getByText(/identity.pdf · Version 1/)).toBeVisible()
  await act(async () => { onlineManager.setOnline(true) })
  await waitFor(() => expect(reference).toBeEnabled())
})
it.each(['success', 'mismatch'] as const)('blocks cached pending decisions when reconciliation fails after %s', async outcome => {
  if (outcome === 'success') vi.mocked(api.decideIdentity).mockResolvedValueOnce({ ...v, status: 'VERIFIED' })
  else vi.mocked(api.decideIdentity).mockRejectedValueOnce(new ApiError(422, 'IDENTITY_REFERENCE_MISMATCH', 'Mismatch.', '', 'now'))
  vi.mocked(api.identityDetail).mockResolvedValueOnce(v).mockRejectedValueOnce(new NetworkError())
  detail(); const user = userEvent.setup()
  const reference = await screen.findByLabelText('Identity Reference shown on document')
  await user.type(reference, 'TRANSIENT'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByText('Verification could not be loaded.')).toBeVisible()
  expect(screen.getByText('Pending review · Manual Staff document review')).toBeVisible()
  expect(reference).toHaveValue(''); expect(reference).toBeDisabled()
  await user.click(screen.getByRole('button', { name: 'Reject evidence' }))
  expect(api.decideIdentity).toHaveBeenCalledTimes(1)
  vi.mocked(api.identityDetail).mockResolvedValueOnce(outcome === 'success' ? { ...v, status: 'VERIFIED' } : v)
  await user.click(screen.getByRole('button', { name: 'Refresh' }))
  if (outcome === 'success') {
    expect(await screen.findByText('Verified · Manual Staff document review')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Reject evidence' })).not.toBeInTheDocument()
  } else {
    await waitFor(() => expect(reference).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Reject evidence' })).toBeEnabled()
  }
})
it.each(['APPROVER', 'ACCOUNTING_OFFICER', 'BACK_OFFICE_ADMIN', 'CUSTOMER'])('denies %s before queries without the exact purpose permission', role => {
  auth.role = role; auth.permissions = ['loan:read', 'document:review']; detail()
  expect(screen.getByRole('alert')).toHaveTextContent('Customer identity verification authority is required.')
  expect(api.identityDetail).not.toHaveBeenCalled(); expect(api.identityQueue).not.toHaveBeenCalled()
})
it('uses exact evidence and transient confirmation, then clears it and keeps query cache free of the reference', async () => {
  vi.mocked(api.decideIdentity).mockResolvedValue({ ...v, status: 'VERIFIED' })
  detail(); const user = userEvent.setup()
  const reference = await screen.findByLabelText('Identity Reference shown on document')
  expect(screen.getByRole('button', { name: 'Verify identity' })).toBeDisabled()
  await user.type(reference, 'TRANSIENT-ID-ONLY'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  await waitFor(() => expect(reference).toHaveValue(''))
  expect(api.decideIdentity).toHaveBeenCalledWith({}, id, true, v.evidence.versionId, 'TRANSIENT-ID-ONLY', 'UNREADABLE_EVIDENCE')
  expect(JSON.stringify(client.getQueryCache().getAll().map(q => q.state.data))).not.toContain('TRANSIENT-ID-ONLY')
  expect(client.getMutationCache().getAll()).toHaveLength(0)
  expect(screen.getByRole('button', { name: `View identity evidence ${id}` })).toBeVisible()
})
it('protects an uncertain network result until explicit GET reconciliation and never retries the decision automatically', async () => {
  vi.mocked(api.decideIdentity).mockRejectedValue(new NetworkError())
  detail(); const user = userEvent.setup(); const reference = await screen.findByLabelText('Identity Reference shown on document')
  await user.type(reference, 'WRONG'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByText(/decision was not confirmed/)).toBeVisible(); expect(reference).toHaveValue(''); expect(api.decideIdentity).toHaveBeenCalledTimes(1)
  expect(api.identityDetail).toHaveBeenCalledTimes(2)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Reject evidence' })).toBeDisabled())
  expect(reference).toBeDisabled()
  await user.click(screen.getByRole('button', { name: 'Refresh' }))
  await waitFor(() => expect(reference).toBeEnabled())
  await user.type(reference, 'REENTERED')
  expect(screen.getByRole('button', { name: 'Verify identity' })).toBeEnabled()
  expect(api.decideIdentity).toHaveBeenCalledTimes(1)
})

it('shows a deterministic mismatch, clears the reference and allows immediate manual retry', async () => {
  vi.mocked(api.decideIdentity).mockRejectedValueOnce(new ApiError(422, 'IDENTITY_REFERENCE_MISMATCH', 'Presented Identity Reference does not match the Customer profile.', '', '2026-10-03'))
    .mockResolvedValueOnce({ ...v, status: 'VERIFIED' })
  vi.mocked(api.identityDetail).mockResolvedValueOnce(v).mockResolvedValueOnce(v).mockResolvedValueOnce({ ...v, status: 'VERIFIED' })
  detail(); const user = userEvent.setup(); const reference = await screen.findByLabelText('Identity Reference shown on document')
  await user.type(reference, 'WRONG-TRANSIENT'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByRole('heading', { name: 'Identity Reference does not match' })).toBeVisible()
  expect(screen.getByRole('alert')).toHaveTextContent('Check the identity document and try again.')
  expect(screen.queryByText(/decision was not confirmed/)).not.toBeInTheDocument()
  expect(screen.getByText('Pending review · Manual Staff document review')).toBeVisible()
  expect(reference).toHaveValue('')
  await waitFor(() => expect(reference).toBeEnabled())
  expect(api.decideIdentity).toHaveBeenCalledTimes(1)
  expect(JSON.stringify(client.getQueryCache().getAll().map(q => q.state.data))).not.toContain('WRONG-TRANSIENT')
  expect(client.getMutationCache().getAll()).toHaveLength(0)
  await user.type(reference, 'CORRECT-TRANSIENT'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByText('Verified · Manual Staff document review')).toBeVisible()
  expect(api.decideIdentity).toHaveBeenCalledTimes(2)
  expect(screen.queryByLabelText('Identity Reference shown on document')).not.toBeInTheDocument()
})
it('keeps deterministic invalid review fields retryable without uncertain-result feedback', async () => {
  vi.mocked(api.decideIdentity).mockRejectedValueOnce(new ApiError(422, 'INVALID_IDENTITY_VERIFICATION_REQUEST', 'Invalid review.', '', '2026-10-03'))
  detail(); const user = userEvent.setup(); const reference = await screen.findByLabelText('Identity Reference shown on document')
  await user.type(reference, 'TRANSIENT'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByRole('heading', { name: 'Check the identity review details' })).toBeVisible()
  expect(reference).toHaveValue(''); await waitFor(() => expect(reference).toBeEnabled())
  expect(screen.queryByText(/decision was not confirmed/)).not.toBeInTheDocument()
})
it.each([
  ['IDENTITY_VERIFICATION_EVIDENCE_STALE', 'SUPERSEDED', 'Identity evidence has changed'],
  ['IDENTITY_VERIFICATION_ALREADY_COMPLETED', 'VERIFIED', 'Identity review already completed'],
])('reconciles deterministic %s with authoritative detail', async (code, status, title) => {
  vi.mocked(api.decideIdentity).mockRejectedValueOnce(new ApiError(409, code, 'Review state changed.', '', '2026-10-03'))
  vi.mocked(api.identityDetail).mockResolvedValueOnce(v).mockResolvedValueOnce({ ...v, status })
  detail(); const user = userEvent.setup(); await user.type(await screen.findByLabelText('Identity Reference shown on document'), 'TRANSIENT')
  await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByRole('heading', { name: title })).toBeVisible()
  await waitFor(() => expect(screen.queryByLabelText('Identity Reference shown on document')).not.toBeInTheDocument())
  expect(screen.queryByText(/decision was not confirmed/)).not.toBeInTheDocument()
  expect(api.decideIdentity).toHaveBeenCalledTimes(1)
})
it.each([[500, 'INTERNAL_ERROR'], [422, 'UNEXPECTED_RESPONSE'], [408, 'REQUEST_TIMEOUT']])('preserves reconciliation for ambiguous %s %s', async (status, code) => {
  vi.mocked(api.decideIdentity).mockRejectedValueOnce(new ApiError(status as number, code as string, 'Unconfirmed.', '', '2026-10-03'))
  detail(); const user = userEvent.setup(); const reference = await screen.findByLabelText('Identity Reference shown on document')
  await user.type(reference, 'TRANSIENT'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByText(/decision was not confirmed/)).toBeVisible()
  await waitFor(() => expect(reference).toBeDisabled())
  expect(reference).toHaveValue(''); expect(api.decideIdentity).toHaveBeenCalledTimes(1)
})
it('rejects with a controlled reason and no presented reference', async () => {
  vi.mocked(api.decideIdentity).mockResolvedValue({ ...v, status: 'REJECTED', rejectionReason: 'NAME_MISMATCH' })
  detail(); const user = userEvent.setup(); await user.selectOptions(await screen.findByLabelText('Rejection reason'), 'NAME_MISMATCH'); await user.click(screen.getByRole('button', { name: 'Reject evidence' }))
  expect(api.decideIdentity).toHaveBeenCalledWith({}, id, false, v.evidence.versionId, '', 'NAME_MISMATCH')
})
it('shows only safe queue identity and no arbitrary Customer directory', async () => {
  render(<QueryClientProvider client={client}><MemoryRouter><IdentityVerificationPage /></MemoryRouter></QueryClientProvider>)
  expect(await screen.findByText('CUS-001 · Ari Fictional')).toBeVisible()
  expect(screen.queryByText('identity.pdf')).not.toBeInTheDocument(); expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
})
it('reuses verified Customer identity for another assisted loan without requesting new evidence', () => {
  render(<MemoryRouter><IntakeIdentityReadiness caseId={id} status="VERIFIED" open /></MemoryRouter>)
  expect(screen.getByText(/A new identity file is not required/)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Review current Customer identity evidence' })).not.toBeInTheDocument()
})

it('does not offer decisions for unknown verification methods', async () => {
  vi.mocked(api.identityDetail).mockResolvedValue({ ...v, method: 'FUTURE_METHOD' })
  detail(); expect(await screen.findByText('CUS-001 · Ari Fictional')).toBeVisible()
  expect(screen.queryByLabelText('Identity Reference shown on document')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Verify identity' })).not.toBeInTheDocument()
})
it('shows a safe detail read failure without review controls', async () => {
  vi.mocked(api.identityDetail).mockRejectedValue(new Error('Unavailable'))
  detail(); expect(await screen.findByRole('alert')).toHaveTextContent('Verification could not be loaded')
  expect(screen.queryByRole('button', { name: 'Verify identity' })).not.toBeInTheDocument()
})
