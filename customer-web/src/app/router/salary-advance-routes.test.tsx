import { queryClient } from '@/app/providers/query-client'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { applicationKeys } from '@/features/applications/application-queries'
import type { OwnEmployeeVerification } from '@/features/salary-advance/salary-advance-api'
import {
  manualReviewPollIntervalMs,
  manualReviewRefetchInterval,
  salaryAdvanceKeys,
} from '@/features/salary-advance/salary-advance-queries'
import { ApiError } from '@/lib/api'
import { formatMoney } from '@/lib/format/presentation'
import { customerAuthResponse, createAuthApiMock, createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

const linkId = '55555555-5555-4555-8555-555555555551'
const partnerCompanyId = '66666666-6666-4666-8666-666666666661'
const otherPartnerCompanyId = '66666666-6666-4666-8666-666666666662'

const product = {
  productCode: 'SALARY_ADVANCE',
  productType: 'SALARY_BASED',
  name: 'Salary Advance',
  description: 'A current-policy Salary Advance product.',
  active: true,
  minAmount: 1_000_000,
  maxAmount: 10_000_000,
  policy: {
    allowedTermsMonths: [5, 7],
    pricing: { flatMonthlyInterestRate: 0.012, feeAmount: 0 },
    interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
    repaymentMethod: 'ON_SALARY_DATE',
    offerValidityDays: 7,
    submissionEvidenceRequirements: [],
    eligibilityNotes: ['Verified employment and current product readiness are required.'],
  },
}

const readyReadiness = {
  productCode: 'SALARY_ADVANCE',
  customerPartnerEmployeeLinkId: linkId,
  employeeVerificationStatus: 'VERIFIED',
  partnerEligibilityStatus: 'ELIGIBLE',
  limitStatus: 'ACTIVE',
  totalAmount: 6_000_000,
  usedAmount: 1_000_000,
  reservedAmount: 1_000_000,
  availableAmount: 4_000_000,
  lastRefreshAt: '2026-08-30T08:00:00',
  applicationAllowed: true,
  blockerCodes: [] as string[],
}

const options = [{
  partnerCompanyId,
  companyCode: 'MER-LONG',
  name: 'A Partner Company with a deliberately long Customer-safe display name',
}]

const application = {
  loanApplicationId: '77777777-7777-4777-8777-777777777771',
  applicationNumber: 'SA-20260830-000777',
  customerId: '22222222-2222-4222-8222-222222222222',
  productCode: 'SALARY_ADVANCE',
  productType: 'SALARY_BASED',
  status: 'FUTURE_SUBMITTED_STATUS',
  requestedAmount: 2_000_000,
  requestedTermMonths: 7,
  customerPartnerEmployeeLinkId: linkId,
  productVerificationResult: 'VERIFIED',
  totalLimitSnapshot: 6_000_000,
  usedAmountSnapshot: 1_000_000,
  reservedAmountSnapshot: 3_000_000,
  availableLimitSnapshot: 2_000_000,
  submittedAt: '2026-08-30T10:00:00',
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

const applicationSummary = {
  loanApplicationId: application.loanApplicationId,
  applicationNumber: application.applicationNumber,
  productCode: application.productCode,
  productType: application.productType,
  requestedAmount: application.requestedAmount,
  requestedTermMonths: application.requestedTermMonths,
  status: application.status,
  submittedAt: application.submittedAt,
  lifecycleActive: true,
  requiredAction: 'NONE',
}

function response(body: unknown, status = 200, headers?: HeadersInit) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

function errorResponse(errorCode: string, status: number, path: string) {
  return response({
    timestamp: '2026-08-30T10:00:00Z',
    status,
    errorCode,
    message: 'The current Meridian state does not allow this request.',
    path,
  }, status, { 'X-Request-ID': '88888888-8888-4888-8888-888888888888' })
}

function defaultFetch(input: RequestInfo | URL, init?: RequestInit) {
  const url = String(input)
  if (url.endsWith('/loan-products/SALARY_ADVANCE')) return Promise.resolve(response(product))
  if (url.endsWith('/loan-products/salary-advance/readiness')) return Promise.resolve(response(readyReadiness))
  if (url.endsWith('/partner-companies/verification-options')) return Promise.resolve(response(options))
  if (url.endsWith('/partner-companies/employee-verifications')) return Promise.resolve(response([]))
  if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)) {
    return Promise.resolve(response({
      customerId: customer.customerId,
      partnerCompanyId,
      partnerEmployeeId: '99999999-9999-4999-8999-999999999991',
      customerPartnerEmployeeLinkId: linkId,
      outcome: 'MATCHED_ACTIVE',
      linkStatus: 'VERIFIED',
      manualReviewRequired: false,
    }))
  }
  if (url.endsWith('/loan-applications/salary-advance')) return Promise.resolve(response(application, 201))
  if (url.endsWith('/customers/me')) return Promise.resolve(response(customer))
  if (url.endsWith('/loan-applications')) return Promise.resolve(response([applicationSummary]))
  if (url.endsWith('/loan-accounts')) return Promise.resolve(response([]))
  if (url.endsWith('/loan-products')) return Promise.resolve(response([product]))
  throw new Error(`Unexpected request: ${url}; ${init?.method ?? 'GET'}`)
}

