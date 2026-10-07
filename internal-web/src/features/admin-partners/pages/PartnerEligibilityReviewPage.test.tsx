import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError, NetworkError } from '@/lib/api'
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
const secondEmployeeId = '40000000-0000-4000-8000-000000000006'
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
    expect(screen.getByRole('heading', { name: 'Review detail' })).toBeVisible()
    expect(document.body.textContent).not.toMatch(/authoritative review detail/i)
    expect(await screen.findByText('Requested employee code: EMP-001')).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Current identity-matched candidates' })).toBeVisible()
    expect(screen.getByText('EMP-001', { selector: 'td' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review approval' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review rejection' })).not.toBeInTheDocument()
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

  it('requires deliberate employee selection and clears it when switching reviews', async () => {
    const secondReviewId = '10000000-0000-4000-8000-000000000007'
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method) throw new Error('Unexpected command')
      if (String(path).includes('?status=PENDING')) return {
        ...page,
        totalElements: 2,
        items: [item, { ...item, reviewId: secondReviewId, partnerCompanyName: 'Beta Ltd', partnerCompanyCode: 'BETA' }],
      }
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) return detail
      if (String(path) === `/admin/partner-eligibility-reviews/${secondReviewId}`) return {
        ...detail,
        reviewId: secondReviewId,
        partnerCompany: { ...detail.partnerCompany, name: 'Beta Ltd', companyCode: 'BETA' },
        candidates: [{ ...detail.candidates[0], partnerEmployeeId: secondEmployeeId, employeeCode: 'EMP-002' }],
      }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()

    const firstCandidate = await screen.findByRole('radio', { name: 'Select EMP-001' })
    expect(firstCandidate).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Review approval' })).toBeDisabled()
    await user.click(firstCandidate)
    expect(screen.getByRole('button', { name: 'Review approval' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: /Beta Ltd/ }))
    const secondCandidate = await screen.findByRole('radio', { name: 'Select EMP-002' })
    expect(secondCandidate).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Review approval' })).toBeDisabled()
  })

  it('disables manager decisions when the backend marks the review stale', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    mockReviewReads({
      ...detail, candidates: [], approvalAvailable: false, rejectionAvailable: false,
      nonReviewableReason: 'SOURCE_BATCH_REPLACED',
    })
    renderPage()

    expect(await screen.findByText(/Ask the Customer to verify employment again against the current snapshot/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review approval' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Review rejection' })).toBeDisabled()
  })

  it('confirms safe approval facts before sending the existing decision body', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let detailReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const method = (options as RequestInit | undefined)?.method
      if (method === 'POST') return { ...detail, status: 'APPROVED', decisionOutcome: 'MANUAL_REVIEW_APPROVED', decisionReason: 'CURRENT_EMPLOYEE_CONFIRMED', selectedEmployee: detail.candidates[0] }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) { detailReads += 1; return detailReads > 1 ? { ...detail, status: 'APPROVED', decisionOutcome: 'MANUAL_REVIEW_APPROVED', decisionReason: 'CURRENT_EMPLOYEE_CONFIRMED', selectedEmployee: detail.candidates[0], approvalAvailable: false, rejectionAvailable: false } : detail }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('radio', { name: 'Select EMP-001' }))
    const trigger = screen.getByRole('button', { name: 'Review approval' })
    await user.click(trigger)

    const approvalDialog = screen.getByRole('dialog', { name: 'Confirm employment approval' })
    expect(approvalDialog).toBeVisible()
    expect(within(approvalDialog).getByText('Acme Ltd (ACME)')).toBeVisible()
    expect(within(approvalDialog).getByText('EMP-001', { selector: 'dd' })).toBeVisible()
    expect(within(approvalDialog).getByText(/may replace it through this controlled approval/i)).toBeVisible()
    expect(JSON.stringify(document.body.textContent)).not.toContain('IDENTITY-SECRET')
    expect(vi.mocked(api.apiRequest).mock.calls.some(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(trigger).toHaveFocus())
    expect(vi.mocked(api.apiRequest).mock.calls.some(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toBe(false)

    await user.click(trigger)
    await user.click(screen.getByRole('button', { name: 'Confirm approval' }))

    await screen.findByText('Your decision was confirmed.')
    expect(detailReads).toBeGreaterThan(1)
    const command = vi.mocked(api.apiRequest).mock.calls.find(([, options]) => (options as RequestInit | undefined)?.method === 'POST')
    expect((command?.[1] as { body: unknown }).body).toEqual({
      outcome: 'APPROVE', partnerEmployeeId: employeeId, reasonCode: 'CURRENT_EMPLOYEE_CONFIRMED',
    })
  })

  it('confirms the controlled rejection reason before sending the existing decision body', async () => {
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
    await user.click(screen.getByRole('button', { name: 'Review rejection' }))

    expect(screen.getByRole('dialog', { name: 'Confirm eligibility rejection' })).toBeVisible()
    expect(screen.getByText('Identity evidence does not match', { selector: 'dd' })).toBeVisible()
    expect(screen.getByText(/does not create or change a Partner Employee link/i)).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([, options]) => (options as RequestInit | undefined)?.method === 'POST')).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Confirm rejection' }))

    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.find(([, options]) =>
      (options as { body?: unknown })?.body && (options as { body: { outcome?: string } }).body.outcome === 'REJECT')?.[1]).toMatchObject({
      body: { outcome: 'REJECT', partnerEmployeeId: null, reasonCode: 'IDENTITY_EVIDENCE_MISMATCH' },
    }))
  })

  it('classifies a matching rejection after an uncertain command as confirmed', async () => {
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
    await user.click(await screen.findByRole('button', { name: 'Review rejection' }))
    await user.click(screen.getByRole('button', { name: 'Confirm rejection' }))

    expect(await screen.findByText('Your decision was confirmed after Meridian refreshed the review.')).toBeVisible()
    expect(posts).toBe(1)
    expect(detailReads).toBeGreaterThan(1)
  })

  it('classifies a matching approval after an uncertain command as confirmed', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let posts = 0
    let detailReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        posts += 1
        throw new ApiError(503, 'SERVICE_UNAVAILABLE', 'response lost', String(path), '2026-09-22T08:00:00Z')
      }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) {
        detailReads += 1
        return detailReads > 1 ? { ...detail, status: 'APPROVED', decisionOutcome: 'MANUAL_REVIEW_APPROVED', decisionReason: 'CURRENT_EMPLOYEE_CONFIRMED', selectedEmployee: detail.candidates[0], approvalAvailable: false, rejectionAvailable: false } : detail
      }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('radio', { name: 'Select EMP-001' }))
    await user.click(screen.getByRole('button', { name: 'Review approval' }))
    await user.click(screen.getByRole('button', { name: 'Confirm approval' }))

    expect(await screen.findByText('Your decision was confirmed after Meridian refreshed the review.')).toBeVisible()
    expect(posts).toBe(1)
  })

  it('keeps an uncertain pending review unconfirmed without retrying or substituting a candidate', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let posts = 0
    let detailReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') { posts += 1; throw new NetworkError('response lost') }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) {
        detailReads += 1
        return detailReads > 1 ? {
          ...detail,
          candidates: [{ ...detail.candidates[0], partnerEmployeeId: secondEmployeeId, employeeCode: 'EMP-002' }],
        } : detail
      }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('radio', { name: 'Select EMP-001' }))
    await user.click(screen.getByRole('button', { name: 'Review approval' }))
    await user.click(screen.getByRole('button', { name: 'Confirm approval' }))

    expect(await screen.findByText(/decision was not confirmed.*review is still pending/i)).toBeVisible()
    expect(await screen.findByRole('radio', { name: 'Select EMP-002' })).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Review approval' })).toBeDisabled()
    expect(posts).toBe(1)
  })

  it('reports a different terminal outcome without retrying the decision', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let posts = 0
    let detailReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') { posts += 1; throw new NetworkError('response lost') }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) {
        detailReads += 1
        return detailReads > 1 ? { ...detail, status: 'APPROVED', decisionOutcome: 'MANUAL_REVIEW_APPROVED', decisionReason: 'CURRENT_EMPLOYEE_CONFIRMED', selectedEmployee: detail.candidates[0], approvalAvailable: false, rejectionAvailable: false } : detail
      }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review rejection' }))
    await user.click(screen.getByRole('button', { name: 'Confirm rejection' }))

    expect(await screen.findByText(/completed with a different outcome/i)).toBeVisible()
    expect(screen.getByText(/Employment confirmed/)).toBeVisible()
    expect(posts).toBe(1)
  })

  it('leaves an uncertain decision unconfirmed when refresh fails', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let posts = 0
    let detailReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') { posts += 1; throw new NetworkError('response lost') }
      if (String(path).includes('?status=PENDING')) return page
      if (String(path) === `/admin/partner-eligibility-reviews/${reviewId}`) {
        detailReads += 1
        if (detailReads > 1) throw new NetworkError('refresh failed')
        return detail
      }
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review rejection' }))
    await user.click(screen.getByRole('button', { name: 'Confirm rejection' }))

    expect(await screen.findByRole('heading', { name: 'Decision was not confirmed' }, { timeout: 3000 })).toBeVisible()
    expect(posts).toBe(1)
  })

  it('keeps an unfamiliar trigger neutral and disables manager decisions', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    mockReviewReads({ ...detail, triggerOutcome: 'FUTURE_TRIGGER' })
    renderPage()

    expect(await screen.findByText('Verification result unavailable')).toBeVisible()
    expect(screen.queryByText('Future Trigger')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review approval' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review rejection' })).not.toBeInTheDocument()
  })
})
