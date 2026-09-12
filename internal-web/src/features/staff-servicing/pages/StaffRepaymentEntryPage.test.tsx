import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
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
  repaymentResultFixture,
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
const protectedReference = 'PAYMENT-SENSITIVE-REFERENCE-987'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-10T10:00:00Z',
  userId: '77777777-7777-4777-8777-777777777777', email: 'servicing@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'], permissions: ['loan:read', 'repayment:update'],
}

function renderPage() {
  const queryClient = createQueryClient()
  const router = createTestRouter([`/staff/applications/${applicationId}/repayments/new`])
  const rendered = render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return { ...rendered, queryClient }
}

async function enterEvidence(
  user: ReturnType<typeof userEvent.setup>,
  reference = protectedReference,
) {
  const referenceInput = await screen.findByLabelText('External payment reference')
  await user.clear(referenceInput)
  await user.type(referenceInput, reference)
  const amountInput = screen.getByLabelText('Amount (whole VND)')
  await user.clear(amountInput)
  await user.type(amountInput, '100')
  const dateInput = screen.getByLabelText('Payment value date')
  await user.clear(dateInput)
  await user.type(dateInput, '2026-09-10')
}

function repaymentPostCalls() {
  return vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
    String(path).endsWith('/repayments') && (options as RequestInit | undefined)?.method === 'POST')
}

function standardRead(path: unknown) {
  return String(path).includes('/repayments?') ? historyFixture() : accountFixture()
}

function payoffAccountFixture(status: 'ACTIVE' | 'SETTLED') {
  const account = accountFixture(status)
  const principalPaid = status === 'SETTLED' ? account.originatedPrincipal : account.originatedPrincipal - 100
  const principalOutstanding = account.originatedPrincipal - principalPaid
  const totalPaid = principalPaid + account.totalInterest + account.totalFee
  const totalOutstanding = account.totalRepayment - totalPaid
  const servicing = {
    ...account.servicing,
    principalPaid,
    interestPaid: account.totalInterest,
    feePaid: account.totalFee,
    totalPaid,
    principalOutstanding,
    interestOutstanding: 0,
    feeOutstanding: 0,
    totalOutstanding,
  }
  return {
    ...account,
    servicing,
    finalRepaymentSchedule: {
      ...account.finalRepaymentSchedule,
      items: account.finalRepaymentSchedule.items.map((item) => ({
        ...item,
        servicing: {
          ...item.servicing,
          ...servicing,
          status: status === 'SETTLED' ? 'PAID' : 'PARTIALLY_PAID',
          statusEvaluationDate: item.servicing.statusEvaluationDate,
        },
      })),
    },
  }
}

