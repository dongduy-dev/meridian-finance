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
import { ApiError, NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { caseFixture, contractFixture } from '../api/contracts.test'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const applicationId = '11111111-1111-4111-8111-111111111111'
const operationId = '33333333-3333-4333-8333-333333333333'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-07T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'accounting@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'],
  permissions: ['loan:contract:read', 'loan:contract:prepare', 'loan:disbursement:prepare'],
}

function noContractCase() {
  return caseFixture({
    currentContract: null,
    readiness: {
      ...caseFixture().readiness,
      contractId: null,
      contractVersion: null,
      ready: false,
      blockerCodes: ['CURRENT_CONTRACT_MISSING'],
    },
    workStage: 'NEEDS_PREPARATION',
  })
}

function preparedCase(version = 1) {
  const contract = { ...contractFixture('PREPARED'), contractVersion: version }
  return caseFixture({
    currentContract: contract,
    readiness: {
      ...caseFixture().readiness,
      contractId: contract.contractId,
      contractVersion: version,
      ready: false,
      blockerCodes: ['ACKNOWLEDGMENT_MISSING'],
    },
    workStage: 'CUSTOMER_ACKNOWLEDGMENT_REQUIRED',
  })
}

function confirmedCase() {
  const contract = contractFixture('READY_FOR_DISBURSEMENT')
  return caseFixture({
    applicationStatus: 'DISBURSEMENT_PENDING',
    currentContract: contract,
    readiness: {
      ...caseFixture().readiness,
      ready: false,
      blockerCodes: ['READINESS_ALREADY_CONFIRMED'],
    },
    workStage: 'READINESS_CONFIRMED',
  })
}

