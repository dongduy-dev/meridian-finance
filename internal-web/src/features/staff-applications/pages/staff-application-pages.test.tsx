import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { createTestRouter } from '@/app/router/router'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const applicationId = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
const staff = (permissions: string[] = ['loan:read']): AuthResponse => ({
  tokenType: 'Bearer',
  accessToken: 'staff-token',
  expiresAt: '2026-09-02T10:00:00Z',
  userId: '11111111-1111-4111-8111-111111111111',
  email: 'staff@meridian.local',
  userType: 'STAFF',
  customerId: null,
  roles: ['LOAN_OFFICER'],
  permissions,
})

const item = {
  loanApplicationId: applicationId,
  applicationNumber: 'UCL-20260902-000001',
  productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED',
  requestedAmount: 12_000_000,
  requestedTermMonths: 6,
  status: 'UNDER_REVIEW',
  submittedAt: '2026-09-02T08:00:00',
}

const page = (overrides: Record<string, unknown> = {}) => ({
  page: 0,
  size: 20,
  totalElements: 21,
  totalPages: 2,
  items: [item],
  ...overrides,
})

const caseFixture = {
  ...item,
  customerContext: null,
  collateralContext: null,
  formalReviewRecorded: true,
  assignedLoanOfficer: {
    userId: '11111111-1111-4111-8111-111111111111',
    displayName: 'Deni Loan Officer',
    email: 'staff@meridian.local',
  },
  customerReadiness: {
    active: true,
    profileComplete: true,
    hasPrimaryActiveBankAccount: true,
    verificationStatus: 'VERIFIED',
  },
  lifecycleHistory: [
    { fromStatus: null, toStatus: 'SUBMITTED', action: 'SUBMIT_APPLICATION', actorType: 'SYSTEM', actor: null, occurredAt: '2026-09-02T08:00:00' },
    { fromStatus: 'SUBMITTED', toStatus: 'UNDER_REVIEW', action: 'START_REVIEW', actorType: 'USER',
      actor: { userId: '11111111-1111-4111-8111-111111111111', displayName: 'Deni Loan Officer', email: 'staff@meridian.local' },
      occurredAt: '2026-09-02T09:00:00' },
  ],
}

