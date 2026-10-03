import { QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider, useAuth } from '@/features/auth/model/auth-context'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import * as api from '@/lib/api'
import { ApiError, NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { createTestRouter } from '@/app/router/router'
import { staffApplicationKeys } from '../api/queries'

vi.mock('@/features/auth/api/auth-api', async () => ({
  ...await vi.importActual<typeof authApi>('@/features/auth/api/auth-api'), refresh: vi.fn(), logout: vi.fn(),
}))
vi.mock('@/lib/api', async () => ({ ...await vi.importActual<typeof api>('@/lib/api'), apiRequest: vi.fn() }))

const id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
const otherId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
const secret = 'FICTIONAL-ID-8901'
const permissions = ['loan:read', 'customer:read', 'customer:identity:reveal']
const staff = (roles = ['LOAN_OFFICER'], grants = permissions): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'fictional-token', expiresAt: '2026-10-04T10:00:00Z',
  userId: '11111111-1111-4111-8111-111111111111', email: 'fictional@meridian.local',
  userType: 'STAFF', customerId: null, roles, permissions: grants,
})
const fixture = {
  loanApplicationId: id, applicationNumber: 'UCL-FICTIONAL', productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED', originationChannel: 'CUSTOMER_DIGITAL', requestedAmount: 3_000_000,
  requestedTermMonths: 6, status: 'CANCELLED', submittedAt: '2026-10-01T08:00:00',
  customerContext: { customerNumber: 'CUS-FICTIONAL', fullName: 'Ari Fictional', phoneNumber: '0900000000', maskedIdentityReference: '****8901' },
  collateralContext: null, customerReadiness: { active: true, profileComplete: true, hasPrimaryActiveBankAccount: false, verificationStatus: 'UNVERIFIED' },
  formalReviewRecorded: false, assignedLoanOfficer: null, lifecycleHistory: [],
}
let manager: AuthSessionManager
function Capture() {
  const auth = useAuth()
  useEffect(() => { manager = auth.manager }, [auth.manager])
  return null
}
function setup() {
  const queryClient = createQueryClient()
  const router = createTestRouter([`/staff/applications/${id}`])
  const view = render(<QueryClientProvider client={queryClient}><AuthProvider><Capture /><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return { ...view, router, queryClient }
}
const revealCalls = () => vi.mocked(api.apiRequest).mock.calls.filter(([path]) => String(path).endsWith('/customer-identity-reference/reveal'))
const revealClick = async () => userEvent.setup().click(await screen.findByRole('button', { name: 'Reveal Identity Reference' }))

describe('Application Case protected Identity Reference', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
    vi.mocked(authApi.logout).mockResolvedValue(undefined)
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).endsWith('/reveal')
      ? { loanApplicationId: id, identityReference: secret }
      : { ...fixture, loanApplicationId: String(path).includes(otherId) ? otherId : id })
  })
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })

  it('shows only the stored mask initially; explicit reveal and Hide never cache or persist the value', async () => {
    const { queryClient } = setup()
    expect(await screen.findByText('****8901')).toBeVisible()
    expect(screen.getByText('Ari Fictional')).toBeVisible()
    expect(screen.getByText('0900000000')).toBeVisible()
    expect(screen.queryByText(secret)).not.toBeInTheDocument()
    expect(revealCalls()).toHaveLength(0)
    await revealClick()
    expect(await screen.findByText(secret)).toBeVisible()
    expect(screen.queryByRole('button', { name: /copy identity/i })).not.toBeInTheDocument()
    expect(revealCalls()).toHaveLength(1)
    expect(revealCalls()[0]?.[1]).toMatchObject({ method: 'POST' })
    expect(JSON.stringify(queryClient.getQueryCache().getAll().map(q => q.state.data))).not.toContain(secret)
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain(secret)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Hide Identity Reference' }))
    expect(screen.queryByText(secret)).not.toBeInTheDocument()
  })

  it.each(['APPROVER', 'ACCOUNTING_OFFICER', 'BACK_OFFICE_ADMIN', 'CUSTOM_ROLE'])('hides mask and control for %s even if the response carries a mask', async role => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff([role]))
    setup()
    expect(await screen.findByText('Customer details')).toBeVisible()
    expect(screen.queryByText('****8901')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reveal Identity Reference' })).not.toBeInTheDocument()
  })

  it.each([['loan:read'], ['loan:read', 'customer:read'], ['loan:read', 'customer:read', 'customer:identity:verify']].map(grants => ({ grants })))('requires reveal authority beyond $grants', async ({ grants }) => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['LOAN_OFFICER'], grants))
    setup()
    expect(await screen.findByText('Customer details')).toBeVisible()
    expect(screen.queryByText('****8901')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reveal Identity Reference' })).not.toBeInTheDocument()
  })

  it.each(['hide', 'navigate', 'unmount', 'refresh', 'epoch', 'actor', 'authority', 'logout', 'context'] as const)('discards a late response after %s and prevents concurrent clicks', async action => {
    let resolve!: (value: unknown) => void
    vi.mocked(api.apiRequest).mockImplementation(async path => String(path).endsWith('/reveal')
      ? new Promise(done => { resolve = done })
      : { ...fixture, loanApplicationId: String(path).includes(otherId) ? otherId : id })
    const { router, unmount, queryClient } = setup()
    await revealClick()
    fireEvent.click(screen.getByRole('button', { name: 'Revealing…' }))
    expect(revealCalls()).toHaveLength(1)
    if (action === 'hide') await userEvent.setup().click(screen.getByRole('button', { name: 'Hide Identity Reference' }))
    if (action === 'navigate') await act(() => router.navigate(`/staff/applications/${otherId}`))
    if (action === 'unmount') unmount()
    if (action === 'refresh') await userEvent.setup().click(screen.getByRole('button', { name: /^refresh$/i }))
    if (action === 'epoch') await act(() => manager.refresh())
    if (action === 'actor') {
      vi.mocked(authApi.refresh).mockResolvedValue({ ...staff(), userId: otherId })
      await act(() => manager.refresh())
    }
    if (action === 'authority') {
      vi.mocked(authApi.refresh).mockResolvedValue(staff(['LOAN_OFFICER'], ['loan:read', 'customer:read']))
      await act(() => manager.refresh())
    }
    if (action === 'logout') await act(() => manager.logout())
    if (action === 'context') act(() => queryClient.setQueryData(staffApplicationKeys.case(id), { ...fixture, customerContext: { ...fixture.customerContext, maskedIdentityReference: '****9999' } }))
    await act(async () => resolve({ loanApplicationId: id, identityReference: secret }))
    expect(screen.queryByText(secret)).not.toBeInTheDocument()
    expect(revealCalls()).toHaveLength(1)
  })

  it('clears an already displayed secret on manual refresh and session change', async () => {
    setup(); await revealClick(); expect(await screen.findByText(secret)).toBeVisible()
    await userEvent.setup().click(screen.getByRole('button', { name: /^refresh$/i }))
    expect(screen.queryByText(secret)).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Reveal Identity Reference' })).toBeEnabled())
    await revealClick(); expect(await screen.findByText(secret)).toBeVisible()
    await act(() => manager.refresh())
    expect(screen.queryByText(secret)).not.toBeInTheDocument()
  })

  it('clears after 60 seconds hidden and cancels the timer when returning early', async () => {
    setup(); await revealClick(); expect(await screen.findByText(secret)).toBeVisible()
    vi.useFakeTimers()
    const visibility = vi.spyOn(document, 'visibilityState', 'get')
    visibility.mockReturnValue('hidden'); fireEvent(document, new Event('visibilitychange'))
    act(() => vi.advanceTimersByTime(59_999)); expect(screen.getByText(secret)).toBeVisible()
    visibility.mockReturnValue('visible'); fireEvent(document, new Event('visibilitychange'))
    act(() => vi.advanceTimersByTime(60_000)); expect(screen.getByText(secret)).toBeVisible()
    visibility.mockReturnValue('hidden'); fireEvent(document, new Event('visibilitychange'))
    act(() => vi.advanceTimersByTime(60_000)); expect(screen.queryByText(secret)).not.toBeInTheDocument()
  })

  it.each([[403, 'CUSTOMER_IDENTITY_REFERENCE_ACCESS_DENIED', 'access is denied'], [404, 'LOAN_APPLICATION_NOT_FOUND', 'application is unavailable'], [409, 'CUSTOMER_IDENTITY_REFERENCE_UNAVAILABLE', 'stored Identity Reference is unavailable']] as const)('handles controlled %s safely', async (status, code, message) => {
    vi.mocked(api.apiRequest).mockImplementation(async path => {
      if (String(path).endsWith('/reveal')) throw new ApiError(status, code, 'internal detail', '', 'now')
      return fixture
    })
    setup(); await revealClick()
    // 404 reconciliation remounts the panel, clearing feedback together with stale case state.
    if (status !== 404) expect(await screen.findByText(new RegExp(message))).toBeVisible()
    expect(screen.queryByText(secret)).not.toBeInTheDocument()
    expect(screen.queryByText('internal detail')).not.toBeInTheDocument()
    expect(revealCalls()).toHaveLength(1)
  })

  it('keeps unknown-result reveal blocked when case refresh fails, then unlocks after a successful refresh', async () => {
    let failRead = false
    vi.mocked(api.apiRequest).mockImplementation(async path => {
      if (String(path).endsWith('/reveal') || failRead) throw new NetworkError()
      return fixture
    })
    setup(); await revealClick()
    expect(await screen.findByText(/request was not repeated automatically/)).toBeVisible()
    failRead = true
    await userEvent.setup().click(screen.getByRole('button', { name: /^refresh$/i }))
    expect(await screen.findByText('Latest refresh unavailable', {}, { timeout: 3_000 })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Reveal Identity Reference' })).toBeDisabled()
    expect(revealCalls()).toHaveLength(1)
    failRead = false
    await userEvent.setup().click(screen.getByRole('button', { name: /^refresh$/i }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Reveal Identity Reference' })).toBeEnabled())
    expect(revealCalls()).toHaveLength(1)
  })

  it.each([
    { loanApplicationId: otherId, identityReference: secret },
    { loanApplicationId: id, identityReference: 'FICTIONAL-ID-9999' },
  ])('rejects a successful transport response with inconsistent application or mask context', async response => {
    vi.mocked(api.apiRequest).mockImplementation(async path => String(path).endsWith('/reveal') ? response : fixture)
    setup(); await revealClick()
    expect(await screen.findByText(/request was not repeated automatically/)).toBeVisible()
    expect(screen.queryByText(response.identityReference)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reveal Identity Reference' })).toBeDisabled()
  })

  it.each([new NetworkError(), new ApiError(503, 'SERVICE_UNAVAILABLE', 'internal detail', '', 'now'), new Error('unrecognized')])('requires successful case refresh after an unknown failure without repeating POST', async failure => {
    vi.mocked(api.apiRequest).mockImplementation(async path => {
      if (String(path).endsWith('/reveal')) throw failure
      return fixture
    })
    setup(); await revealClick()
    expect(await screen.findByText(/request was not repeated automatically/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Reveal Identity Reference' })).toBeDisabled()
    expect(revealCalls()).toHaveLength(1)
    await userEvent.setup().click(screen.getByRole('button', { name: /^refresh$/i }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Reveal Identity Reference' })).toBeEnabled())
    expect(revealCalls()).toHaveLength(1)
    await revealClick(); expect(revealCalls()).toHaveLength(2)
  })
})