function renderPage() {
  const router = createTestRouter([`/staff/applications/${applicationId}/contract`])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

function postCalls() {
  return vi.mocked(api.apiRequest).mock.calls.filter(([, options]) =>
    (options as RequestInit | undefined)?.method === 'POST')
}

describe('Staff contract workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(operationId)
  })

  it('renders immutable terms and only the masked destination without a Staff acknowledgment action', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(preparedCase())
    renderPage()

    expect(await screen.findByText('****7890')).toBeVisible()
    expect(screen.queryByText('1234567890')).not.toBeInTheDocument()
    expect(screen.getAllByText('Customer acknowledgment required').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: /acknowledge/i })).not.toBeInTheDocument()
    expect(document.body.textContent).toContain('10.000.000')
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).includes('/acknowledgment') || String(path).includes('/destination')
      || String(path).includes('/disbursements') || String(path).includes('/approved-offer'))).toBe(false)
  })

  it('prepares version 1 with exact version zero, null reason, and a generated stable UUID', async () => {
    let prepared = false
    let submitted: Record<string, unknown> | undefined
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        prepared = true
        submitted = (options as { body: Record<string, unknown> }).body
        return contractFixture('PREPARED')
      }
      return prepared ? preparedCase() : noContractCase()
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review preparation' }))
    await user.click(screen.getByRole('button', { name: 'Confirm exact operation' }))

    await screen.findByText(/command response and refreshed authoritative contract case are confirmed/i)
    expect(submitted).toEqual({
      preparationRequestId: operationId,
      expectedCurrentContractVersion: 0,
      supersessionReasonCode: null,
    })
  })

  it('regenerates the exact displayed version only for destination refresh', async () => {
    let submitted: Record<string, unknown> | undefined
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        submitted = (options as { body: Record<string, unknown> }).body
        throw new ApiError(409, 'CONTRACT_REGENERATION_NOT_ALLOWED', 'not allowed', '/contracts', 'now')
      }
      return preparedCase(2)
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review regeneration' }))
    expect(screen.getAllByText(/financial terms and repayment items remain unchanged/i).length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Confirm exact operation' }))

    await screen.findByText(/backend rejected the command/i)
    expect(submitted).toEqual({
      preparationRequestId: operationId,
      expectedCurrentContractVersion: 2,
      supersessionReasonCode: 'DISBURSEMENT_ACCOUNT_REFRESH',
    })
  })

  it('confirms advisory readiness against the exact displayed version and distinguishes disbursement', async () => {
    let confirmed = false
    let submitted: Record<string, unknown> | undefined
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        confirmed = true
        submitted = (options as { body: Record<string, unknown> }).body
        return contractFixture('READY_FOR_DISBURSEMENT')
      }
      return confirmed ? confirmedCase() : caseFixture()
    })
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByText(/POINT_IN_TIME_ADVISORY evidence can become stale/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Review readiness confirmation' }))
    expect(screen.getByText(/not transferred funds or an activated LoanAccount/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Confirm exact operation' }))

    expect(await screen.findByRole('heading', { name: 'Readiness confirmed — not disbursed' })).toBeVisible()
    expect(submitted).toEqual({ confirmationRequestId: operationId, expectedContractVersion: 1 })
  })

  it('keeps a lost result unresolved and retries the exact UUID and payload only on operator action', async () => {
    let attempts = 0
    const submitted: Record<string, unknown>[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        attempts += 1
        submitted.push((options as { body: Record<string, unknown> }).body)
        if (attempts === 1) throw new NetworkError('response lost')
        return contractFixture('PREPARED')
      }
      return attempts > 1 ? preparedCase() : noContractCase()
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review preparation' }))
    await user.click(screen.getByRole('button', { name: 'Confirm exact operation' }))

    expect(await screen.findByRole('heading', { name: 'Contract preparation result unknown' })).toBeVisible()
    expect(postCalls()).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Retry exact operation' }))
    await screen.findByText(/command response and refreshed authoritative contract case are confirmed/i)
    expect(postCalls()).toHaveLength(2)
    expect(submitted[1]).toEqual(submitted[0])
    expect(submitted[0]).toMatchObject({ preparationRequestId: operationId, expectedCurrentContractVersion: 0 })
  })

  it('does not POST again after definite success when refresh fails', async () => {
    let posted = false
    let readsAvailable = true
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        posted = true
        readsAvailable = false
        return contractFixture('READY_FOR_DISBURSEMENT')
      }
      if (posted && !readsAvailable) throw new NetworkError('refresh unavailable')
      return posted ? confirmedCase() : caseFixture()
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review readiness confirmation' }))
    await user.click(screen.getByRole('button', { name: 'Confirm exact operation' }))

    expect(await screen.findByText(/Command confirmed; refreshed state unavailable/i)).toBeVisible()
    expect(postCalls()).toHaveLength(1)
    readsAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText(/confirmed command is now reconciled/i)
    expect(postCalls()).toHaveLength(1)
  })

  it('fails closed for unknown blocker or status values', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(caseFixture({
      currentContract: { ...contractFixture(), status: 'FUTURE_STATUS' },
      readiness: { ...caseFixture().readiness, blockerCodes: ['FUTURE_BLOCKER'] },
      workStage: 'FUTURE_STAGE',
    }))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Operational evidence unavailable' })).toBeVisible()
    expect(screen.getByText('Unknown readiness blocker. Refresh authoritative evidence.')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review readiness confirmation' })).not.toBeInTheDocument()
  })

  it('fails closed for a known but contradictory superseded current contract', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(caseFixture({
      currentContract: contractFixture('SUPERSEDED'),
      workStage: 'READY_TO_CONFIRM',
    }))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Operational evidence unavailable' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review readiness confirmation' })).not.toBeInTheDocument()
  })

  it('refetches and requires explicit re-review after stale version rejection', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        throw new ApiError(409, 'CONTRACT_VERSION_STALE', 'stale', '/contracts', 'now')
      }
      return noContractCase()
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review preparation' }))
    await user.click(screen.getByRole('button', { name: 'Confirm exact operation' }))

    expect(await screen.findByRole('heading', { name: 'Displayed version changed' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Review preparation' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'I reviewed the current contract' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Review preparation' })).toBeVisible())
    expect(postCalls()).toHaveLength(1)
  })
})
