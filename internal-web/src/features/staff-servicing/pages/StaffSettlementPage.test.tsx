import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError, NetworkError } from '@/lib/api'
import { findUnresolvedOperation } from '@/lib/operation/unresolved-operation'
import { createQueryClient } from '@/lib/query/query-client'
import {
  accountFixture,
  applicationId,
  historyFixture,
  settlementEvidenceFixture,
  settlementResultFixture,
  terminalAccountFixture,
} from '../api/contracts.test'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const requestId = '66666666-6666-4666-8666-666666666666'
const protectedReference = 'SETTLEMENT-SECRET-987'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-14T10:00:00Z',
  userId: '77777777-7777-4777-8777-777777777777', email: 'approver@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['APPROVER'],
  permissions: ['loan:read', 'loan:settlement:approve'],
}

function renderPage() {
  const router = createTestRouter([`/staff/applications/${applicationId}/settlement`])
  return render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

function settlementPosts() {
  return vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
    String(path).endsWith('/settlements') && (options as RequestInit | undefined)?.method === 'POST')
}

function read(path: unknown, account = accountFixture()) {
  if (String(path).includes('/repayments?')) return historyFixture()
  if (String(path).endsWith('/settlements/approved')) return settlementEvidenceFixture()
  return account
}

async function enterEvidence(user: ReturnType<typeof userEvent.setup>, reference = protectedReference) {
  await user.type(await screen.findByLabelText('External payment reference'), reference)
  await user.type(screen.getByLabelText('Payment value date'), '2026-09-10')
}