function renderRoute(path: string) {
  const router = createTestRouter([path])
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider><RouterProvider router={router} /></AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

describe('Staff application pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
  })

  it('loads the safe default index, reflects filters and paging in the URL, and opens a case', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(page())
    const user = userEvent.setup()
    const router = renderRoute('/staff/applications')

    expect(await screen.findByRole('heading', { name: 'Applications', level: 1 })).toBeVisible()
    expect(await screen.findByText('UCL-20260902-000001')).toBeVisible()
    expect(screen.getByText(/12\.000\.000/)).toBeVisible()
    expect(screen.queryByText(/customer@example|bank account number/i)).not.toBeInTheDocument()
    expect(api.apiRequest).toHaveBeenCalledWith(
      '/staff/loan-applications?page=0&size=20',
      expect.objectContaining({ headers: expect.any(Object) }),
    )

    await user.selectOptions(screen.getByLabelText('Product'), 'SALARY_ADVANCE')
    await waitFor(() => expect(router.state.location.search).toContain('productCode=SALARY_ADVANCE'))
    await waitFor(() => expect(api.apiRequest).toHaveBeenCalledWith(
      expect.stringContaining('productCode=SALARY_ADVANCE'),
      expect.any(Object),
    ))

    await user.selectOptions(screen.getByLabelText('Application status'), 'APPROVAL_PENDING')
    await waitFor(() => expect(router.state.location.search).toContain('status=APPROVAL_PENDING'))

    await user.click(screen.getByRole('button', { name: 'Next application page' }))
    await waitFor(() => expect(router.state.location.search).toContain('page=1'))

    await user.click(screen.getByRole('button', { name: 'Reset filters' }))
    await waitFor(() => expect(router.state.location.search).toBe(''))

    const link = screen.getByRole('link', { name: 'Open case UCL-20260902-000001' })
    expect(link).toHaveAttribute('href', `/staff/applications/${applicationId}`)
  })

  it('normalizes malformed URL state without sending a backend request', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(page())
    const user = userEvent.setup()
    const router = renderRoute('/staff/applications?status=NOT_REAL&page=-1')

    expect(await screen.findByRole('heading', { name: 'Invalid application filters' })).toBeVisible()
    expect(api.apiRequest).not.toHaveBeenCalled()
    await user.click(screen.getAllByRole('button', { name: 'Reset filters' })[0]!)
    await waitFor(() => expect(router.state.location.search).toBe(''))
    await waitFor(() => expect(api.apiRequest).toHaveBeenCalled())
  })

  it('renders a scoped empty state from backend page metadata without inventing totals', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(page({
      totalElements: 0,
      totalPages: 0,
      items: [],
    }))
    renderRoute('/staff/applications?productCode=COLLATERAL_LOAN&page=0')

    expect(await screen.findByRole('heading', { name: 'No applications match these filters' })).toBeVisible()
    expect(screen.getByText('0 applications · Page 0 of 0')).toBeVisible()
  })

  it('renders unknown response status neutrally without enabling an action', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(page({ items: [{ ...item, status: 'FUTURE_STATUS' }] }))
    renderRoute('/staff/applications')

    expect(await screen.findByText('Status unavailable')).toBeVisible()
    expect(screen.queryByRole('button', { name: /approve|review|disburse/i })).not.toBeInTheDocument()
  })

  it('renders the safe case header, readiness, authoritative history order, and background refresh state', async () => {
    let finishRefresh: ((value: unknown) => void) | undefined
    vi.mocked(api.apiRequest)
      .mockResolvedValueOnce(caseFixture)
      .mockImplementationOnce(() => new Promise((resolve) => { finishRefresh = resolve }))
    const user = userEvent.setup()
    renderRoute(`/staff/applications/${applicationId}`)

    const heading = await screen.findByRole('heading', { name: 'UCL-20260902-000001', level: 1 })
    expect(heading).toBeVisible()
    await waitFor(() => expect(heading).toHaveFocus())
    expect(screen.getByRole('navigation', { name: 'Application sections' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'History' })).toHaveAttribute('href', `/staff/applications/${applicationId}#history`)
    expect(screen.queryByRole('link', { name: 'Verification' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Copy application ID' }))
    expect(await navigator.clipboard.readText()).toBe(applicationId)
    expect(screen.getByText('Customer readiness')).toBeVisible()
    expect(screen.getByText('Primary bank account')).toBeVisible()
    expect(screen.getAllByText('Deni Loan Officer').length).toBeGreaterThan(0)
    expect(screen.getAllByText('staff@meridian.local').length).toBeGreaterThan(0)
    expect(screen.getByText('System')).toBeVisible()
    expect(screen.queryByText(/customer@example|0123456789|identity number/i)).not.toBeInTheDocument()

    const history = screen.getByRole('heading', { name: 'Application history' }).closest('section')!
    const evidence = within(history).getAllByRole('listitem')
    expect(evidence[0]).toHaveTextContent('Application submitted')
    expect(evidence[1]).toHaveTextContent('Loan Officer review started')
    expect(screen.queryByText(/operationId|actorUserId|restricted decision note/i)).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByRole('button', { name: 'Refreshing…' })).toBeDisabled()
    expect(screen.getByRole('heading', { name: 'UCL-20260902-000001', level: 1 })).toBeVisible()
    finishRefresh?.(caseFixture)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled())
  })

  it('opens History after a delayed direct read and returns to Overview with accessible focus', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(caseFixture)
    const user = userEvent.setup()
    const router = renderRoute(`/staff/applications/${applicationId}#history`)
    const history = await screen.findByRole('heading', { name: 'Application history' })
    await waitFor(() => expect(history).toHaveFocus())
    expect(screen.getByRole('link', { name: 'History' })).toHaveAttribute('aria-current', 'location')
    await user.click(screen.getByRole('link', { name: 'Overview' }))
    await waitFor(() => expect(router.state.location.hash).toBe('#overview'))
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Application and readiness' })).toHaveFocus())
    expect(screen.getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
  })

  it('renders unresolved USER actor evidence neutrally without inventing an identity', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...caseFixture,
      assignedLoanOfficer: null,
      lifecycleHistory: [
        { ...caseFixture.lifecycleHistory[0] },
        { ...caseFixture.lifecycleHistory[1], actor: null },
      ],
    })
    renderRoute(`/staff/applications/${applicationId}`)

    expect(await screen.findByText('Assignment unavailable')).toBeVisible()
    expect(screen.getByText('System')).toBeVisible()
    expect(screen.getByText('User unavailable')).toBeVisible()
    expect(screen.queryByText('Staff actor unavailable')).not.toBeInTheDocument()
    expect(screen.queryByText(/Unknown Staff|Current user/)).not.toBeInTheDocument()
  })

  it('shows an application as unassigned before formal review is recorded', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...caseFixture,
      formalReviewRecorded: false,
      assignedLoanOfficer: null,
      lifecycleHistory: [caseFixture.lifecycleHistory[0]],
    })
    renderRoute(`/staff/applications/${applicationId}`)

    expect(await screen.findByText('Unassigned')).toBeVisible()
    expect(screen.queryByText('Assignment unavailable')).not.toBeInTheDocument()
  })

  it('shows current Customer contact only when the narrow context is returned', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:read', 'customer:read']))
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...caseFixture,
      customerContext: { customerNumber: 'CUST-001', fullName: 'Nguyen Van A', phoneNumber: '0901234567' },
    })
    renderRoute(`/staff/applications/${applicationId}`)

    expect(await screen.findByText('Customer contact details')).toBeVisible()
    expect(screen.getByText('CUST-001')).toBeVisible()
    expect(screen.getByText('Nguyen Van A')).toBeVisible()
    expect(screen.getByText('0901234567')).toBeVisible()
    expect(screen.getByText(/Application evidence remains unchanged/i)).toBeVisible()
  })

  it('omits Customer contact when the case response has none', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(caseFixture)
    renderRoute(`/staff/applications/${applicationId}`)

    expect(await screen.findByRole('heading', { name: 'UCL-20260902-000001', level: 1 })).toBeVisible()
    expect(screen.queryByText('Customer contact details')).not.toBeInTheDocument()
  })

  it('shows collateral facts on a Collateral Loan case', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...caseFixture, productCode: 'COLLATERAL_LOAN',
      collateralContext: { collateralType: 'CAR', description: 'Vehicle', estimatedValue: 3200000000,
        ownershipStatus: 'Owner', conditionNote: 'Very good' },
    })
    renderRoute(`/staff/applications/${applicationId}`)

    expect(await screen.findByText('Submitted collateral facts')).toBeVisible()
    expect(screen.getByText('Vehicle')).toBeVisible()
    expect(screen.getByText('Very good')).toBeVisible()
  })

  it.each([
    [403, 'Application access changed'],
    [404, 'Application unavailable'],
    [503, 'Application details unavailable'],
  ])('presents a safe case error for HTTP %s', async (status, heading) => {
    vi.mocked(api.apiRequest).mockRejectedValue(new ApiError(
      status,
      status === 404 ? 'LOAN_APPLICATION_NOT_FOUND' : 'ACCESS_DENIED',
      'unsafe backend detail',
      `/staff/loan-applications/${applicationId}`,
      '2026-09-02T08:00:00Z',
      'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    ))
    renderRoute(`/staff/applications/${applicationId}`)

    expect(await screen.findByRole('heading', { name: heading }, { timeout: 4_000 })).toBeVisible()
    expect(screen.queryByText('unsafe backend detail')).not.toBeInTheDocument()
  })
})
