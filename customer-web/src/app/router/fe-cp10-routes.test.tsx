import { queryClient } from '@/app/providers/query-client'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { ApiError } from '@/lib/api'
import { clearAccessCredential } from '@/lib/auth/access-credential'
import { formatDateOnly, formatMoney } from '@/lib/format/presentation'
import { createAuthApiMock, createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

const applicationId = '10000000-0000-4000-8000-000000000001'
const accountId = '20000000-0000-4000-8000-000000000001'
const scheduleId = '30000000-0000-4000-8000-000000000001'
const transactionOne = '40000000-0000-4000-8000-000000000001'
const transactionTwo = '40000000-0000-4000-8000-000000000002'
const scheduleItemOne = '50000000-0000-4000-8000-000000000001'

function moneyText(value: number) {
  return formatMoney(value).replace(/\u00a0/g, ' ')
}

const accountSummaries = [
  ['ACTIVE', 'LA-ACTIVE', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'],
  ['OVERDUE', 'LA-OVERDUE', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002'],
  ['SETTLED', 'LA-SETTLED', '10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003'],
  ['CLOSED', 'LA-CLOSED', '10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000004'],
  ['FUTURE_ACCOUNT_STATE', 'LA-FUTURE', '10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000005'],
].map(([status, accountNumber, loanApplicationId, loanAccountId], index) => ({
  loanApplicationId,
  loanAccountId,
  accountNumber,
  applicationNumber: `APP-${index + 1}`,
  productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED',
  status,
  activatedAt: `2026-08-${30 - index}T08:00:00`,
  originatedPrincipal: 10_000_000 - index,
  totalPaid: index * 1_000_000,
  totalOutstanding: 10_000_000 - index * 1_000_000,
  servicingActive: status === 'ACTIVE' || status === 'OVERDUE',
}))

const servicingAmounts = {
  principalPaid: 1_111_111,
  interestPaid: 123_456,
  feePaid: 0,
  totalPaid: 1_234_567,
  principalOutstanding: 8_888_889,
  interestOutstanding: 987_654,
  feeOutstanding: 0,
  totalOutstanding: 9_876_543,
}

const detail = {
  loanApplicationId: applicationId,
  loanAccountId: accountId,
  accountNumber: 'LA-20260901-VERY-LONG-ACCOUNT-NUMBER-000001',
  status: 'ACTIVE',
  activatedAt: '2026-09-01T08:00:00',
  originatedPrincipal: 10_000_000,
  approvedTermMonths: 2,
  totalInterest: 1_111_110,
  totalFee: 0,
  totalRepayment: 12_000_000,
  servicing: {
    ...servicingAmounts,
    servicingEvaluationDate: '2026-09-01',
    lastPaymentValueDate: null,
    lastPaymentRecordedAt: null,
  },
  disbursementDestination: {
    bankCode: 'VCB',
    bankName: 'Vietcombank',
    accountHolderName: 'MERIDIAN CUSTOMER',
    maskedAccountNumber: '********',
  },
  finalRepaymentSchedule: {
    scheduleId,
    scheduleType: 'FINAL',
    version: 1,
    firstDueDate: '2020-01-01',
    lastDueDate: '2026-10-01',
    items: [{
      installmentNumber: 1,
      dueDate: '2020-01-01',
      principalDue: 5_000_000,
      interestDue: 555_555,
      feeDue: 0,
      totalDue: 5_555_555,
      servicing: {
        principalPaid: 0,
        interestPaid: 0,
        feePaid: 0,
        totalPaid: 0,
        principalOutstanding: 5_000_000,
        interestOutstanding: 555_555,
        feeOutstanding: 0,
        totalOutstanding: 5_555_555,
        status: 'NOT_DUE',
        statusEvaluationDate: '2026-09-01',
        lastPaymentValueDate: null,
        lastPaymentRecordedAt: null,
      },
    }, {
      installmentNumber: 2,
      dueDate: '2026-10-01',
      principalDue: 5_000_000,
      interestDue: 555_555,
      feeDue: 0,
      totalDue: 5_555_555,
      servicing: {
        principalPaid: 1_111_111,
        interestPaid: 123_456,
        feePaid: 0,
        totalPaid: 1_234_567,
        principalOutstanding: 3_888_889,
        interestOutstanding: 432_099,
        feeOutstanding: 0,
        totalOutstanding: 4_320_988,
        status: 'FUTURE_INSTALLMENT_STATUS',
        statusEvaluationDate: '2026-09-01',
        lastPaymentValueDate: '2026-08-31',
        lastPaymentRecordedAt: '2026-08-31T09:30:00',
      },
    }],
  },
}

function historyItem(transactionId: string, receivedAmount: number, recordedAt: string) {
  return {
    repaymentTransactionId: transactionId,
    receivedAmount,
    paymentValueDate: '2026-08-31',
    recordedAt,
    principalAllocated: 111_111,
    principalReleased: 0,
    resultingLoanAccountStatus: 'FUTURE_ACCOUNT_STATE',
    accountBalance: {
      principalPaid: 2_000_000,
      interestPaid: 222_222,
      feePaid: 0,
      totalPaid: 2_222_222,
      principalOutstanding: 7_000_000,
      interestOutstanding: 777_777,
      feeOutstanding: 0,
      totalOutstanding: 7_777_777,
      lastPaymentValueDate: '2026-08-31',
      lastPaymentRecordedAt: recordedAt,
      servicingEvaluationDate: '2026-08-31',
      status: 'FUTURE_BALANCE_STATE',
    },
    allocations: [{
      sequence: 1,
      repaymentScheduleItemId: scheduleItemOne,
      installmentNumber: 1,
      component: 'FUTURE_COMPONENT',
      allocatedAmount: receivedAmount,
    }],
    affectedInstallments: [{
      repaymentScheduleItemId: scheduleItemOne,
      installmentNumber: 1,
      dueDate: '2020-01-01',
      previousStatus: 'NOT_DUE',
      resultingStatus: 'FUTURE_INSTALLMENT_STATUS',
      evaluationDate: '2026-08-31',
      principalPaid: 2_000_000,
      interestPaid: 222_222,
      feePaid: 0,
      totalPaid: 2_222_222,
      principalOutstanding: 3_000_000,
      interestOutstanding: 333_333,
      feeOutstanding: 0,
      totalOutstanding: 3_333_333,
      lastPaymentValueDate: '2026-08-31',
      lastPaymentRecordedAt: recordedAt,
      statusChanged: true,
    }],
  }
}

interface Fixture {
  accounts?: unknown
  detail?: unknown
  history?: unknown
  indexError?: Response
  detailError?: Response
  historyError?: Response
  requests: string[]
}

function fixture(overrides: Partial<Fixture> = {}): Fixture {
  return {
    accounts: accountSummaries,
    detail,
    history: {
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      items: [
        historyItem(transactionTwo, 222_222, '2026-08-31T10:00:00'),
        historyItem(transactionOne, 111_111, '2026-08-31T09:00:00'),
      ],
    },
    requests: [],
    ...overrides,
  }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function error(path: string, errorCode: string, status: number) {
  return json({ timestamp: '2026-09-01T10:00:00Z', status, errorCode, message: `Safe ${errorCode} response.`, path }, status)
}

function fixtureFetch(state: Fixture) {
  return async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    state.requests.push(`${method} ${url}`)
    if (method !== 'GET') throw new Error(`Unexpected mutation: ${method} ${url}`)
    if (url.endsWith('/loan-accounts')) return state.indexError ?? json(state.accounts)
    if (url.includes(`/loan-applications/${applicationId}/repayments?`)) return state.historyError ?? json(state.history)
    if (url.endsWith(`/loan-applications/${applicationId}/loan-account`)) return state.detailError ?? json(state.detail)
    throw new Error(`Unexpected request: ${method} ${url}`)
  }
}

function renderRoute(path: string, state = fixture()) {
  vi.stubGlobal('fetch', vi.fn(fixtureFetch(state)))
  const router = createTestRouter([path])
  render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return { router, state }
}

afterEach(() => {
  clearAccessCredential()
  queryClient.clear()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  cleanup()
})

describe('FE-CP10 LoanAccount index', () => {
  it.each(['/loans', `/loans/${applicationId}`])('keeps %s protected', async (path) => {
    clearAccessCredential()
    const authApi = createAuthApiMock()
    vi.mocked(authApi.refresh).mockRejectedValue(new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid session.' }))
    const manager = new AuthSessionManager(authApi, vi.fn())
    const router = createTestRouter([path])
    render(<AppProviders router={router} authManager={manager} />)
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  it('renders loading and API-error states', async () => {
    let resolve!: (response: Response) => void
    const pending = new Promise<Response>((complete) => { resolve = complete })
    vi.stubGlobal('fetch', vi.fn(() => pending))
    const router = createTestRouter(['/loans'])
    render(<AppProviders router={router} authManager={createTestAuthManager()} />)
    expect(await screen.findByLabelText('Loading LoanAccounts')).toBeVisible()
    resolve(error('/api/v1/loan-accounts', 'SYSTEM_STATE_CONFLICT', 409))
    expect(await screen.findByText('LoanAccounts could not be loaded')).toBeVisible()
  })

  it('renders a normal empty state', async () => {
    renderRoute('/loans', fixture({ accounts: [] }))
    expect(await screen.findByText('No LoanAccounts yet')).toBeVisible()
  })

  it('shows all statuses in backend order and navigates cards by loanApplicationId', async () => {
    const user = userEvent.setup()
    const { router } = renderRoute('/loans')
    expect(await screen.findByRole('heading', { name: 'Your LoanAccounts' })).toHaveFocus()
    await screen.findByRole('heading', { name: 'LA-ACTIVE' })
    for (const status of ['Active', 'Overdue', 'Settled', 'Closed', 'Status unavailable']) {
      expect(screen.getByText(status)).toBeVisible()
    }
    const accountHeadings = screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)
    expect(accountHeadings).toEqual(['LA-ACTIVE', 'LA-OVERDUE', 'LA-SETTLED', 'LA-CLOSED', 'LA-FUTURE'])
    await user.click(screen.getAllByRole('link', { name: 'View loan details' })[1]!)
    await waitFor(() => expect(router.state.location.pathname).toBe(`/loans/${accountSummaries[1]!.loanApplicationId}`))
  })
})

describe('FE-CP10 LoanAccount detail', () => {
  it('renders loading and concealed unavailable states', async () => {
    let resolve!: (response: Response) => void
    const pending = new Promise<Response>((complete) => { resolve = complete })
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => String(input).includes('/loan-account') ? pending : json([])))
    const router = createTestRouter([`/loans/${applicationId}`])
    render(<AppProviders router={router} authManager={createTestAuthManager()} />)
    expect(await screen.findByLabelText('Loading LoanAccount detail')).toBeVisible()
    resolve(error(`/api/v1/loan-applications/${applicationId}/loan-account`, 'LOAN_ACCOUNT_NOT_FOUND', 404))
    expect(await screen.findByText('This loan could not be found or is not available to this Customer.')).toBeVisible()
    expect(screen.queryByText(/foreign|inconsistent/i)).not.toBeInTheDocument()
  })

  it('presents exact backend financials, mask, final dates, and nullable payment state without Customer actions', async () => {
    renderRoute(`/loans/${applicationId}`)
    expect(await screen.findByRole('heading', { name: detail.accountNumber })).toHaveFocus()
    expect(screen.getAllByText(moneyText(servicingAmounts.totalPaid)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(moneyText(servicingAmounts.totalOutstanding)).length).toBeGreaterThan(0)
    expect(screen.getByText('********')).toBeVisible()
    expect(screen.queryByText(/6789|full account/i)).not.toBeInTheDocument()
    expect(screen.getByText(`Due ${formatDateOnly('2020-01-01')}`)).toBeVisible()
    expect(screen.getByText(`${formatDateOnly('2020-01-01')} – ${formatDateOnly('2026-10-01')}`)).toBeVisible()
    expect(screen.getAllByText('No payment recorded').length).toBeGreaterThan(0)
    for (const action of [/make payment/i, /record repayment/i, /settle loan/i, /close account/i, /evaluate overdue/i, /reveal account/i, /confirm disbursement/i]) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
    }
    expect(screen.getAllByText(moneyText(1_234_567)).length).toBeGreaterThan(0)
    expect(screen.queryAllByText(moneyText(12_000_000 - 1_234_567))).toHaveLength(0)
  })

  it('uses backend installment servicing even when a due date is in the past and fails safely for unknown status', async () => {
    renderRoute(`/loans/${applicationId}`)
    await screen.findByLabelText('Installment 1 servicing state')
    expect(screen.getByText('Not due')).toBeVisible()
    expect(screen.getByText('Status unavailable')).toBeVisible()
  })
})

describe('FE-CP10 repayment history', () => {
  it('uses URL-backed view/page state, fixed size 20, backend order, and disclosures', async () => {
    const user = userEvent.setup()
    const { router, state } = renderRoute(`/loans/${applicationId}`)
    await user.click(await screen.findByRole('link', { name: 'Repayment history' }))
    await waitFor(() => expect(router.state.location.search).toBe('?tab=repayments&page=0'))
    expect(await screen.findByRole('heading', { name: 'Repayment history' })).toBeVisible()
    expect(state.requests.some((request) => request.endsWith(`/repayments?page=0&size=20`))).toBe(true)
    const receivedLabels = screen.getAllByText('Received amount')
    const receivedAmounts = receivedLabels.map((label) => (
      within(label.parentElement as HTMLElement).getByText(/₫/).textContent?.replace(/\u00a0/g, ' ')
    ))
    expect(receivedAmounts).toEqual([moneyText(222_222), moneyText(111_111)])
    await user.click(screen.getAllByText('Allocation detail')[0]!)
    expect(screen.getAllByText(/Component unavailable/)[0]).toBeVisible()
    await user.click(screen.getAllByText('Installment outcomes')[0]!)
    expect(screen.getAllByText('Status unavailable')[0]).toBeVisible()
    const firstHistoryCard = receivedLabels[0]!.closest('.rounded-lg') as HTMLElement
    expect(within(firstHistoryCard).getByText(moneyText(7_777_777))).toBeVisible()
    expect(within(firstHistoryCard).queryByText(moneyText(servicingAmounts.totalOutstanding))).not.toBeInTheDocument()
    expect(screen.queryByText(/external payment|payment reference/i)).not.toBeInTheDocument()
  })

  it('moves Previous/Next through URL pages and preserves zero-based requests', async () => {
    const user = userEvent.setup()
    const state = fixture({ history: { page: 1, size: 20, totalElements: 45, totalPages: 3, items: [historyItem(transactionOne, 111_111, '2026-08-31T09:00:00')] } })
    const { router } = renderRoute(`/loans/${applicationId}?tab=repayments&page=1`, state)
    expect(await screen.findByText(/Page/)).toHaveTextContent('Page 2')
    await user.click(screen.getByRole('button', { name: 'Previous repayment history page' }))
    await waitFor(() => expect(router.state.location.search).toBe('?tab=repayments&page=0'))
    await user.click(screen.getByRole('button', { name: 'Next repayment history page' }))
    await waitFor(() => expect(router.state.location.search).toBe('?tab=repayments&page=1'))
  })

  it('normalizes invalid URL pages to zero before the request', async () => {
    const { router, state } = renderRoute(`/loans/${applicationId}?tab=repayments&page=-7`)
    await screen.findByRole('heading', { name: 'Repayment history' })
    await waitFor(() => expect(router.state.location.search).toBe('?tab=repayments&page=0'))
    expect(state.requests.some((request) => request.endsWith('/repayments?page=0&size=20'))).toBe(true)
    expect(state.requests.some((request) => request.includes('page=-7'))).toBe(false)
  })

  it('keeps detail context when history fails and handles empty history normally', async () => {
    const first = renderRoute(`/loans/${applicationId}?tab=repayments&page=0`, fixture({ historyError: error('/repayments', 'SYSTEM_STATE_CONFLICT', 409) }))
    expect(await screen.findByText('Repayment history could not be loaded')).toBeVisible()
    expect(screen.getByRole('heading', { name: detail.accountNumber })).toBeVisible()
    first.router.dispose()
    cleanup()
    queryClient.clear()

    renderRoute(`/loans/${applicationId}?tab=repayments&page=0`, fixture({ history: { page: 0, size: 20, totalElements: 0, totalPages: 0, items: [] } }))
    expect(await screen.findByText('No repayments recorded yet')).toBeVisible()
    expect(screen.queryByText(/nothing is owed|no payment required/i)).not.toBeInTheDocument()
  })
})
