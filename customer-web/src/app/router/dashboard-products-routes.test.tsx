import { queryClient } from '@/app/providers/query-client'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { ApiError } from '@/lib/api'
import { formatMoney, formatPercentage } from '@/lib/format/presentation'
import { customerAuthResponse, createAuthApiMock, createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

function moneyText(value: number) {
  return formatMoney(value).replace(/\u00a0/g, ' ')
}

const customer = {
  customerId: '22222222-2222-4222-8222-222222222222',
  customerNumber: 'CUS-000000001',
  status: 'ACTIVE',
  verificationStatus: 'VERIFIED',
  profileCompletionStatus: 'COMPLETE',
  primaryActiveBankAccountPresent: true,
  profile: {
    fullName: 'Customer Demo',
    phoneNumber: '0901234567',
    residentialAddress: '1 Meridian Street',
    employmentStatus: 'SALARIED',
    employerName: 'Meridian Partner Co',
    termsConsentAccepted: true,
    dataProcessingConsentAccepted: true,
  },
}

const productDefinitions = {
  SALARY_ADVANCE: {
    productCode: 'SALARY_ADVANCE',
    productType: 'SALARY_BASED',
    name: 'Salary Advance',
    description: null,
    active: true,
    minAmount: 500_000,
    maxAmount: 20_000_000,
    policy: {
      allowedTermsMonths: [1, 2, 3],
      pricing: { flatMonthlyInterestRate: 0.012, feeAmount: 0 },
      interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
      repaymentMethod: 'ON_SALARY_DATE',
      offerValidityDays: 7,
      submissionEvidenceRequirements: [],
      eligibilityNotes: ['Verified employment and current product readiness are required.'],
    },
  },
  UNSECURED_CONSUMER_LOAN: {
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    name: 'Unsecured Consumer Loan',
    description: 'A document-based product reviewed by Meridian.',
    active: true,
    minAmount: 2_000_000,
    maxAmount: 50_000_000,
    policy: {
      allowedTermsMonths: [3, 6, 9, 12],
      pricing: { flatMonthlyInterestRate: 0.018, feeAmount: 0 },
      interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
      repaymentMethod: 'MONTHLY_INSTALLMENT',
      offerValidityDays: 7,
      submissionEvidenceRequirements: [
        { documentType: 'INCOME_PROOF', requirementStatus: 'REQUIRED' },
      ],
      eligibilityNotes: ['Income and employment evidence are required.'],
    },
  },
  COLLATERAL_LOAN: {
    productCode: 'COLLATERAL_LOAN',
    productType: 'SECURED',
    name: 'Collateral Loan',
    description: 'A secured product with manual collateral assessment.',
    active: true,
    minAmount: 5_000_000,
    maxAmount: 100_000_000,
    policy: {
      allowedTermsMonths: [6, 12, 18, 24],
      pricing: { flatMonthlyInterestRate: 0.015, feeAmount: 0 },
      interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
      repaymentMethod: 'MONTHLY_INSTALLMENT',
      offerValidityDays: 7,
      submissionEvidenceRequirements: [
        { documentType: 'COLLATERAL_OWNERSHIP_EVIDENCE', requirementStatus: 'REQUIRED' },
      ],
      eligibilityNotes: ['One structured collateral fact is assessed manually.'],
    },
  },
}

const products = Object.values(productDefinitions)

const salaryAdvanceReadiness = {
  productCode: 'SALARY_ADVANCE',
  customerPartnerEmployeeLinkId: '55555555-5555-4555-8555-555555555551',
  employeeVerificationStatus: 'VERIFIED',
  partnerEligibilityStatus: 'ELIGIBLE',
  limitStatus: 'ACTIVE',
  totalAmount: 5_000_000,
  usedAmount: 500_000,
  reservedAmount: 0,
  availableAmount: 4_500_000,
  lastRefreshAt: '2026-08-30T08:00:00',
  applicationAllowed: true,
  blockerCodes: [],
}

const applications = [
  {
    loanApplicationId: '11111111-1111-4111-8111-111111111111',
    applicationNumber: 'UNKNOWN-ACTIVE-APPLICATION-WITH-A-LONG-REFERENCE-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    requestedAmount: 10_000_000,
    requestedTermMonths: 6,
    status: 'FUTURE_APPLICATION_STATUS_WITH_A_LONG_LABEL',
    submittedAt: '2026-08-30T08:00:00',
    lifecycleActive: true,
    requiredAction: 'NONE',
  },
  {
    loanApplicationId: '11111111-1111-4111-8111-111111111112',
    applicationNumber: 'RETURNED-WITHOUT-CUSTOMER-ACTION',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    requestedAmount: 10_000_000,
    requestedTermMonths: 6,
    status: 'RETURNED_FOR_REVISION',
    submittedAt: '2026-08-30T07:00:00',
    lifecycleActive: true,
    requiredAction: 'NONE',
  },
  {
    loanApplicationId: '11111111-1111-4111-8111-111111111113',
    applicationNumber: 'ACCEPTANCE-PENDING-WITHOUT-CUSTOMER-ACTION',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    requestedAmount: 10_000_000,
    requestedTermMonths: 6,
    status: 'CUSTOMER_ACCEPTANCE_PENDING',
    submittedAt: '2026-08-30T06:00:00',
    lifecycleActive: true,
    requiredAction: 'NONE',
  },
  ...[
    ['22222222-2222-4222-8222-222222222221', 'ACTION-DOCUMENTS', 'UPLOAD_DOCUMENTS'],
    ['22222222-2222-4222-8222-222222222222', 'ACTION-CORRECTIONS', 'COMPLETE_CORRECTIONS'],
    ['22222222-2222-4222-8222-222222222223', 'ACTION-OFFER', 'REVIEW_APPROVED_OFFER'],
    ['22222222-2222-4222-8222-222222222224', 'ACTION-CONTRACT', 'ACKNOWLEDGE_CONTRACT'],
  ].map(([loanApplicationId, applicationNumber, requiredAction]) => ({
    loanApplicationId,
    applicationNumber,
    productCode: 'SALARY_ADVANCE',
    productType: 'SALARY_BASED',
    requestedAmount: 1_000_000,
    requestedTermMonths: 1,
    status: 'REJECTED',
    submittedAt: '2026-08-29T08:00:00',
    lifecycleActive: false,
    requiredAction,
  })),
  {
    loanApplicationId: '22222222-2222-4222-8222-222222222225',
    applicationNumber: 'ACTION-FUTURE',
    productCode: 'SALARY_ADVANCE',
    productType: 'SALARY_BASED',
    requestedAmount: 1_000_000,
    requestedTermMonths: 1,
    status: 'REJECTED',
    submittedAt: '2026-08-28T08:00:00',
    lifecycleActive: false,
    requiredAction: 'FUTURE_CUSTOMER_ACTION',
  },
]

const loanAccounts = [
  {
    loanApplicationId: '33333333-3333-4333-8333-333333333331',
    loanAccountId: '33333333-3333-4333-8333-333333333332',
    accountNumber: 'LOAN-ACCOUNT-WITH-A-LONG-REFERENCE-000001',
    applicationNumber: 'UCL-20260830-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    productType: 'UNSECURED',
    status: 'FUTURE_LOAN_ACCOUNT_STATUS_WITH_A_LONG_LABEL',
    activatedAt: '2026-08-30T09:00:00',
    originatedPrincipal: 99_999_999_999,
    totalPaid: 1_234_567,
    totalOutstanding: 98_765_432_109,
    servicingActive: true,
  },
  {
    loanApplicationId: '44444444-4444-4444-8444-444444444441',
    loanAccountId: '44444444-4444-4444-8444-444444444442',
    accountNumber: 'INACTIVE-SETTLED-ACCOUNT',
    applicationNumber: 'SA-20260820-000001',
    productCode: 'SALARY_ADVANCE',
    productType: 'SALARY_BASED',
    status: 'SETTLED',
    activatedAt: '2026-08-20T09:00:00',
    originatedPrincipal: 1_000_000,
    totalPaid: 1_012_000,
    totalOutstanding: 0,
    servicingActive: false,
  },
]

function response(body: unknown, status = 200, headers?: HeadersInit) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

function errorResponse(path: string, status = 400) {
  return response({
    timestamp: '2026-08-30T08:00:00Z',
    status,
    errorCode: 'QUERY_UNAVAILABLE',
    message: 'This information is temporarily unavailable.',
    path,
  }, status, { 'X-Request-ID': '55555555-5555-4555-8555-555555555555' })
}

function successfulFetch(input: RequestInfo | URL, init?: RequestInit) {
  const url = String(input)
  if (url.endsWith('/customers/me')) return Promise.resolve(response(customer))
  if (url.endsWith('/loan-applications')) return Promise.resolve(response(applications))
  if (url.endsWith('/loan-accounts')) return Promise.resolve(response(loanAccounts))
  if (url.endsWith('/loan-products')) return Promise.resolve(response(products))
  if (url.endsWith('/loan-products/salary-advance/readiness')) return Promise.resolve(response(salaryAdvanceReadiness))
  const productCode = Object.keys(productDefinitions).find((code) => url.endsWith(`/loan-products/${code}`))
  if (productCode) return Promise.resolve(response(productDefinitions[productCode as keyof typeof productDefinitions]))
  throw new Error(`Unexpected request: ${url}; ${init?.method ?? 'GET'}`)
}

function renderRoute(path: string, fetchImplementation: typeof fetch = successfulFetch) {
  vi.stubGlobal('fetch', vi.fn(fetchImplementation))
  const router = createTestRouter([path])
  render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return router
}

afterEach(() => {
  queryClient.clear()
  vi.unstubAllGlobals()
})

describe('FE-CP5 Dashboard', () => {
  it('composes backend readiness, required actions, lifecycle-active applications, servicing-active accounts, and public products', async () => {
    const fetchMock = vi.fn(successfulFetch)
    renderRoute('/', fetchMock)

    expect(await screen.findByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()
    expect(await screen.findByText('Your required profile details are on file.')).toBeVisible()

    const requiredWork = screen.getByRole('heading', { name: 'Required Customer work' }).closest('section') as HTMLElement
    for (const title of [
      'Documents are required',
      'Corrections need attention',
      'An approved offer is ready',
      'Contract acknowledgment is required',
      'Action details unavailable',
    ]) {
      expect(await within(requiredWork).findByText(title)).toBeVisible()
    }
    expect(within(requiredWork).queryByText('UNKNOWN-ACTIVE-APPLICATION-WITH-A-LONG-REFERENCE-000001')).not.toBeInTheDocument()
    expect(within(requiredWork).getAllByRole('link')).toHaveLength(4)
    expect(within(requiredWork).getByRole('link', { name: 'Upload documents' })).toHaveAttribute(
      'href',
      '/applications/22222222-2222-4222-8222-222222222221/documents',
    )
    expect(within(requiredWork).getByRole('link', { name: 'Complete corrections' })).toHaveAttribute(
      'href',
      '/applications/22222222-2222-4222-8222-222222222222/corrections',
    )
    expect(within(requiredWork).getByRole('link', { name: 'Review offer' })).toHaveAttribute(
      'href',
      '/applications/22222222-2222-4222-8222-222222222223/offer',
    )
    expect(within(requiredWork).getByRole('link', { name: 'Review contract' })).toHaveAttribute(
      'href',
      '/applications/22222222-2222-4222-8222-222222222224/contract',
    )

    const activeApplications = screen.getByRole('heading', { name: 'Active applications' }).closest('section') as HTMLElement
    expect(within(activeApplications).getByText('UNKNOWN-ACTIVE-APPLICATION-WITH-A-LONG-REFERENCE-000001')).toBeVisible()
    expect(within(activeApplications).getByText('Status unavailable')).toBeVisible()
    expect(within(activeApplications).getByText('Returned for revision')).toBeVisible()
    expect(within(activeApplications).getByText('Customer acceptance pending')).toBeVisible()
    expect(within(activeApplications).queryByText('Action required')).not.toBeInTheDocument()
    expect(within(activeApplications).queryByText('Offer review required')).not.toBeInTheDocument()
    expect(within(activeApplications).queryByText('ACTION-DOCUMENTS')).not.toBeInTheDocument()

    const activeLoans = screen.getByRole('heading', { name: 'Active LoanAccounts' }).closest('section') as HTMLElement
    expect(within(activeLoans).getAllByText('LOAN-ACCOUNT-WITH-A-LONG-REFERENCE-000001')[0]).toBeVisible()
    expect(within(activeLoans).getByText(moneyText(1_234_567))).toBeVisible()
    expect(within(activeLoans).getByText(moneyText(98_765_432_109))).toBeVisible()
    expect(within(activeLoans).queryByText('INACTIVE-SETTLED-ACCOUNT')).not.toBeInTheDocument()

    expect(await screen.findByRole('heading', { name: 'Salary Advance' })).toBeVisible()

    await waitFor(() => {
      const productRequest = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/loan-products'))
      expect(new Headers(productRequest?.[1]?.headers).get('Authorization')).toBeNull()
      for (const path of ['/customers/me', '/loan-applications', '/loan-accounts']) {
        const protectedRequest = fetchMock.mock.calls.find(([input]) => String(input).endsWith(path))
        expect(new Headers(protectedRequest?.[1]?.headers).get('Authorization')).toBe('Bearer customer-access-token')
      }
    })
  })

  it('keeps successful independent regions visible when application queries fail', async () => {
    renderRoute('/', (input, init) => {
      if (String(input).endsWith('/loan-applications')) {
        return Promise.resolve(errorResponse('/api/v1/loan-applications'))
      }
      return successfulFetch(input, init)
    })

    expect(await screen.findByText('Required Customer work could not be loaded')).toBeVisible()
    expect(await screen.findByText('Your required profile details are on file.')).toBeVisible()
    expect((await screen.findAllByText('LOAN-ACCOUNT-WITH-A-LONG-REFERENCE-000001'))[0]).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Salary Advance' })).toBeVisible()
    expect(screen.getAllByText(/Support reference: 55555555/).length).toBeGreaterThan(0)
  })

  it('replays a rejected protected Dashboard query through the existing session coordinator', async () => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh)
      .mockResolvedValueOnce(customerAuthResponse({ accessToken: 'initial-access-token' }))
      .mockResolvedValueOnce(customerAuthResponse({ accessToken: 'refreshed-access-token' }))
    const manager = new AuthSessionManager(api, vi.fn())
    const seenApplicationTokens: string[] = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/loan-applications')) {
        const token = new Headers(init?.headers).get('Authorization') ?? ''
        seenApplicationTokens.push(token)
        if (token === 'Bearer initial-access-token') {
          return response({
            timestamp: '2026-08-30T08:00:00Z',
            status: 401,
            errorCode: 'TOKEN_EXPIRED',
            message: 'Access token expired.',
            path: '/api/v1/loan-applications',
          }, 401)
        }
        return response(applications)
      }
      return successfulFetch(input, init)
    }))
    const router = createTestRouter(['/'])
    render(<AppProviders router={router} authManager={manager} />)

    expect(await screen.findByText('UNKNOWN-ACTIVE-APPLICATION-WITH-A-LONG-REFERENCE-000001')).toBeVisible()
    expect(seenApplicationTokens).toEqual([
      'Bearer initial-access-token',
      'Bearer refreshed-access-token',
    ])
    expect(api.refresh).toHaveBeenCalledTimes(2)
  })
})

