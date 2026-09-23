import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const reviewId = '10000000-0000-4000-8000-000000000001'
const companyId = '20000000-0000-4000-8000-000000000002'
const customerId = '30000000-0000-4000-8000-000000000003'
const employeeId = '40000000-0000-4000-8000-000000000004'
const batchId = '50000000-0000-4000-8000-000000000005'
const item = {
  reviewId, customerId, partnerCompanyId: companyId, partnerCompanyCode: 'ACME',
  partnerCompanyName: 'Acme Ltd', effectiveMonth: '2026-09', triggerOutcome: 'NOT_FOUND',
  requestedEmployeeCode: 'EMP-001', status: 'PENDING', createdAt: '2026-09-22T08:00:00',
  reviewable: true, nonReviewableReason: null,
}
const detail = {
  reviewId, customerId,
  partnerCompany: { id: companyId, companyCode: 'ACME', name: 'Acme Ltd', status: 'ACTIVE' },
  effectiveMonth: '2026-09', sourceImportBatchId: batchId, triggerOutcome: 'NOT_FOUND',
  requestedEmployeeCode: 'EMP-001', status: 'PENDING', decisionOutcome: null, decisionReason: null,
  selectedEmployee: null, reviewerUserId: null, reviewedAt: null,
  createdAt: '2026-09-22T08:00:00', updatedAt: '2026-09-22T08:00:00',
  candidates: [{ partnerEmployeeId: employeeId, importBatchId: batchId, employeeCode: 'EMP-001', employmentStatus: 'ACTIVE', active: true }],
  approvalAvailable: true, rejectionAvailable: true, nonReviewableReason: null,
}
const page = { page: 0, size: 20, totalElements: 1, totalPages: 1, items: [item] }

const actor = (permissions: string[]): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'token', expiresAt: '2026-09-22T12:00:00Z',
  userId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', email: 'admin@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['BACK_OFFICE_ADMIN'], permissions,
})

function renderPage() {
  const router = createTestRouter(['/admin/partner-eligibility-reviews'])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

function mockReviewReads(review: unknown = detail) {
  vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
    if ((options as RequestInit | undefined)?.method) throw new Error('Unexpected command')
    if (String(path).includes('?status=PENDING')) return page
    if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) return review
    throw new Error(`Unexpected path ${path}`)
  })
}

describe('Partner eligibility review page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    localStorage.clear()
  })

  it('renders the queue and purpose-limited candidate evidence for a read-only actor', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read']))
    mockReviewReads()
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Eligibility reviews' })).toBeVisible()
    expect(await screen.findByText('Requested employee code: EMP-001')).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Current identity-matched candidates' })).toBeVisible()
    expect(screen.getByText('EMP-001', { selector: 'td' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Approve selected employee' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject review' })).not.toBeInTheDocument()
    expect(JSON.stringify([...vi.mocked(api.apiRequest).mock.calls])).not.toContain('IDENTITY-SECRET')
  })

  it('renders the empty queue state', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read']))
    vi.mocked(api.apiRequest).mockResolvedValueOnce({ ...page, totalElements: 0, totalPages: 0, items: [] })
    renderPage()
    expect(await screen.findByText('No pending Partner eligibility reviews require action.')).toBeVisible()
  })

  it('renders a retryable queue error state', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read']))
    vi.mocked(api.apiRequest).mockRejectedValue(new NetworkError('service unavailable'))
    renderPage()

    expect(await screen.findByText('Partner data unavailable', undefined, { timeout: 3_000 })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('disables manager decisions when the backend marks the review stale', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    mockReviewReads({
      ...detail, candidates: [], approvalAvailable: false, rejectionAvailable: false,
      nonReviewableReason: 'SOURCE_BATCH_REPLACED',
    })
    renderPage()

    expect(await screen.findByText(/Source Batch Replaced/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Approve selected employee' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Reject review' })).toBeDisabled()
  })

  it('approves only through partner manage and refetches authoritative state', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let detailReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const method = (options as RequestInit | undefined)?.method
      if (method === 'POST') return { ...detail, status: 'APPROVED', decisionOutcome: 'MANUAL_REVIEW_APPROVED', decisionReason: 'CURRENT_EMPLOYEE_CONFIRMED' }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) { detailReads += 1; return detailReads > 1 ? { ...detail, status: 'APPROVED', decisionOutcome: 'MANUAL_REVIEW_APPROVED', decisionReason: 'CURRENT_EMPLOYEE_CONFIRMED', approvalAvailable: false, rejectionAvailable: false } : detail }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Approve selected employee' }))

    await screen.findByText('The authoritative review outcome was confirmed.')
    expect(detailReads).toBeGreaterThan(1)
    const command = vi.mocked(api.apiRequest).mock.calls.find(([, options]) => (options as RequestInit | undefined)?.method === 'POST')
    expect((command?.[1] as { body: unknown }).body).toEqual({
      outcome: 'APPROVE', partnerEmployeeId: employeeId, reasonCode: 'CURRENT_EMPLOYEE_CONFIRMED',
    })
  })

  it('rejects with the selected controlled reason', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    mockReviewReads()
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') return { ...detail, status: 'REJECTED', decisionOutcome: 'MANUAL_REVIEW_REJECTED', decisionReason: 'IDENTITY_EVIDENCE_MISMATCH' }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) return detail
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Rejection reason'), 'IDENTITY_EVIDENCE_MISMATCH')
    await user.click(screen.getByRole('button', { name: 'Reject review' }))

    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.some(([, options]) =>
      (options as { body?: unknown })?.body && (options as { body: { outcome?: string } }).body.outcome === 'REJECT')).toBe(true))
  })

  it('reconciles an uncertain command with GET without retrying the POST', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let posts = 0
    let detailReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') { posts += 1; throw new NetworkError('response lost') }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) {
        detailReads += 1
        return detailReads > 1 ? { ...detail, status: 'REJECTED', decisionOutcome: 'MANUAL_REVIEW_REJECTED', decisionReason: 'NO_ELIGIBLE_CURRENT_EMPLOYEE', approvalAvailable: false, rejectionAvailable: false } : detail
      }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Reject review' }))

    expect(await screen.findByText(/reconciled this review through an authoritative GET/i)).toBeVisible()
    expect(posts).toBe(1)
    expect(detailReads).toBeGreaterThan(1)
  })
})