describe('Staff repayment entry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    localStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(requestId)
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('collects exactly three operator inputs and performs only obvious whole-VND validation', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => standardRead(path))
    renderPage()
    const user = userEvent.setup()

    expect(await screen.findByLabelText('External payment reference')).toBeVisible()
    expect(screen.getByLabelText('Amount (whole VND)')).toBeVisible()
    expect(screen.getByLabelText('Payment value date')).toBeVisible()
    expect(screen.queryByLabelText(/customer|installment|allocation|principal portion|resulting balance/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Evaluate overdue|settlement|closure/i })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Review repayment' }))
    expect(await screen.findByText('Enter the external payment reference.')).toBeVisible()
    expect(screen.getByLabelText('External payment reference')).toHaveFocus()
    await user.type(screen.getByLabelText('External payment reference'), 'ref')
    await user.type(screen.getByLabelText('Amount (whole VND)'), '100.5')
    await user.click(screen.getByRole('button', { name: 'Review repayment' }))
    expect(await screen.findByText('Enter a positive whole-VND amount.')).toBeVisible()
  })

  it.each([
    ['network', new NetworkError()],
    ['HTTP 502', new ApiError(502, 'UPSTREAM_FAILURE', 'unavailable', '/repayments', 'now', 'corr-502')],
  ])('keeps a %s outcome unknown with one POST and digest-only persisted recovery', async (_label, failure) => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/repayments') && (options as RequestInit | undefined)?.method === 'POST') throw failure
      return standardRead(path)
    })
    const { queryClient } = renderPage()
    const user = userEvent.setup()
    await enterEvidence(user, ' payment-sensitive-reference-987 ')
    await user.click(screen.getByRole('button', { name: 'Review repayment' }))
    await user.click(screen.getByRole('button', { name: 'Confirm repayment' }))

    expect(await screen.findByText(/Current account or history changes cannot prove this request identity/i)).toBeVisible()
    expect(repaymentPostCalls()).toHaveLength(1)
    const body = repaymentPostCalls()[0]?.[1]?.body as Record<string, unknown>
    expect(body).toMatchObject({
      requestId,
      externalPaymentReference: protectedReference,
      amount: 100,
      paymentValueDate: '2026-09-10',
    })
    const serialized = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(serialized).toContain('REPAYMENT_RECORDING')
    expect(serialized).toContain(requestId)
    expect(serialized).not.toContain(protectedReference)
    expect(JSON.stringify(localStorage)).not.toContain(protectedReference)
    expect(JSON.stringify(queryClient.getQueryCache().getAll().map((entry) => entry.state.data)))
      .not.toContain(protectedReference)
    expect(findUnresolvedOperation('REPAYMENT_RECORDING', applicationId)?.semanticPayload).toBeUndefined()
  })

  it('uses the same request UUID and canonical payload for explicit exact replay', async () => {
    let attempts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/repayments') && (options as RequestInit | undefined)?.method === 'POST') {
        attempts += 1
        if (attempts === 1) throw new NetworkError()
        return repaymentResultFixture({ idempotentReplay: true })
      }
      return standardRead(path)
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user, ' payment-sensitive-reference-987 ')
    await user.click(screen.getByRole('button', { name: 'Review repayment' }))
    await user.click(screen.getByRole('button', { name: 'Confirm repayment' }))
    await screen.findByRole('button', { name: 'Retry exact operation' })
    await user.click(screen.getByRole('button', { name: 'Retry exact operation' }))

    expect(await screen.findByRole('heading', { name: 'Previously recorded repayment result' })).toBeVisible()
    expect(repaymentPostCalls()).toHaveLength(2)
    const firstBody = repaymentPostCalls()[0]?.[1]?.body
    const secondBody = repaymentPostCalls()[1]?.[1]?.body
    expect(secondBody).toEqual(firstBody)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
    expect(findUnresolvedOperation('REPAYMENT_RECORDING', applicationId)).toBeUndefined()
  })

  it('keeps a lost payoff unknown and allows exact replay after the account becomes SETTLED', async () => {
    let attempts = 0
    let accountStatus: 'ACTIVE' | 'SETTLED' = 'ACTIVE'
    const settledAccount = payoffAccountFixture('SETTLED')
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/repayments') && (options as RequestInit | undefined)?.method === 'POST') {
        attempts += 1
        if (attempts === 1) {
          accountStatus = 'SETTLED'
          throw new NetworkError()
        }
        return repaymentResultFixture({
          idempotentReplay: true,
          resultingLoanAccountStatus: 'SETTLED',
          accountBalance: { ...settledAccount.servicing, status: 'SETTLED' },
        })
      }
      return String(path).includes('/repayments?')
        ? historyFixture()
        : payoffAccountFixture(accountStatus)
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review repayment' }))
    await user.click(screen.getByRole('button', { name: 'Confirm repayment' }))

    expect(await screen.findByText(/Current account or history changes cannot prove this request identity/i)).toBeVisible()
    expect(await screen.findByText(/backend reports SETTLED/i)).toBeVisible()
    const retry = screen.getByRole('button', { name: 'Retry exact operation' })
    expect(retry).toBeEnabled()
    expect(repaymentPostCalls()).toHaveLength(1)

    await user.click(retry)

    expect(await screen.findByRole('heading', { name: 'Previously recorded repayment result' })).toBeVisible()
    expect(repaymentPostCalls()).toHaveLength(2)
    expect(repaymentPostCalls()[1]?.[1]?.body).toEqual(repaymentPostCalls()[0]?.[1]?.body)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
    expect(findUnresolvedOperation('REPAYMENT_RECORDING', applicationId)).toBeUndefined()
    expect(screen.getByRole('button', { name: 'Review repayment' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /settlement|closure/i })).not.toBeInTheDocument()
  })

  it('survives reload without the raw reference and blocks a mismatched candidate without generating R2', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/repayments') && (options as RequestInit | undefined)?.method === 'POST') throw new NetworkError()
      return standardRead(path)
    })
    const first = renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review repayment' }))
    await user.click(screen.getByRole('button', { name: 'Confirm repayment' }))
    await screen.findByRole('button', { name: 'Retry exact operation' })
    first.unmount()

    renderPage()
    const reloadUser = userEvent.setup()
    await enterEvidence(reloadUser, 'DIFFERENT-REFERENCE')
    await reloadUser.click(await screen.findByRole('button', { name: 'Retry exact operation' }))

    expect(await screen.findByText(/does not match the unresolved operation/i)).toBeVisible()
    expect(repaymentPostCalls()).toHaveLength(1)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
    expect(sessionStorage.getItem('meridian.staff.unresolved-operations.v1')).not.toContain(protectedReference)
  })

  it('treats a confirmed POST followed by failed GET refresh as confirmed and retries GET only', async () => {
    let confirmed = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/repayments') && (options as RequestInit | undefined)?.method === 'POST') {
        confirmed = true
        return repaymentResultFixture()
      }
      if (confirmed) throw new NetworkError()
      return standardRead(path)
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review repayment' }))
    await user.click(screen.getByRole('button', { name: 'Confirm repayment' }))

    expect(await screen.findByText(/Repayment confirmed; refreshed state unavailable/i)).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Confirmed repayment result' })).toBeVisible()
    expect(repaymentPostCalls()).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(repaymentPostCalls()).toHaveLength(1)
    expect(screen.getByLabelText('External payment reference')).toHaveValue('')
  })

  it('keeps cached balances visible but disables repayment after a failed authoritative refetch', async () => {
    let readsAvailable = true
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (!readsAvailable) throw new NetworkError()
      return standardRead(path)
    })
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByRole('button', { name: 'Review repayment' })).toBeEnabled()
    expect(await screen.findByText(/Page 1 of 1/)).toBeVisible()

    readsAvailable = false
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByRole('heading', { name: 'Latest account refresh unavailable' })).toBeVisible()
    expect(screen.getByText(/current outstanding/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Review repayment' })).toBeDisabled()

    readsAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Latest account refresh unavailable' })).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Review repayment' })).toBeEnabled()
  })
})
