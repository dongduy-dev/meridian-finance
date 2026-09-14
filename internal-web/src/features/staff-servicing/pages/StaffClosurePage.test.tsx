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
import { applicationId, closureResultFixture, terminalAccountFixture } from '../api/contracts.test'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const requestId = '66666666-6666-4666-8666-666666666666'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-14T10:00:00Z',
  userId: '77777777-7777-4777-8777-777777777777', email: 'accounting@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'],
  permissions: ['loan:read', 'loan:account:close'],
}

function renderPage() {
  const router = createTestRouter([`/staff/applications/${applicationId}/closure`])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

function closurePosts() {
  return vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
    String(path).endsWith('/loan-account/closure')
      && (options as RequestInit | undefined)?.method === 'POST')
}

describe('Administrative closure workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(requestId)
  })
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it('has no financial inputs and sends one stable UUID after naming the SETTLED account', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) =>
      (options as RequestInit | undefined)?.method === 'POST'
        ? closureResultFixture()
        : terminalAccountFixture('SETTLED'))
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByRole('button', { name: 'Review administrative closure' })).toBeEnabled()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByText(/does not record another payment or change allocations/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Review administrative closure' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('LA-20260910-000001')
    expect(screen.getByRole('dialog')).toHaveTextContent('SETTLED')
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByRole('heading', { name: 'Administrative closure confirmed' })).toBeVisible()
    expect(closurePosts()).toHaveLength(1)
    expect(closurePosts()[0]?.[1]?.body).toEqual({ requestId })
  })

  it.each([
    ['network', new NetworkError()],
    ['HTTP 503', new ApiError(503, 'SERVICE_UNAVAILABLE', 'unavailable', '/closure', 'now')],
  ])('keeps an unknown %s result digest-only and exactly replays after the account becomes CLOSED', async (_label, firstFailure) => {
    let attempts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        attempts += 1
        if (attempts === 1) throw firstFailure
        return closureResultFixture({ idempotentReplay: true })
      }
      return attempts > 0 ? terminalAccountFixture('CLOSED') : terminalAccountFixture('SETTLED')
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review administrative closure' }))
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByText(/stable request UUID and minimal semantic digest were persisted/i)).toBeVisible()
    expect(closurePosts()).toHaveLength(1)
    const stored = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(stored).toContain(requestId)
    expect(stored).toContain('LOAN_ACCOUNT_ADMINISTRATIVE_CLOSURE')
    expect(stored).not.toContain('accountNumber')
    expect(findUnresolvedOperation('LOAN_ACCOUNT_ADMINISTRATIVE_CLOSURE', applicationId)?.semanticPayload)
      .toBeUndefined()
    await user.click(screen.getByRole('button', { name: 'Retry exact closure' }))
    expect(await screen.findByRole('heading', { name: 'Closure recovered by exact replay' })).toBeVisible()
    expect(closurePosts()).toHaveLength(2)
    expect(closurePosts()[1]?.[1]?.body).toEqual(closurePosts()[0]?.[1]?.body)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
  })

  it('does not offer a different new closure for an already CLOSED account', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(terminalAccountFixture('CLOSED'))
    renderPage()
    expect(await screen.findByText(/Only a fresh, coherent SETTLED account/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeDisabled()
    expect(closurePosts()).toHaveLength(0)
  })

  it('keeps a confirmed closure final when GET reconciliation fails and retries reads only', async () => {
    let posted = false
    let readsFail = false
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        posted = true
        readsFail = true
        return closureResultFixture()
      }
      if (posted && readsFail) throw new NetworkError()
      return terminalAccountFixture(posted ? 'CLOSED' : 'SETTLED')
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review administrative closure' }))
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByText(/Closure is confirmed\. Current account refresh failed/i))
      .toBeVisible()
    expect(screen.getByRole('heading', { name: 'Administrative closure confirmed' })).toBeVisible()
    readsFail = false
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText(/confirmed closure is now reconciled/i)).toBeVisible()
    expect(closurePosts()).toHaveLength(1)
  })

  it('locks closure when settled account evidence is contradictory', async () => {
    const contradictory = terminalAccountFixture('SETTLED')
    contradictory.servicing.totalPaid = 1199
    vi.mocked(api.apiRequest).mockResolvedValue(contradictory)
    renderPage()
    expect(await screen.findByText('Contradictory LoanAccount evidence')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeDisabled()
    expect(closurePosts()).toHaveLength(0)
  })
})