function renderRoute(path: string, fetchImplementation: typeof fetch = defaultFetch) {
  const fetchMock = vi.fn(fetchImplementation)
  vi.stubGlobal('fetch', fetchMock)
  const router = createTestRouter([path])
  const rendered = render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return { fetchMock, router, unmount: rendered.unmount }
}

function moneyText(value: number) {
  return formatMoney(value).replace(/\u00a0/g, ' ')
}

afterEach(() => {
  queryClient.clear()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('FE-CP6 Salary Advance product readiness', () => {
  it('preserves CP5 policy, displays exact returned limit facts, and exposes Apply only from backend readiness', async () => {
    const { fetchMock } = renderRoute('/products/salary-advance')

    expect(await screen.findByRole('heading', { level: 1, name: 'Salary Advance' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Amounts & terms' })).toBeVisible()
    for (const amount of [
      readyReadiness.totalAmount,
      readyReadiness.usedAmount,
      readyReadiness.reservedAmount,
      readyReadiness.availableAmount,
    ]) {
      expect(screen.getAllByText(moneyText(amount)).length).toBeGreaterThan(0)
    }
    expect(screen.getByRole('link', { name: 'Apply for Salary Advance' })).toHaveAttribute('href', '/products/salary-advance/apply')

    await waitFor(() => {
      const publicRequest = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/loan-products/SALARY_ADVANCE'))
      const protectedRequest = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/loan-products/salary-advance/readiness'))
      expect(new Headers(publicRequest?.[1]?.headers).get('Authorization')).toBeNull()
      expect(new Headers(protectedRequest?.[1]?.headers).get('Authorization')).toBe('Bearer customer-access-token')
    })
  })

  it('never exposes Apply when applicationAllowed is false even if returned amounts look sufficient', async () => {
    renderRoute('/products/salary-advance', (input, init) => {
      if (String(input).endsWith('/loan-products/salary-advance/readiness')) {
        return Promise.resolve(response({
          ...readyReadiness,
          totalAmount: 99_000_000,
          availableAmount: 99_000_000,
          applicationAllowed: false,
          blockerCodes: ['BLOCKING_APPLICATION_EXISTS'],
        }))
      }
      return defaultFetch(input, init)
    })

    expect(await screen.findByText('Salary Advance application in progress')).toBeVisible()
    expect(screen.getByText('You already have a Salary Advance application in progress. You can start another after it is no longer active.')).toBeVisible()
    expect(screen.queryByText(/finish or close/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Apply for Salary Advance' })).not.toBeInTheDocument()
  })

  it('fails safe when readiness allows application without a reusable employee link', async () => {
    renderRoute('/products/salary-advance', (input, init) => {
      if (String(input).endsWith('/loan-products/salary-advance/readiness')) {
        return Promise.resolve(response({ ...readyReadiness, customerPartnerEmployeeLinkId: null }))
      }
      return defaultFetch(input, init)
    })

    expect(await screen.findByText('Application cannot be started safely')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Apply for Salary Advance' })).not.toBeInTheDocument()
  })

  it('maps profile and bank blockers only to their existing Customer-owned routes', async () => {
    renderRoute('/products/salary-advance', (input, init) => {
      if (String(input).endsWith('/loan-products/salary-advance/readiness')) {
        return Promise.resolve(response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          applicationAllowed: false,
          blockerCodes: ['PROFILE_INCOMPLETE', 'PRIMARY_BANK_ACCOUNT_REQUIRED'],
        }))
      }
      return defaultFetch(input, init)
    })

    expect(await screen.findByRole('link', { name: 'Complete profile' })).toHaveAttribute('href', '/account/profile')
    expect(screen.getByRole('link', { name: 'Manage bank accounts' })).toHaveAttribute('href', '/account/bank-accounts')
    const readinessSection = screen.getByRole('heading', { name: 'Before you apply' }).closest('section') as HTMLElement
    expect(within(readinessSection).getAllByRole('link')).toHaveLength(2)
  })

  it('performs first-time verification through the Customer-safe selector and refetches readiness before Apply', async () => {
    const user = userEvent.setup()
    let readinessReads = 0
    let submittedBody: unknown
    const { fetchMock, router } = renderRoute('/products/salary-advance', async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        readinessReads += 1
        return response(readinessReads === 1 ? {
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          employeeVerificationStatus: 'NOT_VERIFIED',
          partnerEligibilityStatus: 'NOT_VERIFIED',
          limitStatus: 'UNAVAILABLE',
          totalAmount: 0,
          usedAmount: 0,
          reservedAmount: 0,
          availableAmount: 0,
          lastRefreshAt: null,
          applicationAllowed: false,
          blockerCodes: ['EMPLOYEE_NOT_VERIFIED', 'SALARY_ADVANCE_LIMIT_UNAVAILABLE'],
        } : readyReadiness)
      }
      if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)) {
        submittedBody = JSON.parse(String(init?.body))
      }
      return defaultFetch(input, init)
    })

    expect(await screen.findByRole('heading', { name: 'Verify your employment' })).toBeVisible()
    const select = await screen.findByRole('combobox', { name: /Employer/ })
    expect(within(select).getAllByRole('option')).toHaveLength(2)
    await user.selectOptions(select, partnerCompanyId)
    await user.type(screen.getByRole('textbox', { name: /Employee code/ }), 'PRIVATE-EMPLOYEE-001')
    await user.click(screen.getByRole('button', { name: 'Verify employment' }))

    expect((await screen.findAllByText('Employment verified')).length).toBeGreaterThan(0)
    expect(await screen.findByRole('link', { name: 'Apply for Salary Advance' })).toBeVisible()
    expect(readinessReads).toBeGreaterThanOrEqual(2)
    expect(submittedBody).toEqual({ employeeCode: 'PRIVATE-EMPLOYEE-001' })
    expect(router.state.location.pathname).toBe('/products/salary-advance')
    expect(router.state.location.search).toBe('')
    expect(screen.queryByText('PRIVATE-EMPLOYEE-001')).not.toBeInTheDocument()

    const optionRequest = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/partner-companies/verification-options'))
    expect(new Headers(optionRequest?.[1]?.headers).get('Authorization')).toBe('Bearer customer-access-token')
  })

  it('presents stale evidence as re-verification and never turns manual review into Apply authority', async () => {
    const user = userEvent.setup()
    renderRoute('/products/salary-advance', async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        return response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          partnerEligibilityStatus: 'EVIDENCE_STALE',
          limitStatus: 'STALE',
          applicationAllowed: false,
          blockerCodes: ['SALARY_ADVANCE_ELIGIBILITY_DATA_STALE'],
        })
      }
      if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)) {
        return response({
          customerId: customer.customerId,
          partnerCompanyId,
          partnerEmployeeId: null,
          customerPartnerEmployeeLinkId: null,
          outcome: 'MATCHED_ACTIVE',
          linkStatus: null,
          manualReviewRequired: true,
        })
      }
      return defaultFetch(input, init)
    })

    expect(await screen.findByRole('heading', { name: 'Refresh employment verification' })).toBeVisible()
    expect(screen.getByText('Your employment verification needs to be refreshed before submission.')).toBeVisible()
    expect(screen.queryByText(/employment information changed/i)).not.toBeInTheDocument()
    await user.selectOptions(await screen.findByRole('combobox', { name: /Employer/ }), partnerCompanyId)
    await user.type(screen.getByRole('textbox', { name: /Employee code/ }), 'PRIVATE-EMPLOYEE-002')
    await user.click(screen.getByRole('button', { name: 'Refresh verification' }))

    expect(await screen.findByText("We're reviewing your employment details")).toBeVisible()
    expect(screen.getByText('Action or waiting required')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Apply for Salary Advance' })).not.toBeInTheDocument()
  })

  it.each([
    [
      'MANUAL_REVIEW_APPROVED',
      'Your employment was verified. We updated your application availability.',
    ],
    [
      'MANUAL_REVIEW_REJECTED',
      'We could not verify eligible employment for Salary Advance.',
    ],
  ])('updates a pending manual review from a later %s read without another verification POST', async (
    terminalOutcome,
    terminalDescription,
  ) => {
    const user = userEvent.setup()
    let statusReads = 0
    let verificationPosts = 0
    let resolveStatus!: (value: OwnEmployeeVerification[]) => void
    const laterStatus = new Promise<OwnEmployeeVerification[]>((resolve) => { resolveStatus = resolve })
    renderRoute('/products/salary-advance', async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        return response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          employeeVerificationStatus: 'NOT_VERIFIED',
          partnerEligibilityStatus: 'NOT_VERIFIED',
          limitStatus: 'UNAVAILABLE',
          applicationAllowed: false,
          blockerCodes: ['EMPLOYEE_NOT_VERIFIED'],
        })
      }
      if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)) {
        if (init?.method === 'POST') {
          verificationPosts += 1
          return response({
            customerId: customer.customerId,
            partnerCompanyId,
            partnerEmployeeId: null,
            customerPartnerEmployeeLinkId: null,
            outcome: 'PENDING_MANUAL_REVIEW',
            linkStatus: null,
            manualReviewRequired: true,
          })
        }
      }
      if (url.endsWith('/partner-companies/employee-verifications')) {
        statusReads += 1
        return response(await laterStatus)
      }
      return defaultFetch(input, init)
    })

    await screen.findByRole('heading', { name: 'Verify your employment' })
    await user.selectOptions(await screen.findByRole('combobox', { name: /Employer/ }), partnerCompanyId)
    await user.type(screen.getByRole('textbox', { name: /Employee code/ }), 'GHOST-999')
    await user.click(screen.getByRole('button', { name: 'Verify employment' }))

    expect(await screen.findByText("We're reviewing your employment details")).toBeVisible()
    await waitFor(() => expect(statusReads).toBeGreaterThanOrEqual(1))
    resolveStatus([{
      partnerCompanyId,
      outcome: terminalOutcome,
      manualReviewRequired: false,
    }])

    expect(await screen.findByText(terminalDescription)).toBeVisible()
    expect(statusReads).toBeGreaterThanOrEqual(1)
    expect(verificationPosts).toBe(1)
  })

  it('rediscovers a pending review after remount and later shows rejection without another POST', async () => {
    const user = userEvent.setup()
    let verificationPosts = 0
    let statusReads = 0
    let backendStates: OwnEmployeeVerification[] = []
    const fetchImplementation: typeof fetch = async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        return response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          employeeVerificationStatus: 'NOT_VERIFIED',
          partnerEligibilityStatus: 'NOT_VERIFIED',
          limitStatus: 'UNAVAILABLE',
          applicationAllowed: false,
          blockerCodes: ['EMPLOYEE_NOT_VERIFIED'],
        })
      }
      if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)) {
        verificationPosts += 1
        backendStates = [{
          partnerCompanyId,
          outcome: 'PENDING_MANUAL_REVIEW',
          manualReviewRequired: true,
        }]
        return response({
          customerId: customer.customerId,
          partnerCompanyId,
          partnerEmployeeId: null,
          customerPartnerEmployeeLinkId: null,
          outcome: 'PENDING_MANUAL_REVIEW',
          linkStatus: null,
          manualReviewRequired: true,
        })
      }
      if (url.endsWith('/partner-companies/employee-verifications')) {
        statusReads += 1
        return response(backendStates)
      }
      return defaultFetch(input, init)
    }

    const firstRender = renderRoute('/products/salary-advance', fetchImplementation)
    await screen.findByRole('heading', { name: 'Verify your employment' })
    await user.selectOptions(await screen.findByRole('combobox', { name: /Employer/ }), partnerCompanyId)
    await user.type(screen.getByRole('textbox', { name: /Employee code/ }), 'GHOST-RELOAD')
    await user.click(screen.getByRole('button', { name: 'Verify employment' }))
    expect(await screen.findByText("We're reviewing your employment details")).toBeVisible()

    firstRender.unmount()
    queryClient.clear()
    renderRoute('/products/salary-advance', fetchImplementation)

    expect(await screen.findByText("We're reviewing your employment details")).toBeVisible()
    backendStates = [{
      partnerCompanyId,
      outcome: 'MANUAL_REVIEW_REJECTED',
      manualReviewRequired: false,
    }]
    await queryClient.refetchQueries({ queryKey: salaryAdvanceKeys.ownEmployeeVerifications() })

    expect(await screen.findByText('We could not verify eligible employment for Salary Advance.')).toBeVisible()
    expect(verificationPosts).toBe(1)
    expect(statusReads).toBeGreaterThanOrEqual(2)
    expect(manualReviewRefetchInterval(
      queryClient.getQueryData<OwnEmployeeVerification[]>(salaryAdvanceKeys.ownEmployeeVerifications()),
    )).toBe(false)
  })

  it('shows an existing rejected review on a fresh mount without any verification POST', async () => {
    let verificationPosts = 0
    renderRoute('/products/salary-advance', async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        return response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          employeeVerificationStatus: 'NOT_VERIFIED',
          partnerEligibilityStatus: 'NOT_VERIFIED',
          limitStatus: 'UNAVAILABLE',
          applicationAllowed: false,
          blockerCodes: ['EMPLOYEE_NOT_VERIFIED'],
        })
      }
      if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)) {
        verificationPosts += 1
      }
      if (url.endsWith('/partner-companies/employee-verifications')) {
        return response([{
          partnerCompanyId,
          outcome: 'MANUAL_REVIEW_REJECTED',
          manualReviewRequired: false,
        }])
      }
      return defaultFetch(input, init)
    })

    expect(await screen.findByText('We could not verify eligible employment for Salary Advance.')).toBeVisible()
    expect(screen.getByText(`for ${options[0]!.name}`)).toBeVisible()
    expect(verificationPosts).toBe(0)
  })

  it('keeps a fresh successful verification ahead of unchanged rejection history for the same company', async () => {
    const user = userEvent.setup()
    let verificationPosts = 0
    let statusReads = 0
    const companyOptions = [
      { partnerCompanyId, companyCode: 'AURORA', name: 'Aurora' },
      { partnerCompanyId: otherPartnerCompanyId, companyCode: 'BOREALIS', name: 'Borealis' },
    ]
    const backendStates: OwnEmployeeVerification[] = [
      {
        partnerCompanyId,
        outcome: 'MANUAL_REVIEW_REJECTED',
        manualReviewRequired: false,
      },
      {
        partnerCompanyId: otherPartnerCompanyId,
        outcome: 'PENDING_MANUAL_REVIEW',
        manualReviewRequired: true,
      },
    ]
    const fetchImplementation: typeof fetch = async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        return response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          employeeVerificationStatus: 'NOT_VERIFIED',
          partnerEligibilityStatus: 'NOT_VERIFIED',
          limitStatus: 'UNAVAILABLE',
          applicationAllowed: false,
          blockerCodes: ['EMPLOYEE_NOT_VERIFIED'],
        })
      }
      if (url.endsWith('/partner-companies/verification-options')) {
        return response(companyOptions)
      }
      if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)
        && init?.method === 'POST') {
        verificationPosts += 1
        return response({
          customerId: customer.customerId,
          partnerCompanyId,
          partnerEmployeeId: '99999999-9999-4999-8999-999999999991',
          customerPartnerEmployeeLinkId: linkId,
          outcome: 'MATCHED_ACTIVE',
          linkStatus: 'VERIFIED',
          manualReviewRequired: false,
        })
      }
      if (url.endsWith('/partner-companies/employee-verifications')) {
        statusReads += 1
        return response(backendStates)
      }
      return defaultFetch(input, init)
    }

    const firstRender = renderRoute('/products/salary-advance', fetchImplementation)
    expect(await screen.findByText('We could not verify eligible employment for Salary Advance.')).toBeVisible()
    expect(screen.getByText('for Aurora')).toBeVisible()
    expect(screen.getByText("We're reviewing your employment details")).toBeVisible()
    expect(screen.getByText('for Borealis')).toBeVisible()

    await user.selectOptions(await screen.findByRole('combobox', { name: /Employer/ }), partnerCompanyId)
    await user.type(screen.getByRole('textbox', { name: /Employee code/ }), 'AURORA-CORRECTED')
    await user.click(screen.getByRole('button', { name: 'Verify employment' }))

    expect(await screen.findByText('Your employment was verified. We updated your application availability.')).toBeVisible()
    await waitFor(() => expect(statusReads).toBeGreaterThanOrEqual(2))
    expect(screen.queryByText('We could not verify eligible employment for Salary Advance.')).not.toBeInTheDocument()
    expect(screen.getByText("We're reviewing your employment details")).toBeVisible()
    expect(screen.getByText('for Borealis')).toBeVisible()
    expect(verificationPosts).toBe(1)

    firstRender.unmount()
    queryClient.clear()
    renderRoute('/products/salary-advance', fetchImplementation)

    expect(await screen.findByText('We could not verify eligible employment for Salary Advance.')).toBeVisible()
    expect(screen.queryByText('Your employment was verified. We updated your application availability.')).not.toBeInTheDocument()
    expect(verificationPosts).toBe(1)
  })

  it('polls conservatively only while the authoritative review remains pending', () => {
    expect(manualReviewRefetchInterval([
      {
        partnerCompanyId: '55555555-5555-4555-8555-555555555551',
        outcome: 'MANUAL_REVIEW_REJECTED',
        manualReviewRequired: false,
      },
      {
        partnerCompanyId,
        outcome: 'PENDING_MANUAL_REVIEW',
        manualReviewRequired: true,
      },
    ])).toBe(manualReviewPollIntervalMs)
    expect(manualReviewRefetchInterval([{
      partnerCompanyId,
      outcome: 'MANUAL_REVIEW_APPROVED',
      manualReviewRequired: false,
    }])).toBe(false)
    expect(manualReviewRefetchInterval([{
      partnerCompanyId,
      outcome: 'MANUAL_REVIEW_REJECTED',
      manualReviewRequired: false,
    }])).toBe(false)
    expect(manualReviewRefetchInterval([])).toBe(false)
  })

  it.each([
    ['MATCHED_INACTIVE', 'Employment is not active', 'The verification did not establish active employment for Salary Advance eligibility.'],
    ['NOT_FOUND', 'Employment could not be verified', 'We could not verify employment using the selected employer and employee code.'],
    ['MULTIPLE_MATCHES', 'Employment needs review', 'The verification could not establish one eligible employment record.'],
    ['MANUAL_REVIEW_REJECTED', 'Employment could not be verified', 'We could not verify eligible employment for Salary Advance.'],
    ['FUTURE_OUTCOME', 'Verification result unavailable', "We can't show the verification result right now. Check your application availability before continuing."],
  ])('renders %s safely without internal matching evidence', async (outcome, label, description) => {
    const user = userEvent.setup()
    renderRoute('/products/salary-advance', async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        return response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          employeeVerificationStatus: 'NOT_VERIFIED',
          partnerEligibilityStatus: 'NOT_VERIFIED',
          limitStatus: 'UNAVAILABLE',
          applicationAllowed: false,
          blockerCodes: ['EMPLOYEE_NOT_VERIFIED'],
        })
      }
      if (url.endsWith(`/partner-companies/${partnerCompanyId}/employee-verifications`)) {
        return response({
          customerId: customer.customerId,
          partnerCompanyId,
          partnerEmployeeId: '99999999-9999-4999-8999-999999999991',
          customerPartnerEmployeeLinkId: null,
          outcome,
          linkStatus: null,
          manualReviewRequired: false,
        })
      }
      return defaultFetch(input, init)
    })

    await screen.findByRole('heading', { name: 'Verify your employment' })
    await user.selectOptions(await screen.findByRole('combobox', { name: /Employer/ }), partnerCompanyId)
    await user.type(screen.getByRole('textbox', { name: /Employee code/ }), 'PRIVATE-EMPLOYEE-003')
    await user.click(screen.getByRole('button', { name: 'Verify employment' }))

    expect(await screen.findByText(label)).toBeVisible()
    expect(screen.getByText(description)).toBeVisible()
    expect(screen.queryByText(/update your employment information before trying again/i)).not.toBeInTheDocument()
    expect(screen.queryByText('99999999-9999-4999-8999-999999999991')).not.toBeInTheDocument()
    expect(screen.queryByText('PRIVATE-EMPLOYEE-003')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Apply for Salary Advance' })).not.toBeInTheDocument()
  })

  it('renders unavailable zero placeholders and unknown readiness values neutrally', async () => {
    renderRoute('/products/salary-advance', (input, init) => {
      if (String(input).endsWith('/loan-products/salary-advance/readiness')) {
        return Promise.resolve(response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          employeeVerificationStatus: 'FUTURE_EMPLOYEE_STATUS',
          partnerEligibilityStatus: 'FUTURE_PARTNER_STATUS',
          limitStatus: 'UNAVAILABLE',
          totalAmount: 0,
          usedAmount: 0,
          reservedAmount: 0,
          availableAmount: 0,
          lastRefreshAt: null,
          applicationAllowed: false,
          blockerCodes: ['FUTURE_BLOCKER'],
        }))
      }
      return defaultFetch(input, init)
    })

    expect(await screen.findByText('Limit values are unavailable')).toBeVisible()
    expect(screen.getByText('Verification status unavailable')).toBeVisible()
    expect(screen.getByText('Employment status unavailable')).toBeVisible()
    expect(screen.getByText('Application status unavailable')).toBeVisible()
    const limitCard = screen.getByRole('heading', { name: 'Current Salary Advance limit' }).closest('[class*="rounded-lg"]') as HTMLElement
    expect(within(limitCard).queryByText(moneyText(0))).not.toBeInTheDocument()
  })
})

