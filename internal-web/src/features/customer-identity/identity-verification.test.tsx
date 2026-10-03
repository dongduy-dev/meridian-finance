import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { IdentityVerificationPage } from './IdentityVerificationPage'
import { IntakeIdentityReadiness } from './IntakeIdentityReadiness'
import * as api from './api'

const auth = vi.hoisted(() => ({ permissions: ['customer:identity:verify'] as string[], role: 'LOAN_OFFICER' }))
vi.mock('@/features/auth/model/auth-context', () => ({ useAuth: () => ({ manager: {}, state: { status: 'authenticated', epoch: 1, actor: { userId: 'officer', permissions: auth.permissions, roles: [auth.role] } } }) }))
vi.mock('@/features/staff-documents/components/DocumentContentViewer', () => ({ DocumentContentViewer: ({ identityVerificationId }: { identityVerificationId: string }) => <button>View identity evidence {identityVerificationId}</button> }))
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), identityQueue: vi.fn(), identityDetail: vi.fn(), decideIdentity: vi.fn(), submitIntakeIdentity: vi.fn() }))
const id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const v = { verificationId: id, sequence: 1, customerNumber: 'CUS-001', fullName: 'Ari Fictional', source: 'CUSTOMER_DIGITAL', method: 'MANUAL_STAFF_DOCUMENT_REVIEW', status: 'PENDING_REVIEW', rejectionReason: null, submittedAt: '2026-10-03T10:00:00', completedAt: null,
  evidence: { versionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', versionNumber: 1, filename: 'identity.pdf', mimeType: 'application/pdf', byteSize: 40, uploadedAt: '2026-10-03T10:00:00' } }
let client: QueryClient
beforeEach(() => { auth.permissions = ['customer:identity:verify']; auth.role = 'LOAN_OFFICER'; client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); vi.mocked(api.identityQueue).mockResolvedValue([{ ...v, evidence: null }]); vi.mocked(api.identityDetail).mockResolvedValue(v) })
afterEach(() => { client.clear(); vi.clearAllMocks() })
function detail() { render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[`/staff/customer-identity-verifications/${id}`]}><Routes><Route path="/staff/customer-identity-verifications/:verificationId" element={<IdentityVerificationPage />} /></Routes></MemoryRouter></QueryClientProvider>) }
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
it('clears failed confirmation, refreshes detail and never retries a decision automatically', async () => {
  vi.mocked(api.decideIdentity).mockRejectedValue(new Error('Mismatch'))
  detail(); const user = userEvent.setup(); const reference = await screen.findByLabelText('Identity Reference shown on document')
  await user.type(reference, 'WRONG'); await user.click(screen.getByRole('button', { name: 'Verify identity' }))
  expect(await screen.findByText(/decision was not confirmed/)).toBeVisible(); expect(reference).toHaveValue(''); expect(api.decideIdentity).toHaveBeenCalledTimes(1)
  expect(api.identityDetail).toHaveBeenCalledTimes(2)
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
