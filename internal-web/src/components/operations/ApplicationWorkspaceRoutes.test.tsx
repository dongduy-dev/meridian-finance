import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import * as api from '@/lib/api'
import { ApiError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { applicationWorkspaceCase as caseFacts, expectCanonicalApplicationHeader } from '@/test/application-workspace-fixture'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const casePath = `/staff/loan-applications/${caseFacts.loanApplicationId}`
const pagePath = `/staff/applications/${caseFacts.loanApplicationId}`
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-10-01T10:00:00Z',
  userId: '44444444-4444-4444-8444-444444444444', email: 'officer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'],
  permissions: ['loan:read', 'loan:review', 'document:review', 'loan:correction:staff'],
}
const featureFacts = {
  loanApplicationId: caseFacts.loanApplicationId, applicationNumber: caseFacts.applicationNumber,
  applicationStatus: caseFacts.status, productCode: caseFacts.productCode, productType: caseFacts.productType,
  requestedAmount: caseFacts.requestedAmount, requestedTermMonths: caseFacts.requestedTermMonths,
  submittedAt: caseFacts.submittedAt,
}
const cycle = {
  verificationId: '22222222-2222-4222-8222-222222222222', verificationSequence: 1,
  productVerificationResult: 'REQUIRES_MORE_INFORMATION', createdAt: caseFacts.submittedAt, reviewedAt: caseFacts.submittedAt,
}
const featureResponses: Record<string, unknown> = {
  '/review-history': {
    loanApplicationId: caseFacts.loanApplicationId, applicationNumber: caseFacts.applicationNumber,
    applicationStatus: caseFacts.status, cycles: [],
  },
  '/verification': {
    ...featureFacts, documentReadiness: { uploadComplete: true, processingReady: true }, correctionTargets: [],
    actions: { startAvailable: false, completeAvailable: false },
    productVerification: { currentCycle: cycle, history: [cycle], collateral: caseFacts.collateralContext },
  },
  '/review': {
    ...featureFacts, documentReadiness: { uploadComplete: true, processingReady: true },
    productReadiness: { productVerificationResult: 'REQUIRES_MORE_INFORMATION', readyForReview: false },
    reviewStartAvailable: false, assignedLoanOfficer: null, currentReviewCycle: null,
  },
  '/documents': {
    loanApplicationId: caseFacts.loanApplicationId, applicationStatus: caseFacts.status,
    originationChannel: caseFacts.originationChannel, checklistStage: 'SUBMISSION',
    uploadComplete: false, processingReady: false, items: [],
  },
  '/corrections': {
    loanApplicationId: caseFacts.loanApplicationId, applicationNumber: caseFacts.applicationNumber,
    applicationStatus: caseFacts.status, productCode: caseFacts.productCode, originationChannel: caseFacts.originationChannel,
    correctionRequest: null,
    assistedCancellation: { available: false, correctionRequestId: null, evidence: null, evidenceUploadAvailable: false, cancellationCommandAvailable: false },
  },
}
const featureRoutes = [
  ['/verification', 'Verification', 'loan:review', 'Product verification'],
  ['/review', 'Review', 'loan:review', 'Loan Officer review'],
  ['/documents', 'Documents', 'document:review', 'Application documents'],
  ['/corrections', 'Corrections', 'loan:correction:staff', 'Application corrections'],
] as const

function mockReads(caseRead: () => unknown | Promise<unknown> = () => caseFacts) {
  vi.mocked(api.apiRequest).mockImplementation(async (path) => {
    if (path === casePath) return caseRead()
    const suffix = Object.keys(featureResponses).find((key) => path === casePath + key)
    if (suffix) return featureResponses[suffix]
    throw new Error(`Unexpected feature query: ${path}`)
  })
}
function renderRoute(suffix: string, queryClient = createQueryClient()) {
  const router = createTestRouter([pagePath + suffix])
  render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return { router, queryClient }
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  vi.mocked(authApi.refresh).mockResolvedValue(staff)
})

describe('Canonical application header across authorized routes', () => {
  it.each([{ route: '#overview', label: 'Overview' }, { route: '#history', label: 'History' }, ...featureRoutes.map(([route, label]) => ({ route, label }))])('retains identical ordered facts on $route', async ({ route, label }) => {
      mockReads()
      renderRoute(route)
      await screen.findByRole('heading', { level: 1, name: caseFacts.applicationNumber })
      await waitFor(() => expectCanonicalApplicationHeader())
      const navigation = within(screen.getByRole('navigation', { name: 'Application sections' }))
      expect(navigation.getByRole('link', { name: label })).toHaveAttribute('aria-current', label === 'History' ? 'location' : 'page')
      if (route === '#history') await waitFor(() => expect(screen.getByRole('heading', { name: 'Application history' })).toHaveFocus())
      expect(vi.mocked(api.apiRequest).mock.calls.filter(([path]) => path === casePath)).toHaveLength(1)
    })

  it('reuses the application case cache across section navigation and keeps Refresh local', async () => {
    mockReads()
    const { router } = renderRoute('#overview')
    await screen.findByRole('heading', { level: 1, name: caseFacts.applicationNumber })
    expectCanonicalApplicationHeader()
    for (const [suffix] of featureRoutes) {
      await act(() => router.navigate(pagePath + suffix))
      await waitFor(() => expectCanonicalApplicationHeader())
      await screen.findByRole('heading', { name: featureRoutes.find(([path]) => path === suffix)![3] })
    }
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path]) => path === casePath)).toHaveLength(1)
    const callsBefore = vi.mocked(api.apiRequest).mock.calls.length
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.length).toBe(callsBefore + 2))
    expect(vi.mocked(api.apiRequest).mock.calls.slice(callsBefore).map(([path]) => path).sort())
      .toEqual([casePath, casePath + '/corrections'].sort())
  })
})