describe('FE-CP6 focused Salary Advance application', () => {
  it('supports direct protected entry and derives amount and term validation only from returned facts', async () => {
    const user = userEvent.setup()
    renderRoute('/products/salary-advance/apply')

    expect(await screen.findByRole('heading', { level: 1, name: 'Choose your request' })).toBeVisible()
    expect(screen.queryByText('Focused flow template')).not.toBeInTheDocument()
    const termSelect = await screen.findByRole('combobox', { name: /Requested term/ })
    expect(within(termSelect).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Select a term', '5 months', '7 months',
    ])

    await user.type(screen.getByRole('textbox', { name: /Requested amount/ }), '500000')
    await user.selectOptions(termSelect, '5')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    expect(await screen.findByText('Requested amount is below the current product minimum.')).toBeVisible()

    await user.clear(screen.getByRole('textbox', { name: /Requested amount/ }))
    await user.type(screen.getByRole('textbox', { name: /Requested amount/ }), '4500000')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    expect(await screen.findByText('Requested amount exceeds the currently available Salary Advance amount.')).toBeVisible()

    await user.clear(screen.getByRole('textbox', { name: /Requested amount/ }))
    await user.type(screen.getByRole('textbox', { name: /Requested amount/ }), '1000000.5')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    expect(await screen.findByText('Enter a positive whole-VND amount using digits only.')).toBeVisible()
  })

  it('blocks a direct Apply route when backend readiness does not allow submission', async () => {
    renderRoute('/products/salary-advance/apply', (input, init) => {
      if (String(input).endsWith('/loan-products/salary-advance/readiness')) {
        return Promise.resolve(response({
          ...readyReadiness,
          customerPartnerEmployeeLinkId: null,
          applicationAllowed: false,
          blockerCodes: ['BLOCKING_APPLICATION_EXISTS'],
        }))
      }
      return defaultFetch(input, init)
    })

    const heading = await screen.findByRole('heading', { level: 1, name: 'Application cannot be started' })
    expect(heading).toBeVisible()
    await waitFor(() => expect(heading).toHaveFocus())
    expect(screen.queryByLabelText('Requested amount')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit application' })).not.toBeInTheDocument()
  })

  it('preserves in-memory values across Request and Review, uses browser history, and warns only on a real exit', async () => {
    const user = userEvent.setup()
    const { router } = renderRoute('/products/salary-advance/apply')
    await screen.findByRole('heading', { name: 'Choose your request' })

    await user.type(await screen.findByRole('textbox', { name: /Requested amount/ }), '2000000')
    await user.click(screen.getByRole('link', { name: 'Back to product' }))
    expect(await screen.findByRole('dialog', { name: 'Leave this application?' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Stay here' }))
    expect(router.state.location.pathname).toBe('/products/salary-advance/apply')

    await user.selectOptions(screen.getByRole('combobox', { name: /Requested term/ }), '7')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    expect(await screen.findByRole('heading', { name: 'Review your application' })).toBeVisible()
    expect(router.state.location.search).toBe('?step=review')
    expect(router.state.location.search).not.toContain('2000000')
    expect(router.state.location.search).not.toContain(linkId)
    expect(screen.queryByRole('dialog', { name: 'Leave this application?' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Back to request' }))
    expect(await screen.findByRole('heading', { name: 'Choose your request' })).toBeVisible()
    expect(screen.getByRole('textbox', { name: /Requested amount/ })).toHaveValue('2000000')
    expect(screen.getByRole('combobox', { name: /Requested term/ })).toHaveValue('7')
    expect(router.state.location.search).toBe('')
  })

  it('recovers a refreshed review URL without in-memory request data to the Request stage', async () => {
    const { router } = renderRoute('/products/salary-advance/apply?step=review')

    expect(await screen.findByRole('heading', { name: 'Choose your request' })).toBeVisible()
    await waitFor(() => expect(router.state.location.search).toBe(''))
  })

  it('disables duplicate submission, invalidates only affected indexes, and shows persistent safe success', async () => {
    const user = userEvent.setup()
    let resolveSubmission: ((value: Response) => void) | undefined
    const submissionResponse = new Promise<Response>((resolve) => { resolveSubmission = resolve })
    const submittedApplication = { ...application, status: 'SUBMITTED' }
    let submissionCalls = 0
    let submittedBody: unknown
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const { router } = renderRoute('/products/salary-advance/apply', async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-applications/salary-advance')) {
        submissionCalls += 1
        submittedBody = JSON.parse(String(init?.body))
        return submissionResponse
      }
      return defaultFetch(input, init)
    })

    await screen.findByRole('heading', { name: 'Choose your request' })
    await user.type(await screen.findByRole('textbox', { name: /Requested amount/ }), '2000000')
    await user.selectOptions(screen.getByRole('combobox', { name: /Requested term/ }), '7')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    const submitButton = await screen.findByRole('button', { name: 'Submit application' })
    await user.click(submitButton)
    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Submitting…' }))
    expect(submissionCalls).toBe(1)

    resolveSubmission?.(response(submittedApplication, 201))
    expect(await screen.findByRole('heading', { name: 'Application submitted' })).toBeVisible()
    expect(screen.getByText(application.applicationNumber)).toBeVisible()
    expect(screen.getByText('Your application was submitted and the requested amount was reserved against your current Salary Advance limit.')).toBeVisible()
    expect(screen.queryByText(/approved current exposure/i)).not.toBeInTheDocument()
    expect(screen.getByText('Submission confirmed').closest('[role="alert"]')).toHaveClass('bg-success-subtle')
    expect(screen.getByText('Submitted').parentElement).toHaveClass('bg-information-subtle')
    expect(screen.getByText(moneyText(application.requestedAmount))).toBeVisible()
    expect(screen.queryByText(application.loanApplicationId)).not.toBeInTheDocument()
    expect(submittedBody).toEqual({
      customerPartnerEmployeeLinkId: linkId,
      requestedAmount: 2_000_000,
      requestedTermMonths: 7,
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: salaryAdvanceKeys.readiness() })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: applicationKeys.index() })

    await user.click(screen.getByRole('link', { name: 'Return to Dashboard' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(await screen.findByText(application.applicationNumber)).toBeVisible()
  })

  it('retains the request after an authoritative state rejection, refetches readiness, and never auto-resubmits', async () => {
    const user = userEvent.setup()
    let readinessReads = 0
    let submissionCalls = 0
    renderRoute('/products/salary-advance/apply', async (input, init) => {
      const url = String(input)
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        readinessReads += 1
        return response(readinessReads === 1 ? readyReadiness : {
          ...readyReadiness,
          applicationAllowed: false,
          availableAmount: 1_000_000,
          blockerCodes: ['INSUFFICIENT_AVAILABLE_LIMIT'],
        })
      }
      if (url.endsWith('/loan-applications/salary-advance')) {
        submissionCalls += 1
        return errorResponse('INSUFFICIENT_AVAILABLE_LIMIT', 422, '/api/v1/loan-applications/salary-advance')
      }
      return defaultFetch(input, init)
    })

    await screen.findByRole('heading', { name: 'Choose your request' })
    await user.type(await screen.findByRole('textbox', { name: /Requested amount/ }), '2000000')
    await user.selectOptions(screen.getByRole('combobox', { name: /Requested term/ }), '7')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    await user.click(await screen.findByRole('button', { name: 'Submit application' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Application was not submitted' })).toBeVisible()
    expect(screen.getByText('The current available amount is no longer sufficient for this request.')).toBeVisible()
    expect(screen.getByText(moneyText(2_000_000))).toBeVisible()
    expect(screen.getByText('7 months')).toBeVisible()
    expect(screen.getAllByText(moneyText(1_000_000)).length).toBeGreaterThan(0)
    expect(submissionCalls).toBe(1)
    expect(readinessReads).toBeGreaterThanOrEqual(2)
  })

  it.each([
    ['SYSTEM_STATE_CONFLICT', "We couldn't confirm the latest Salary Advance information. Review the latest status and try again if appropriate."],
    ['SALARY_ADVANCE_ELIGIBILITY_DATA_STALE', 'Your employment verification needs to be refreshed before submission.'],
    ['BLOCKING_APPLICATION_EXISTS', 'You already have a Salary Advance application in progress. You can submit another after it is no longer active.'],
  ])('describes %s without inventing changed information or a Customer-controlled resolution', async (errorCode, expectedMessage) => {
    const user = userEvent.setup()
    renderRoute('/products/salary-advance/apply', async (input, init) => {
      if (String(input).endsWith('/loan-applications/salary-advance')) {
        return errorResponse(errorCode, 409, '/api/v1/loan-applications/salary-advance')
      }
      return defaultFetch(input, init)
    })

    await screen.findByRole('heading', { name: 'Choose your request' })
    await user.type(screen.getByRole('textbox', { name: /Requested amount/ }), '2000000')
    await user.selectOptions(screen.getByRole('combobox', { name: /Requested term/ }), '7')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    await user.click(await screen.findByRole('button', { name: 'Submit application' }))

    expect(await screen.findByText(expectedMessage)).toBeVisible()
    expect(screen.queryByText(/application details changed|your information changed|finish or close/i)).not.toBeInTheDocument()
  })

  it('replays a rejected protected readiness request while keeping the product read public', async () => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh)
      .mockResolvedValueOnce(customerAuthResponse({ accessToken: 'initial-access-token' }))
      .mockResolvedValueOnce(customerAuthResponse({ accessToken: 'refreshed-access-token' }))
    const manager = new AuthSessionManager(api, vi.fn())
    const readinessTokens: string[] = []
    const productTokens: (string | null)[] = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/loan-products/SALARY_ADVANCE')) {
        productTokens.push(new Headers(init?.headers).get('Authorization'))
        return response(product)
      }
      if (url.endsWith('/loan-products/salary-advance/readiness')) {
        const token = new Headers(init?.headers).get('Authorization') ?? ''
        readinessTokens.push(token)
        if (token === 'Bearer initial-access-token') {
          return errorResponse('TOKEN_EXPIRED', 401, '/api/v1/loan-products/salary-advance/readiness')
        }
        return response(readyReadiness)
      }
      throw new Error(`Unexpected request: ${url}`)
    }))
    const router = createTestRouter(['/products/salary-advance'])
    render(<AppProviders router={router} authManager={manager} />)

    expect(await screen.findByRole('link', { name: 'Apply for Salary Advance' })).toBeVisible()
    expect(readinessTokens).toEqual(['Bearer initial-access-token', 'Bearer refreshed-access-token'])
    expect(productTokens).toEqual([null])
    expect(api.refresh).toHaveBeenCalledTimes(2)
  })

  it('keeps the focused route behind Customer authentication', async () => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid refresh token.' }),
    )
    const manager = new AuthSessionManager(api, vi.fn())
    const router = createTestRouter(['/products/salary-advance/apply'])
    render(<AppProviders router={router} authManager={manager} />)

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.state).toEqual({ from: '/products/salary-advance/apply' })
  })
})
