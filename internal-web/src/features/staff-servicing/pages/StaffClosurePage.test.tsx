import { QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
import { applicationId, closureResultFixture, provenanceFixture, terminalAccountFixture } from '../api/contracts.test'
import { formatTimestamp } from '@/lib/format/presentation'
import { staffServicingKeys } from '../api/queries'

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

function renderPage(client = createQueryClient()) {
  const router = createTestRouter([`/staff/applications/${applicationId}/closure`])
  render(<QueryClientProvider client={client}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
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
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) =>
      (options as RequestInit | undefined)?.method === 'POST'
        ? closureResultFixture()
        : String(path).includes('/servicing-provenance?') ? provenanceFixture('SETTLED') : terminalAccountFixture('SETTLED'))
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByRole('button', { name: 'Review administrative closure' })).toBeEnabled()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByText(/records no payment and leaves allocations/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Review administrative closure' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('LA-20260910-000001')
    expect(screen.getByRole('dialog')).toHaveTextContent('Settled')
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByRole('heading', { name: 'Administrative closure confirmed' })).toBeVisible()
    expect(screen.queryByText('CLOSED', { exact: true })).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('heading', { name: /Closure result for account/i })).toHaveFocus())
    expect(closurePosts()).toHaveLength(1)
    expect(closurePosts()[0]?.[1]?.body).toEqual({ requestId })
  })

  it.each([
    ['network', new NetworkError()],
    ['HTTP 503', new ApiError(503, 'SERVICE_UNAVAILABLE', 'unavailable', '/closure', 'now')],
  ])('keeps an unknown %s result digest-only and exactly replays after the account becomes CLOSED', async (_label, firstFailure) => {
    let attempts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        attempts += 1
        if (attempts === 1) throw firstFailure
        return closureResultFixture({ idempotentReplay: true })
      }
      if (String(path).includes('/servicing-provenance?')) return provenanceFixture(attempts > 0 ? 'CLOSED' : 'SETTLED')
      return attempts > 0 ? terminalAccountFixture('CLOSED') : terminalAccountFixture('SETTLED')
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review administrative closure' }))
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByText(/closure result is not confirmed/i)).toBeVisible()
    expect(document.body).not.toHaveTextContent(/\b(?:POST|GET|UUID|SHA-256)\b|payload digest|request identity/i)
    expect(closurePosts()).toHaveLength(1)
    const stored = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(stored).toContain(requestId)
    expect(stored).toContain('LOAN_ACCOUNT_ADMINISTRATIVE_CLOSURE')
    expect(stored).not.toContain('accountNumber')
    expect(findUnresolvedOperation('LOAN_ACCOUNT_ADMINISTRATIVE_CLOSURE', applicationId)?.semanticPayload)
      .toBeUndefined()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText(/Closed by Mina Accounting/)).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Administrative closure confirmed' })).not.toBeInTheDocument()
    expect(screen.getByText('Previous closure result unknown')).toBeVisible()
    expect(closurePosts()).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Retry this closure' }))
    expect(await screen.findByRole('heading', { name: 'Previously recorded closure confirmed' })).toBeVisible()
    expect(closurePosts()).toHaveLength(2)
    expect(closurePosts()[1]?.[1]?.body).toEqual(closurePosts()[0]?.[1]?.body)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
  })

  it('does not offer a different new closure for an already CLOSED account', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).includes('/servicing-provenance?') ? provenanceFixture('CLOSED') : terminalAccountFixture('CLOSED'))
    renderPage()
    expect(await screen.findByText(/confirm that the account is settled and reconciled/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeDisabled()
    expect(closurePosts()).toHaveLength(0)
  })

  it('keeps a confirmed closure final when GET reconciliation fails and retries reads only', async () => {
    let posted = false
    let readsFail = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST') {
        posted = true
        readsFail = true
        return closureResultFixture()
      }
      if (posted && readsFail) throw new NetworkError()
      if (String(path).includes('/servicing-provenance?')) return provenanceFixture(posted ? 'CLOSED' : 'SETTLED')
      return terminalAccountFixture(posted ? 'CLOSED' : 'SETTLED')
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review administrative closure' }))
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByText(
      /Closure is confirmed, but the latest account information is unavailable/i,
      {},
      { timeout: 5_000 },
    ))
      .toBeVisible()
    expect(screen.getByRole('heading', { name: 'Administrative closure confirmed' })).toBeVisible()
    readsFail = false
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText(/closure remains confirmed and the latest account information is now loaded/i)).toBeVisible()
    expect(closurePosts()).toHaveLength(1)
  })

  it('locks closure when settled account evidence is contradictory', async () => {
    const contradictory = terminalAccountFixture('SETTLED')
    contradictory.servicing.totalPaid = 1199
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).includes('/servicing-provenance?') ? provenanceFixture('SETTLED') : contradictory)
    renderPage()
    expect(await screen.findByText('Loan account details need review')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeDisabled()
    expect(closurePosts()).toHaveLength(0)
  })

  it('shows durable disbursement, USER/SYSTEM history, repayment and approved-settlement actors before closure', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).includes('/servicing-provenance?') ? provenanceFixture('SETTLED') : terminalAccountFixture('SETTLED'))
    renderPage()
    expect(await screen.findByText(/Confirmed by Mina Accounting · mina@meridian.local/)).toBeVisible()
    expect(screen.getByText('Overdue evaluated').closest('li')).toHaveTextContent('Meridian system')
    expect(screen.getByText(/Approved by Mina Accounting · mina@meridian.local/)).toBeVisible()
    expect(screen.queryByText(/Closed by/)).not.toBeInTheDocument()
    fireEvent.click(document.querySelector('details summary')!)
    expect(screen.getByText(/Recorded by Mina Accounting · mina@meridian.local/)).toBeVisible()
    expect(screen.getAllByRole('heading', { name: 'Repayment history' })).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeEnabled()
  })

  it('does not invent an approved-settlement actor for contractual payoff', async () => {
    const provenance = provenanceFixture('SETTLED')
    provenance.settlement = null
    provenance.statusHistory[2]!.action = 'REPAYMENT_RECORDED'
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).includes('/servicing-provenance?') ? provenance : terminalAccountFixture('SETTLED'))
    renderPage()
    expect(await screen.findByText(/Confirmed by Mina Accounting/)).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Approved settlement' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Approved by|Closed by/)).not.toBeInTheDocument()
    expect(screen.getByText('Repayment changed account status')).toBeVisible()
  })

  it('refreshes the exact safe closure actor and time after a confirmed command', async () => {
    let posted = false
    const closed = provenanceFixture('CLOSED')
    closed.closure!.actor = { type: 'USER', staff: { ...closed.closure!.actor.staff!, displayName: 'Ari Accounting', email: 'ari.accounting@meridian.local' } }
    closed.statusHistory[3]!.actor = closed.closure!.actor
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (options?.method === 'POST') { posted = true; return closureResultFixture() }
      if (String(path).includes('/servicing-provenance?')) return posted ? closed : provenanceFixture('SETTLED')
      return terminalAccountFixture(posted ? 'CLOSED' : 'SETTLED')
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByText(/Confirmed by Mina Accounting/)
    expect(screen.queryByText(/Closed by/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Review administrative closure' }))
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByText(`Closed by Ari Accounting · ari.accounting@meridian.local · ${formatTimestamp(closed.closure!.at)}`)).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Administrative closure confirmed' })).toBeVisible()
    expect(closurePosts()).toHaveLength(1)
    expect(sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? '').not.toContain('ari.accounting')
  })

  it.each(['application', 'account', 'status'] as const)('suppresses %s-mismatched provenance without changing closure eligibility', async (mismatch) => {
    const provenance = provenanceFixture('SETTLED')
    if (mismatch === 'application') provenance.loanApplicationId = requestId
    if (mismatch === 'account') provenance.loanAccountId = requestId
    if (mismatch === 'status') provenance.statusHistory.at(-1)!.toStatus = 'CLOSED'
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).includes('/servicing-provenance?') ? provenance : terminalAccountFixture('SETTLED'))
    renderPage()
    expect((await screen.findAllByRole('button', { name: 'Try again' })).length).toBeGreaterThan(0)
    expect(screen.queryByText(/Mina Accounting|mina@meridian.local/)).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Administrative closure confirmed' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeEnabled()
  })

  it('suppresses cached actors after a failed refresh and retries only provenance without gating closure', async () => {
    let failing = false
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).includes('/servicing-provenance?')) {
        if (failing) throw new ApiError(409, 'SYSTEM_STATE_CONFLICT', 'unavailable', String(path), 'now')
        return provenanceFixture('SETTLED')
      }
      return terminalAccountFixture('SETTLED')
    })
    const client = createQueryClient()
    renderPage(client)
    await screen.findByText(/Confirmed by Mina Accounting/)
    failing = true
    await act(async () => { await client.refetchQueries({ queryKey: staffServicingKeys.provenance(applicationId, 0, 20) }) })
    await waitFor(() => expect(screen.queryAllByText(/Mina Accounting|mina@meridian.local/)).toHaveLength(0))
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeEnabled()
    const activity = screen.getByRole('heading', { name: 'Account activity' }).parentElement!.parentElement!
    const before = vi.mocked(api.apiRequest).mock.calls.length
    failing = false
    fireEvent.click(within(activity as HTMLElement).getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText(/Confirmed by Mina Accounting/)).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.slice(before).map(([path]) => path)).toEqual([`/staff/loan-applications/${applicationId}/servicing-provenance?page=0&size=20`])
    expect(closurePosts()).toHaveLength(0)
  })

  it('keeps command confirmation final when only the provenance refresh fails', async () => {
    let posted = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (options?.method === 'POST') { posted = true; return closureResultFixture() }
      if (String(path).includes('/servicing-provenance?')) {
        if (posted) throw new ApiError(409, 'SYSTEM_STATE_CONFLICT', 'unavailable', String(path), 'now')
        return provenanceFixture('SETTLED')
      }
      return terminalAccountFixture(posted ? 'CLOSED' : 'SETTLED')
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review administrative closure' }))
    await user.click(screen.getByRole('button', { name: 'Confirm closure' }))
    expect(await screen.findByRole('heading', { name: 'Administrative closure confirmed' })).toBeVisible()
    await screen.findAllByRole('button', { name: 'Try again' })
    expect(screen.queryByText(/Closed by/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Previous closure result unknown|Closure recorded; refresh needed/)).not.toBeInTheDocument()
    expect(closurePosts()).toHaveLength(1)
  })

  it('loads actor context independently of the eligible account and handles empty repayment history', async () => {
    let resolveProvenance!: (value: ReturnType<typeof provenanceFixture>) => void
    vi.mocked(api.apiRequest).mockImplementation(async (path) => String(path).includes('/servicing-provenance?')
      ? new Promise((resolve) => { resolveProvenance = resolve }) : terminalAccountFixture('SETTLED'))
    renderPage()
    expect(await screen.findByText('Loading account activity…')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review administrative closure' })).toBeEnabled()
    const provenance = provenanceFixture('SETTLED')
    provenance.repaymentHistory = { ...provenance.repaymentHistory, totalElements: 0, totalPages: 0, items: [] }
    await act(async () => resolveProvenance(provenance))
    expect(await screen.findByText('No repayments recorded')).toBeVisible()
    expect(screen.getByText(/Confirmed by Mina Accounting/)).toBeVisible()
  })

  it('pages actor-bound repayment rows through the existing provenance query', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (!String(path).includes('/servicing-provenance?')) return terminalAccountFixture('SETTLED')
      const provenance = provenanceFixture('SETTLED')
      provenance.repaymentHistory = { ...provenance.repaymentHistory, page: String(path).includes('page=1') ? 1 : 0, totalElements: 21, totalPages: 2 }
      return provenance
    })
    renderPage()
    await screen.findByText('Page 1 of 2 · 21 payments')
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('Page 2 of 2 · 21 payments')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path) === `/staff/loan-applications/${applicationId}/servicing-provenance?page=1&size=20`)).toBe(true)
  })

  it.each([
    { userType: 'CUSTOMER', customerId: applicationId, roles: ['CUSTOMER'], permissions: ['loan:read:own'] },
    { userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'], permissions: ['loan:account:close'] },
    { userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['loan:read'] },
  ] satisfies Pick<AuthResponse, 'userType' | 'customerId' | 'roles' | 'permissions'>[])('does not load provenance before closure-route authorization: $permissions', async (actor) => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, ...actor })
    renderPage()
    expect(await screen.findByRole('heading', { name: /No operational access|Staff sign in|Loan account access required/i })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('/servicing-provenance'))).toBe(false)
  })
})