describe('Administrative Full-Balance Settlement workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    localStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(requestId)
  })
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it('copies the authoritative whole-VND outstanding, exposes only two inputs, and posts after confirmation', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/settlements') && (options as RequestInit | undefined)?.method === 'POST') {
        return settlementResultFixture()
      }
      return read(path)
    })
    renderPage()
    const user = userEvent.setup()
    expect((await screen.findAllByText(/1[.,]100\s*₫|₫\s*1[.,]100/)).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('textbox')).toHaveLength(1)
    expect(screen.queryByLabelText(/settlement amount|discount/i)).not.toBeInTheDocument()
    expect(screen.getByText(/not a discount, concession, waiver, forgiveness, or write-off/i)).toBeVisible()
    await enterEvidence(user, ' settlement-secret-987 ')
    await user.click(screen.getByRole('button', { name: 'Review full-balance settlement' }))
    expect(screen.getByRole('dialog')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Confirm settlement' }))
    expect(await screen.findByRole('heading', { name: 'Settlement confirmed' })).toBeVisible()
    expect(settlementPosts()).toHaveLength(1)
    expect(settlementPosts()[0]?.[1]?.body).toEqual({
      requestId,
      externalPaymentReference: protectedReference,
      expectedSettlementAmount: 1100,
      paymentValueDate: '2026-09-10',
    })
    expect(screen.getByLabelText('External payment reference')).toHaveValue('')
  })

  it.each([
    ['network', new NetworkError()],
    ['HTTP 503', new ApiError(503, 'SERVICE_UNAVAILABLE', 'unavailable', '/settlements', 'now')],
  ])('persists digest-only RESULT_UNKNOWN after %s with one POST', async (_label, failure) => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/settlements') && (options as RequestInit | undefined)?.method === 'POST') throw failure
      return read(path)
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review full-balance settlement' }))
    await user.click(screen.getByRole('button', { name: 'Confirm settlement' }))
    expect(await screen.findByText(/Only the request UUID and SHA-256 semantic digest were persisted/i)).toBeVisible()
    expect(settlementPosts()).toHaveLength(1)
    const stored = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(stored).toContain(requestId)
    expect(stored).toContain('ADMINISTRATIVE_FULL_BALANCE_SETTLEMENT')
    expect(stored).not.toContain(protectedReference)
    expect(stored).not.toContain('expectedSettlementAmount')
    expect(findUnresolvedOperation('ADMINISTRATIVE_FULL_BALANCE_SETTLEMENT', applicationId)?.semanticPayload)
      .toBeUndefined()
  })

  it.each(['SETTLED', 'CLOSED'] as const)('exactly replays the same command after account becomes %s', async (status) => {
    let attempts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/settlements') && (options as RequestInit | undefined)?.method === 'POST') {
        attempts += 1
        if (attempts === 1) throw new NetworkError()
        return settlementResultFixture({ idempotentReplay: true })
      }
      return read(path, attempts > 0 ? terminalAccountFixture(status) : accountFixture())
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review full-balance settlement' }))
    await user.click(screen.getByRole('button', { name: 'Confirm settlement' }))
    const retry = await screen.findByRole('button', { name: 'Retry exact settlement' })
    expect(retry).toBeEnabled()
    await user.click(retry)
    expect(await screen.findByRole('heading', { name: 'Settlement recovered by exact replay' })).toBeVisible()
    expect(settlementPosts()).toHaveLength(2)
    expect(settlementPosts()[1]?.[1]?.body).toEqual(settlementPosts()[0]?.[1]?.body)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
  })

  it('uses safe immutable evidence after reload and blocks a mismatched protected reference', async () => {
    let terminal = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/settlements') && (options as RequestInit | undefined)?.method === 'POST') {
        terminal = true
        throw new NetworkError()
      }
      return read(path, terminal ? terminalAccountFixture('CLOSED') : accountFixture())
    })
    const first = renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review full-balance settlement' }))
    await user.click(screen.getByRole('button', { name: 'Confirm settlement' }))
    await screen.findByRole('button', { name: 'Retry exact settlement' })
    first.unmount()

    renderPage()
    const reloadUser = userEvent.setup()
    await reloadUser.type(await screen.findByLabelText('External payment reference'), 'DIFFERENT-REFERENCE')
    await reloadUser.click(await screen.findByRole('button', { name: 'Retry exact settlement' }))
    expect(await screen.findByText(/does not match the unresolved settlement digest/i)).toBeVisible()
    expect(settlementPosts()).toHaveLength(1)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).endsWith('/settlements/approved'))).toBe(true)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
  })

  it('keeps a confirmed settlement final when GET reconciliation fails and retries reads only', async () => {
    let posted = false
    let readsFail = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/settlements') && (options as RequestInit | undefined)?.method === 'POST') {
        posted = true
        readsFail = true
        return settlementResultFixture()
      }
      if (posted && readsFail) throw new NetworkError()
      return read(path, posted ? terminalAccountFixture('SETTLED') : accountFixture())
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review full-balance settlement' }))
    await user.click(screen.getByRole('button', { name: 'Confirm settlement' }))
    expect(await screen.findByText(
      /Settlement is confirmed\. Current reads could not be reconciled/i,
      undefined,
      { timeout: 3_000 },
    ))
      .toBeVisible()
    expect(screen.getByRole('heading', { name: 'Settlement confirmed' })).toBeVisible()
    readsFail = false
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText(/confirmed settlement is now reconciled/i)).toBeVisible()
    expect(settlementPosts()).toHaveLength(1)
  })

  it('handles a definitive stale-amount conflict without blind retry or losing safe input', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/settlements') && (options as RequestInit | undefined)?.method === 'POST') {
        throw new ApiError(422, 'SETTLEMENT_AMOUNT_INVALID', 'stale amount', '/settlements', 'now')
      }
      return read(path)
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review full-balance settlement' }))
    await user.click(screen.getByRole('button', { name: 'Confirm settlement' }))
    expect(await screen.findByText(/backend definitely rejected the command/i)).toBeVisible()
    expect(screen.getByLabelText('External payment reference')).toHaveValue(protectedReference)
    expect(settlementPosts()).toHaveLength(1)
    expect(findUnresolvedOperation('ADMINISTRATIVE_FULL_BALANCE_SETTLEMENT', applicationId))
      .toBeUndefined()
  })

  it('locks settlement when authoritative account evidence is contradictory', async () => {
    const contradictory = accountFixture()
    contradictory.servicing.totalOutstanding = 1099
    vi.mocked(api.apiRequest).mockImplementation(async (path) => read(path, contradictory))
    renderPage()
    expect(await screen.findByText('Contradictory LoanAccount evidence')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review full-balance settlement' })).toBeDisabled()
    expect(settlementPosts()).toHaveLength(0)
  })
})