describe('FE-CP5 product catalogue and details', () => {
  it('renders returned catalogue data, including a nullable description, without invented copy', async () => {
    renderRoute('/products')

    expect(await screen.findByRole('heading', { level: 1, name: 'Products' })).toBeVisible()
    for (const product of products) {
      expect(await screen.findByRole('heading', { name: product.name })).toBeVisible()
    }
    expect(screen.queryByText('Finish setting up your account')).not.toBeInTheDocument()
    expect(screen.queryByText(/flexible cash|quick funds|perfect for/i)).not.toBeInTheDocument()
  })

  it('prompts for incomplete basic account readiness using only existing account routes', async () => {
    renderRoute('/products', (input, init) => {
      if (String(input).endsWith('/customers/me')) {
        return Promise.resolve(response({
          ...customer,
          profileCompletionStatus: 'INCOMPLETE',
          primaryActiveBankAccountPresent: false,
        }))
      }
      return successfulFetch(input, init)
    })

    expect(await screen.findByText('Finish setting up your account')).toBeVisible()
    expect(screen.getByText(/does not confirm loan eligibility/i)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Complete profile' })).toHaveAttribute('href', '/account/profile')
    expect(screen.getByRole('link', { name: 'Manage bank accounts' })).toHaveAttribute('href', '/account/bank-accounts')
    expect(await screen.findByRole('heading', { name: 'Salary Advance' })).toBeVisible()
  })

  it('keeps products visible when account readiness cannot be loaded', async () => {
    renderRoute('/products', (input, init) => {
      if (String(input).endsWith('/customers/me')) {
        return Promise.resolve(errorResponse('/api/v1/customers/me'))
      }
      return successfulFetch(input, init)
    })

    expect(await screen.findByText('Account readiness could not be loaded')).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Salary Advance' })).toBeVisible()
  })

  it('renders a real empty catalogue state without synthesizing products', async () => {
    renderRoute('/products', async (input) => {
      if (String(input).endsWith('/customers/me')) return response(customer)
      if (String(input).endsWith('/loan-products')) return response([])
      throw new Error(`Unexpected request: ${String(input)}`)
    })

    expect(await screen.findByText('No products available')).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Salary Advance' })).not.toBeInTheDocument()
  })

  it('keeps page context, support reference, and a working retry when the product query fails', async () => {
    const user = userEvent.setup()
    let reads = 0
    renderRoute('/products', async (input) => {
      if (String(input).endsWith('/customers/me')) return response(customer)
      if (!String(input).endsWith('/loan-products')) throw new Error(`Unexpected request: ${String(input)}`)
      reads += 1
      return reads === 1 ? errorResponse('/api/v1/loan-products') : response(products)
    })

    expect(await screen.findByRole('heading', { level: 1, name: 'Products' })).toBeVisible()
    expect(await screen.findByText('Product catalogue could not be loaded')).toBeVisible()
    expect(screen.getByText(/Support reference: 55555555/)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Salary Advance' })).toBeVisible()
  })

  it.each([
    ['/products/salary-advance', 'SALARY_ADVANCE', 'Salary Advance', 'Verified employment and current product readiness are required.'],
    ['/products/unsecured-consumer-loan', 'UNSECURED_CONSUMER_LOAN', 'Unsecured Consumer Loan', 'Income and employment evidence are required.'],
    ['/products/collateral-loan', 'COLLATERAL_LOAN', 'Collateral Loan', 'One structured collateral fact is assessed manually.'],
  ])('renders authoritative policy at %s', async (path, code, name, note) => {
    renderRoute(path)
    const product = productDefinitions[code as keyof typeof productDefinitions]

    expect(await screen.findByRole('heading', { level: 1, name })).toBeVisible()
    expect((await screen.findAllByText(moneyText(product.minAmount)))[0]).toBeVisible()
    expect(screen.getByText(formatPercentage(product.policy.pricing.flatMonthlyInterestRate))).toBeVisible()
    expect(screen.getByText(note)).toBeVisible()
    if (code === 'SALARY_ADVANCE') {
      expect(screen.getByText('No submission evidence is listed for this product.')).toBeVisible()
      expect(screen.queryByText(/flexible cash|quick funds/i)).not.toBeInTheDocument()
      expect(await screen.findByRole('link', { name: 'Apply for Salary Advance' })).toHaveAttribute('href', '/products/salary-advance/apply')
    } else {
      expect(screen.queryByRole('button', { name: /apply|submit|verify/i })).not.toBeInTheDocument()
    }
  })

  it('renders unknown enum-like policy values neutrally and keeps long returned content usable', async () => {
    const longName = 'A very long Meridian product name that must wrap without hiding authoritative financial values or policy labels'
    const futureProduct = {
      ...productDefinitions.UNSECURED_CONSUMER_LOAN,
      name: longName,
      policy: {
        ...productDefinitions.UNSECURED_CONSUMER_LOAN.policy,
        interestCalculationMethod: 'FUTURE_INTEREST_METHOD',
        repaymentMethod: 'FUTURE_REPAYMENT_METHOD',
        submissionEvidenceRequirements: [
          { documentType: 'FUTURE_DOCUMENT_TYPE', requirementStatus: 'FUTURE_REQUIREMENT_STATUS' },
        ],
      },
    }
    renderRoute('/products/unsecured-consumer-loan', async (input) => {
      if (String(input).endsWith('/loan-products/UNSECURED_CONSUMER_LOAN')) return response(futureProduct)
      throw new Error(`Unexpected request: ${String(input)}`)
    })

    const heading = await screen.findByRole('heading', { name: longName })
    expect(heading).toBeVisible()
    expect(screen.getAllByText('Status unavailable').length).toBeGreaterThanOrEqual(3)
    expect(screen.getByText(moneyText(futureProduct.maxAmount))).toBeVisible()
  })

  it('renders an honest unavailable experience for an unknown product slug', async () => {
    renderRoute('/products/not-a-product')

    expect(await screen.findByRole('heading', { level: 1, name: 'Product not available' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Return to products' })).toHaveAttribute('href', '/products')
  })

  it('focuses the page heading after product navigation', async () => {
    const user = userEvent.setup()
    const router = renderRoute('/products')
    const salaryCard = (await screen.findByRole('heading', { name: 'Salary Advance' })).closest('[class*="rounded-lg"]') as HTMLElement
    await user.click(within(salaryCard).getByRole('link', { name: 'View product details' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/products/salary-advance'))
    const heading = await screen.findByRole('heading', { level: 1, name: 'Salary Advance' })
    expect(heading).toHaveFocus()
  })
})

describe('FE-CP5 route protection', () => {
  it.each(['/', '/products'])('preserves Customer route protection for %s', async (path) => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid refresh token.' }),
    )
    const manager = new AuthSessionManager(api, vi.fn())
    const router = createTestRouter([path])
    render(<AppProviders router={router} authManager={manager} />)

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.state).toEqual({ from: path })
  })
})