describe('Independent feature authority and optional application context', () => {
  it.each(featureRoutes)('does not read cached or remote application facts without loan:read on %s', async (route, label, permission, heading) => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: [permission] })
    mockReads()
    const queryClient = createQueryClient()
    renderRoute(route, queryClient)
    await screen.findByRole('heading', { name: heading, level: 2 })
    await act(() => { queryClient.setQueryData(staffApplicationKeys.case(caseFacts.loanApplicationId), caseFacts) })
    const header = screen.getByRole('heading', { level: 1 }).closest('header')!
    expect(within(header).queryByText('Requested amount') !== null).toBe(route === '/verification' || route === '/review')
    expect(within(header).queryByRole('button', { name: 'Copy application ID' }) !== null).toBe(route === '/documents')
    expect(screen.getByRole('link', { name: label, current: 'page' })).toBeVisible()
    const expectedReads = route === '/review'
      ? [casePath + route, casePath + '/review-history'] : [casePath + route]
    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.map(([path]) => path).sort())
      .toEqual(expectedReads.sort()))
  })

  it.each(featureRoutes)('keeps the feature usable while case context is pending on %s', async (route, _label, _permission, heading) => {
    let resolveCase!: (value: typeof caseFacts) => void
    mockReads(() => new Promise((resolve) => { resolveCase = resolve }))
    renderRoute(route)
    expect(await screen.findByRole('heading', { name: heading, level: 2 })).toBeVisible()
    expect(screen.queryByText('Loading application case')).not.toBeInTheDocument()
    await act(async () => resolveCase(caseFacts))
    await waitFor(() => expectCanonicalApplicationHeader())
  })

  it.each(featureRoutes.flatMap((route) => [403, 404, 'parse', 503].map((failure) => ({ route, failure }))))('falls back to feature facts after $failure on $route.0', async ({ route: [route, , , heading], failure }) => {
      mockReads(() => {
        if (failure === 'parse') return { invalid: true }
        throw new ApiError(failure as number, 'CASE_UNAVAILABLE', 'unavailable', casePath, 'now')
      })
      const queryClient = createQueryClient()
      queryClient.setDefaultOptions({ queries: { retry: false } })
      renderRoute(route, queryClient)
      expect(await screen.findByRole('heading', { name: heading, level: 2 })).toBeVisible()
      expect(await screen.findByText('Application context unavailable')).toBeVisible()
      const header = screen.getByRole('heading', { level: 1 }).closest('header')!
      expect(within(header).queryByRole('button', { name: 'Copy application ID' }) !== null).toBe(route === '/documents')
      expect(within(header).queryByText('Requested amount') !== null).toBe(route === '/verification' || route === '/review')
      expect(screen.getByRole('button', { name: 'Retry application context' })).toBeVisible()
    })

  it('marks cached context as stale while its refresh is pending and uses the feature-owned current status', async () => {
    let pending = false
    let resolveCase!: (value: typeof caseFacts) => void
    mockReads(() => pending
      ? new Promise((resolve) => { resolveCase = resolve })
      : { ...caseFacts, status: 'UNDER_REVIEW' })
    const { queryClient } = renderRoute('/corrections')
    await waitFor(() => expectCanonicalApplicationHeader())
    pending = true
    await act(() => {
      queryClient.setQueryData(staffApplicationKeys.case(caseFacts.loanApplicationId), {
        ...caseFacts, status: 'UNDER_REVIEW',
      }, { updatedAt: Date.now() - 60_000 })
      void queryClient.refetchQueries({ queryKey: staffApplicationKeys.case(caseFacts.loanApplicationId) })
    })
    expectCanonicalApplicationHeader()
    expect(await screen.findByText('These details may be out of date. Refresh before taking action.')).toBeVisible()
    await act(async () => resolveCase(caseFacts))
    await waitFor(() => expect(screen.queryByText('These details may be out of date. Refresh before taking action.')).not.toBeInTheDocument())
  })

  it('drops failed cached enrichment after a refresh and restores it on retry without feature refetch', async () => {
    let unavailable = false
    mockReads(() => {
      if (unavailable) throw new ApiError(403, 'FORBIDDEN', 'unavailable', casePath, 'now')
      return caseFacts
    })
    const queryClient = createQueryClient()
    queryClient.setDefaultOptions({ queries: { retry: false } })
    renderRoute('/corrections', queryClient)
    await waitFor(() => expectCanonicalApplicationHeader())
    unavailable = true
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText('Application context unavailable')
    expect(screen.queryByText('Requested amount')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Copy application ID' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'No correction request' })).toBeVisible()
    const featureReads = vi.mocked(api.apiRequest).mock.calls.filter(([path]) => path === casePath + '/corrections').length
    unavailable = false
    await user.click(screen.getByRole('button', { name: 'Retry application context' }))
    await waitFor(() => expectCanonicalApplicationHeader())
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path]) => path === casePath + '/corrections')).toHaveLength(featureReads)
  })
})
