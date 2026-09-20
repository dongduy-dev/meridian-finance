import { QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
import { findUnresolvedOperation } from '@/lib/operation/unresolved-operation'
import { evidenceRecoveryResource } from '../model/recovery'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const caseId = '11111111-1111-4111-8111-111111111111'
const customerId = '22222222-2222-4222-8222-222222222222'
const versionId = '33333333-3333-4333-8333-333333333333'
const loanApplicationId = '77777777-7777-4777-8777-777777777777'

const staff = (permissions = [
  'loan:originate:staff', 'customer:read', 'customer:intake:manage', 'document:upload:intake',
]): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-17T10:00:00Z',
  userId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', email: 'loan.officer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions,
})

const intake = (overrides: Record<string, unknown> = {}) => ({
  assistedOriginationCaseId: caseId, productCode: 'UNSECURED_CONSUMER_LOAN', customerId,
  status: 'OPEN', createdByStaffUserId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  createdAt: '2026-09-17T08:00:00', updatedAt: '2026-09-17T08:00:00', terminalAt: null,
  ...overrides,
})

const customer = {
  customerId, customerNumber: 'CUS-000000123', status: 'ACTIVE', verificationStatus: 'UNVERIFIED',
  profileCompletionStatus: 'COMPLETE', primaryActiveBankAccountPresent: false,
  profile: {
    fullName: 'Paper Customer', phoneNumber: '0900000000', residentialAddress: '1 Meridian Street',
    employmentStatus: 'EMPLOYED', employerName: 'Fictional Employer',
    termsConsentAccepted: true, dataProcessingConsentAccepted: true,
  },
}

const evidence = [{
  intakeDocumentId: '44444444-4444-4444-8444-444444444444', assistedOriginationCaseId: caseId,
  evidenceType: 'UCL_PAPER_APPLICATION', currentVersionId: versionId,
  versions: [{
    intakeDocumentVersionId: versionId, versionNumber: 1, originalFilename: 'application.pdf',
    detectedMimeType: 'application/pdf', byteSize: 128, uploadedAt: '2026-09-17T08:10:00',
  }],
}]

const collateralEvidence = [{
  ...evidence[0],
  evidenceType: 'COLLATERAL_PAPER_APPLICATION',
}]

const uploadedVersion = {
  intakeDocumentVersionId: '66666666-6666-4666-8666-666666666666', versionNumber: 2,
  originalFilename: 'replacement.pdf', detectedMimeType: 'application/pdf', byteSize: 16,
  uploadedAt: '2026-09-17T08:30:00',
}

const ocrJob = (intakeDocumentVersionId: string, disposition = 'REVIEWED') => ({
  ocrJobId: '44444444-4444-4444-8444-444444444445', intakeDocumentVersionId,
  state: 'COMPLETED', disposition, attemptCount: 1, failureCategory: null,
  createdAt: '2026-09-20T08:00:00', updatedAt: '2026-09-20T08:01:00',
  completedAt: '2026-09-20T08:01:00', failedAt: null,
})

const ocrReview = (
  evidenceType: 'CUSTOMER_IDENTITY' | 'UCL_PAPER_APPLICATION' | 'COLLATERAL_PAPER_APPLICATION',
  reviewedFields: Record<string, string>,
  disposition = 'REVIEWED',
) => ({
  ocrResultId: '55555555-5555-4555-8555-555555555555', evidenceType, disposition,
  suggestions: disposition === 'REVIEWED' ? [] : [
    { fieldName: 'fullName', proposedValue: 'OCR Applicant', confidence: 0.95 },
    { fieldName: 'requestedAmount', proposedValue: '9999999', confidence: 0.94 },
  ], reviewedFields, reviewedAt: disposition === 'REVIEWED' ? '2026-09-20T08:05:00' : null,
})

function renderRoute(path: string) {
  const router = createTestRouter([path])
  const view = render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return { router, ...view }
}

function requestPath(call: unknown[]): string { return String(call[0]) }

describe('assisted origination pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    localStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff())
  })

  it('loads the OPEN intake list and presents both supported products without inventing application submission', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue([intake({ customerId: null })])
    renderRoute('/staff/origination')

    expect(await screen.findByRole('heading', { name: 'Paper intake', level: 1 })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Start UCL intake' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Start Collateral intake' })).toBeEnabled()
    expect(await screen.findByText(/Customer not selected/)).toBeVisible()
    expect(screen.queryByRole('button', { name: /submit application/i })).not.toBeInTheDocument()
  })

  it('supports exact Customer search/select, bank mutation, and expected-version evidence replacement', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts` && !options?.method) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence` && !options?.method) return evidence
      if (path === '/staff/customers/search') return customer
      if (path.endsWith('/customer')) return intake()
      if (path === `/staff/customers/${customerId}/bank-accounts`) return {
        customerBankAccountId: '55555555-5555-4555-8555-555555555555', bankCode: 'MER',
        bankNameSnapshot: 'Meridian Bank', accountHolderName: 'Paper Customer', maskedAccountNumber: '******7890',
        accountNumberLastFour: '7890', status: 'ACTIVE', primaryAccount: true,
        createdAt: '2026-09-17T08:20:00', updatedAt: '2026-09-17T08:20:00', deactivatedAt: null,
      }
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) return {
        intakeDocumentVersionId: '66666666-6666-4666-8666-666666666666', versionNumber: 2,
        originalFilename: 'replacement.pdf', detectedMimeType: 'application/pdf', byteSize: 16,
        uploadedAt: '2026-09-17T08:30:00',
      }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByRole('heading', { name: 'UCL intake', level: 1 })).toBeVisible()
    expect(await screen.findByText('Selected Customer · CUS-000000123')).toBeVisible()
    expect(screen.queryByText(/identityReference|fingerprint|ciphertext/i)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Exact value'), 'CUS-000000123')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByRole('button', { name: 'Select Customer' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Select Customer' }))
    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.some((call) =>
      requestPath(call) === `/staff/assisted-originations/${caseId}/customer`
      && (call[1] as { body?: unknown })?.body !== undefined)).toBe(true))

    await user.type(screen.getByLabelText('Bank code'), 'MER')
    await user.type(screen.getByLabelText('Bank name'), 'Meridian Bank')
    await user.type(screen.getByLabelText('Account holder'), 'Paper Customer')
    await user.type(screen.getByLabelText('Account number'), '1234567890')
    await user.click(screen.getByRole('button', { name: 'Add bank account' }))
    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.some((call) =>
      requestPath(call) === `/staff/customers/${customerId}/bank-accounts`
      && (call[1] as { method?: string })?.method === 'POST')).toBe(true))

    expect(await screen.findByRole('button', { name: 'Replace evidence' })).toBeEnabled()
  })

  it('creates and selects a Customer without exposing an Identity User control', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ customerId: null })
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return []
      if (path === '/staff/customers') return customer
      if (path.endsWith('/customer')) return intake()
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByRole('heading', { name: 'Create Customer' })).toBeVisible()
    await user.type(screen.getByLabelText('Full name'), 'Paper Customer')
    await user.type(screen.getByLabelText('Identity reference'), '012345678901')
    await user.type(screen.getByLabelText('Phone'), '0900000000')
    await user.type(screen.getByLabelText('Employment status'), 'EMPLOYED')
    await user.type(screen.getByLabelText('Residential address'), '1 Meridian Street')
    await user.click(screen.getByLabelText('Signed application consent recorded'))
    await user.click(screen.getByLabelText('Data processing consent recorded'))
    await user.click(screen.getByRole('button', { name: 'Create and select Customer' }))

    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.some((call) =>
      requestPath(call) === '/staff/customers' && (call[1] as { method?: string })?.method === 'POST')).toBe(true))
    expect(screen.queryByText(/create identity user|customer login|password/i)).not.toBeInTheDocument()
  })

  it('keeps terminal cases read-only and removes the abandon command', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ status: 'ABANDONED', terminalAt: '2026-09-17T09:00:00' })
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      throw new Error(`Unexpected request ${path}`)
    })
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByText('This intake is terminal. Customer association and evidence upload are disabled.')).toBeVisible()
    expect(await screen.findByRole('button', { name: 'Save profile' })).toBeDisabled()
    expect(await screen.findByRole('button', { name: 'Replace evidence' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Abandon intake' })).not.toBeInTheDocument()
  })

  it('offers UCL conversion only when the authoritative prerequisites are present', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      throw new Error(`Unexpected request ${path}`)
    })
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByRole('heading', { name: 'Create UCL application' })).toBeVisible()
    expect(await screen.findByText('Primary active bank account: present')).toBeVisible()
    expect(await screen.findByText('Signed UCL paper application: present')).toBeVisible()
    expect(screen.getByLabelText('Requested amount')).toBeEnabled()
    expect(screen.getByLabelText('Requested term months')).toBeEnabled()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create UCL application' })).toBeEnabled())
  })

  it('offers all Collateral conversion fields only when authoritative prerequisites are present', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ productCode: 'COLLATERAL_LOAN' })
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return collateralEvidence
      throw new Error(`Unexpected request ${path}`)
    })
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByRole('heading', { name: 'Create Collateral Loan application' })).toBeVisible()
    expect(screen.queryByText('Collateral application creation is not available in this workflow yet.')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Requested amount')).toBeEnabled()
    expect(screen.getByLabelText('Requested term months')).toBeEnabled()
    expect(screen.getByLabelText('Collateral type')).toBeEnabled()
    expect(screen.getByLabelText('Description')).toBeEnabled()
    expect(screen.getByLabelText('Estimated value')).toBeEnabled()
    expect(screen.getByLabelText('Ownership status')).toBeEnabled()
    expect(screen.getByLabelText('Condition note')).toBeEnabled()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create Collateral Loan application' })).toBeEnabled())
    expect(screen.queryByRole('button', { name: 'Create UCL application' })).not.toBeInTheDocument()
  })

  it('keeps Collateral conversion unavailable while readiness or paper evidence is missing', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ productCode: 'COLLATERAL_LOAN' })
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      throw new Error(`Unexpected request ${path}`)
    })
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByText('Primary active bank account: missing')).toBeVisible()
    expect(screen.getByText('Signed Collateral paper application: missing')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Create Collateral Loan application' })).toBeDisabled()
  })

  it('submits exactly one structured Collateral after confirmation and exposes the completed application', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    let posts = 0
    let converted = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}/collateral-loan/submit`) {
        posts += 1
        expect(options?.body).toEqual({
          requestedAmount: 25_000_000,
          requestedTermMonths: 12,
          collateral: {
            type: 'MOTORBIKE', description: '2024 motorbike', estimatedValue: 35_000_000,
            ownershipStatus: 'Owned by Customer', conditionNote: 'Normal used condition',
          },
        })
        expect(options?.body).not.toHaveProperty('customerId')
        expect(options?.body).not.toHaveProperty('originationChannel')
        converted = true
        return intake({ productCode: 'COLLATERAL_LOAN', status: 'COMPLETED', loanApplicationId, terminalAt: '2026-09-17T09:00:00' })
      }
      if (path === `/staff/assisted-originations/${caseId}`) return converted
        ? intake({ productCode: 'COLLATERAL_LOAN', status: 'COMPLETED', loanApplicationId, terminalAt: '2026-09-17T09:00:00' })
        : intake({ productCode: 'COLLATERAL_LOAN' })
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return collateralEvidence
      if (path === '/staff/assisted-originations?status=OPEN') return []
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.type(await screen.findByLabelText('Requested amount'), '25000000')
    await user.type(screen.getByLabelText('Requested term months'), '12')
    await user.type(screen.getByLabelText('Description'), '2024 motorbike')
    await user.type(screen.getByLabelText('Estimated value'), '35000000')
    await user.type(screen.getByLabelText('Ownership status'), 'Owned by Customer')
    await user.type(screen.getByLabelText('Condition note'), 'Normal used condition')
    await user.click(screen.getByRole('button', { name: 'Create Collateral Loan application' }))

    expect(confirm).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('link', { name: 'Open application documents' })).toHaveAttribute(
      'href', `/staff/applications/${loanApplicationId}/documents`,
    )
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeDisabled()
    expect(posts).toBe(1)
  })

  it('reconciles a lost Collateral conversion without automatically repeating the POST', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let caseReads = 0
    let posts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}/collateral-loan/submit`) {
        posts += 1
        throw new NetworkError()
      }
      if (path === `/staff/assisted-originations/${caseId}`) {
        caseReads += 1
        return caseReads === 1 ? intake({ productCode: 'COLLATERAL_LOAN' }) : intake({
          productCode: 'COLLATERAL_LOAN', status: 'COMPLETED', loanApplicationId,
          terminalAt: '2026-09-17T09:00:00',
        })
      }
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return collateralEvidence
      if (path === '/staff/assisted-originations?status=OPEN') return []
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.type(await screen.findByLabelText('Requested amount'), '25000000')
    await user.type(screen.getByLabelText('Requested term months'), '12')
    await user.type(screen.getByLabelText('Description'), '2024 motorbike')
    await user.type(screen.getByLabelText('Estimated value'), '35000000')
    await user.type(screen.getByLabelText('Ownership status'), 'Owned by Customer')
    await user.type(screen.getByLabelText('Condition note'), 'Normal used condition')
    await user.click(screen.getByRole('button', { name: 'Create Collateral Loan application' }))

    expect(await screen.findByRole('link', { name: 'Open application documents' })).toHaveAttribute(
      'href', `/staff/applications/${loanApplicationId}/documents`,
    )
    expect(posts).toBe(1)
  })

  it('submits amount and term once and makes the completed application link authoritative', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let posts = 0
    let converted = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}/unsecured-consumer-loan/submit`) {
        posts += 1
        expect(options?.body).toEqual({ requestedAmount: 10_000_000, requestedTermMonths: 12 })
        converted = true
        return intake({ status: 'COMPLETED', loanApplicationId, terminalAt: '2026-09-17T09:00:00' })
      }
      if (path === `/staff/assisted-originations/${caseId}`) return converted
        ? intake({ status: 'COMPLETED', loanApplicationId, terminalAt: '2026-09-17T09:00:00' })
        : intake()
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      if (path === '/staff/assisted-originations?status=OPEN') return []
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.type(await screen.findByLabelText('Requested amount'), '10000000')
    await user.type(screen.getByLabelText('Requested term months'), '12')
    const submit = screen.getByRole('button', { name: 'Create UCL application' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByRole('link', { name: 'Open application documents' })).toHaveAttribute(
      'href', `/staff/applications/${loanApplicationId}/documents`,
    )
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeDisabled()
    expect(posts).toBe(1)
  })

  it.each([
    ['COMPLETED', loanApplicationId, /confirmed from the authoritative intake/i],
    ['OPEN', null, /requires explicit operator confirmation|review the intake and confirm a new attempt explicitly/i],
  ])('reconciles a lost conversion as %s without automatically repeating POST', async (status, resultId, message) => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let caseReads = 0
    let posts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}/unsecured-consumer-loan/submit`) {
        posts += 1
        throw new NetworkError()
      }
      if (path === `/staff/assisted-originations/${caseId}`) {
        caseReads += 1
        return caseReads === 1 ? intake() : intake({
          status, loanApplicationId: resultId, terminalAt: status === 'COMPLETED' ? '2026-09-17T09:00:00' : null,
        })
      }
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      if (path === '/staff/assisted-originations?status=OPEN') return []
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.type(await screen.findByLabelText('Requested amount'), '10000000')
    await user.type(screen.getByLabelText('Requested term months'), '12')
    const submit = screen.getByRole('button', { name: 'Create UCL application' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    if (status === 'COMPLETED') {
      expect(await screen.findByRole('link', { name: 'Open application documents' })).toHaveAttribute(
        'href', `/staff/applications/${loanApplicationId}/documents`,
      )
    } else {
      expect(await screen.findByText(message)).toBeVisible()
    }
    expect(posts).toBe(1)
  })

  it('does not issue hidden Customer or Document queries without their capabilities', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:originate:staff']))
    vi.mocked(api.apiRequest).mockResolvedValue(intake())
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByRole('heading', { name: 'UCL intake', level: 1 })).toBeVisible()
    expect(screen.getByText(/Customer intake permissions are required/)).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.map(requestPath)).toEqual([`/staff/assisted-originations/${caseId}`])
  })

  it('keeps unfinalized OCR suggestions separate from existing Customer and Loan forms', async () => {
    const ocrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      if (path === ocrBase) return ocrJob(versionId, 'PENDING_REVIEW')
      if (path === `${ocrBase}/review`) return ocrReview('UCL_PAPER_APPLICATION', {}, 'PENDING_REVIEW')
      throw new Error(`Unexpected request ${path}`)
    })
    renderRoute(`/staff/origination/${caseId}`)

    await waitFor(() => expect(screen.getAllByLabelText('Full name')).toHaveLength(2))
    const fullNames = screen.getAllByLabelText('Full name')
    expect(fullNames[0]).toHaveValue('Paper Customer')
    expect(fullNames[1]).toHaveValue('OCR Applicant')
    const requestedAmounts = screen.getAllByLabelText('Requested amount')
    expect(requestedAmounts.find((input) => input.getAttribute('type') === 'number')).toHaveValue(null)
    expect(screen.queryByRole('button', { name: 'Apply reviewed values' })).not.toBeInTheDocument()
  })

  it('explicitly applies reviewed Customer, bank, and UCL values without consent or automatic commands', async () => {
    const ocrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`
    const sensitiveAccount = '1234567890123456'
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      if (path === ocrBase) return ocrJob(versionId)
      if (path === `${ocrBase}/review`) return ocrReview('UCL_PAPER_APPLICATION', {
        fullName: 'Reviewed Customer', identityReference: '999999999999', phoneNumber: '0911222333',
        residentialAddress: '9 Reviewed Street', employmentStatus: 'SELF_EMPLOYED', employerName: 'Reviewed Employer',
        bankCode: 'REV', bankNameSnapshot: 'Reviewed Bank', accountHolderName: 'Reviewed Customer',
        accountNumber: sensitiveAccount, requestedAmount: '15000000', requestedTermMonths: '18',
        termsConsentAccepted: 'false', dataProcessingConsentAccepted: 'false',
      })
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    const apply = await screen.findByRole('button', { name: 'Apply reviewed values' })
    expect(screen.getAllByLabelText('Full name').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('Paper Customer')
    await user.click(apply)

    expect(screen.getAllByLabelText('Full name').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('Reviewed Customer')
    expect(screen.getAllByLabelText('Identity reference').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('')
    expect(screen.getByLabelText('Phone')).toHaveValue('0911222333')
    expect(screen.getByLabelText('Signed application consent recorded')).toBeChecked()
    expect(screen.getByLabelText('Data processing consent recorded')).toBeChecked()
    expect(screen.getAllByLabelText('Bank code').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('REV')
    expect(screen.getAllByLabelText('Bank name').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('Reviewed Bank')
    expect(screen.getAllByLabelText('Account holder').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('Reviewed Customer')
    expect(screen.getAllByLabelText('Account number').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue(sensitiveAccount)
    expect(screen.getAllByLabelText('Requested amount').find((field) => field.getAttribute('type') === 'number')).toHaveValue(15000000)
    expect(screen.getAllByLabelText('Requested term months').find((field) => field.getAttribute('type') === 'number')).toHaveValue(18)
    expect(vi.mocked(api.apiRequest).mock.calls.some((call) => (call[1] as { method?: string } | undefined)?.method === 'POST')).toBe(false)
    expect(sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? '').not.toContain(sensitiveAccount)
    expect(JSON.stringify(localStorage)).not.toContain(sensitiveAccount)
  })

  it('applies reviewed identity to Create Customer while leaving both consent choices unchecked', async () => {
    const identityVersionId = '88888888-8888-4888-8888-888888888888'
    const identityEvidence = [{ ...evidence[0]!, evidenceType: 'CUSTOMER_IDENTITY', currentVersionId: identityVersionId,
      versions: [{ ...evidence[0]!.versions[0]!, intakeDocumentVersionId: identityVersionId }] }]
    const ocrBase = `/staff/assisted-originations/${caseId}/evidence/CUSTOMER_IDENTITY/versions/${identityVersionId}/ocr`
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ customerId: null })
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return identityEvidence
      if (path === ocrBase) return ocrJob(identityVersionId)
      if (path === `${ocrBase}/review`) return ocrReview('CUSTOMER_IDENTITY', {
        fullName: 'New Reviewed Customer', identityReference: '012345678901',
      })
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    await user.click(await screen.findByRole('button', { name: 'Apply reviewed values' }))

    expect(screen.getAllByLabelText('Full name').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('New Reviewed Customer')
    expect(screen.getAllByLabelText('Identity reference').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('012345678901')
    expect(screen.getByLabelText('Signed application consent recorded')).not.toBeChecked()
    expect(screen.getByLabelText('Data processing consent recorded')).not.toBeChecked()
    expect(vi.mocked(api.apiRequest).mock.calls.some((call) => requestPath(call) === '/staff/customers')).toBe(false)
  })

  it('allows reviewed identity reference only for an existing INCOMPLETE Customer profile', async () => {
    const ocrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return { ...customer, profileCompletionStatus: 'INCOMPLETE' }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      if (path === ocrBase) return ocrJob(versionId)
      if (path === `${ocrBase}/review`) return ocrReview('UCL_PAPER_APPLICATION', {
        identityReference: '012345678901',
      })
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    await user.click(await screen.findByRole('button', { name: 'Apply reviewed values' }))

    expect(screen.getAllByLabelText('Identity reference').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('012345678901')
  })

  it('applies all supported Collateral fields and ignores invalid structured values conservatively', async () => {
    const ocrBase = `/staff/assisted-originations/${caseId}/evidence/COLLATERAL_PAPER_APPLICATION/versions/${versionId}/ocr`
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ productCode: 'COLLATERAL_LOAN' })
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return collateralEvidence
      if (path === ocrBase) return ocrJob(versionId)
      if (path === `${ocrBase}/review`) return ocrReview('COLLATERAL_PAPER_APPLICATION', {
        requestedAmount: '25000000', requestedTermMonths: '24', 'collateral.type': 'CAR',
        'collateral.description': 'Reviewed vehicle', 'collateral.estimatedValue': '50000000',
        'collateral.ownershipStatus': 'CUSTOMER_OWNED', 'collateral.conditionNote': 'Good condition',
      })
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.click(await screen.findByRole('button', { name: 'Apply reviewed values' }))

    expect(screen.getAllByLabelText('Requested amount').find((field) => field.getAttribute('type') === 'number')).toHaveValue(25000000)
    expect(screen.getAllByLabelText('Collateral type').find((field) => field.tagName === 'SELECT')).toHaveValue('CAR')
    expect(screen.getAllByLabelText('Description').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('Reviewed vehicle')
    expect(screen.getAllByLabelText('Estimated value').find((field) => field.getAttribute('type') === 'number')).toHaveValue(50000000)
    expect(screen.getAllByLabelText('Ownership status').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('CUSTOMER_OWNED')
    expect(screen.getAllByLabelText('Condition note').find((field) => !(field as HTMLInputElement).readOnly)).toHaveValue('Good condition')
    expect(vi.mocked(api.apiRequest).mock.calls.some((call) => requestPath(call).endsWith('/collateral-loan/submit'))).toBe(false)
  })

  it('does not guess invalid structured OCR values and keeps manual entry usable', async () => {
    const ocrBase = `/staff/assisted-originations/${caseId}/evidence/COLLATERAL_PAPER_APPLICATION/versions/${versionId}/ocr`
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ productCode: 'COLLATERAL_LOAN' })
      if (path === `/staff/customers/${customerId}`) return { ...customer, primaryActiveBankAccountPresent: true }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return collateralEvidence
      if (path === ocrBase) return ocrJob(versionId)
      if (path === `${ocrBase}/review`) return ocrReview('COLLATERAL_PAPER_APPLICATION', {
        requestedAmount: 'not-a-number', requestedTermMonths: '12 months',
        'collateral.type': 'BICYCLE', 'collateral.estimatedValue': '999999999999999999999',
      })
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    const numericAmount = (await screen.findAllByLabelText('Requested amount')).find((field) => field.getAttribute('type') === 'number')!
    await user.type(numericAmount, '123')
    const collateralType = screen.getAllByLabelText('Collateral type').find((field) => field.tagName === 'SELECT')!
    await user.selectOptions(collateralType, 'CAR')
    await user.click(screen.getByRole('button', { name: 'Apply reviewed values' }))

    expect(numericAmount).toHaveValue(123)
    expect(screen.getAllByLabelText('Requested term months').find((field) => field.getAttribute('type') === 'number')).toHaveValue(null)
    expect(collateralType).toHaveValue('CAR')
    expect(screen.getAllByLabelText('Estimated value').find((field) => field.getAttribute('type') === 'number')).toHaveValue(null)
    const description = screen.getAllByLabelText('Description').find((field) => !(field as HTMLInputElement).readOnly)!
    await user.type(description, 'Manual description')
    expect(description).toHaveValue('Manual description')
  })

  it('treats an empty reviewed map as a no-op and keeps manual UCL entry usable', async () => {
    const ocrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      if (path === ocrBase) return ocrJob(versionId)
      if (path === `${ocrBase}/review`) return ocrReview('UCL_PAPER_APPLICATION', {})
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.click(await screen.findByRole('button', { name: 'Apply reviewed values' }))
    expect(screen.getByText(/No supported reviewed values were available/i)).toBeVisible()
    const amount = screen.getAllByLabelText('Requested amount').find((field) => field.getAttribute('type') === 'number')!
    await user.type(amount, '9000000')
    expect(amount).toHaveValue(9000000)
  })

  it('lets a later explicit reviewed source replace overlapping unsaved values without hidden priority', async () => {
    const identityVersionId = '88888888-8888-4888-8888-888888888888'
    const bothEvidence = [
      { ...evidence[0]!, evidenceType: 'CUSTOMER_IDENTITY', currentVersionId: identityVersionId,
        versions: [{ ...evidence[0]!.versions[0]!, intakeDocumentVersionId: identityVersionId }] },
      evidence[0]!,
    ]
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return bothEvidence
      if (path.includes(`/CUSTOMER_IDENTITY/versions/${identityVersionId}/ocr/review`)) return ocrReview('CUSTOMER_IDENTITY', { fullName: 'Identity Review' })
      if (path.includes(`/CUSTOMER_IDENTITY/versions/${identityVersionId}/ocr`)) return ocrJob(identityVersionId)
      if (path.includes(`/UCL_PAPER_APPLICATION/versions/${versionId}/ocr/review`)) return ocrReview('UCL_PAPER_APPLICATION', { fullName: 'Application Review' })
      if (path.includes(`/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`)) return ocrJob(versionId)
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    const buttons = await screen.findAllByRole('button', { name: 'Apply reviewed values' })
    const editableName = screen.getAllByLabelText('Full name').find((field) => !(field as HTMLInputElement).readOnly)
    await user.click(buttons[0]!)
    expect(editableName).toHaveValue('Identity Review')
    await user.click(buttons[1]!)
    expect(editableName).toHaveValue('Application Review')
  })

  it('uses an intervening Staff edit as the restoration baseline for a later OCR source', async () => {
    const identityVersionId = '88888888-8888-4888-8888-888888888888'
    const identityEvidence = { ...evidence[0]!, evidenceType: 'CUSTOMER_IDENTITY', currentVersionId: identityVersionId,
      versions: [{ ...evidence[0]!.versions[0]!, intakeDocumentVersionId: identityVersionId }] }
    const replacementApplicationEvidence = { ...evidence[0]!, currentVersionId: uploadedVersion.intakeDocumentVersionId,
      versions: [...evidence[0]!.versions, uploadedVersion] }
    const identityOcrBase = `/staff/assisted-originations/${caseId}/evidence/CUSTOMER_IDENTITY/versions/${identityVersionId}/ocr`
    const applicationOcrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`
    const replacementOcrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${uploadedVersion.intakeDocumentVersionId}/ocr`
    let replaced = false
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) {
        return [identityEvidence, replaced ? replacementApplicationEvidence : evidence[0]!]
      }
      if (path === identityOcrBase) return ocrJob(identityVersionId)
      if (path === `${identityOcrBase}/review`) return ocrReview('CUSTOMER_IDENTITY', { fullName: 'Identity Review' })
      if (path === applicationOcrBase) return ocrJob(versionId)
      if (path === `${applicationOcrBase}/review`) return ocrReview('UCL_PAPER_APPLICATION', { fullName: 'Application Review' })
      if (path === replacementOcrBase) throw new ApiError(404, 'OCR_JOB_NOT_FOUND', 'Missing', path, '2026-09-20T08:00:00Z')
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) { replaced = true; return uploadedVersion }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    const buttons = await screen.findAllByRole('button', { name: 'Apply reviewed values' })
    const editableName = screen.getAllByLabelText('Full name').find((field) => !(field as HTMLInputElement).readOnly)!
    await user.click(buttons[0]!)
    expect(editableName).toHaveValue('Identity Review')
    await user.clear(editableName)
    await user.type(editableName, 'Staff Corrected Customer')
    await user.click(buttons[1]!)
    expect(editableName).toHaveValue('Application Review')

    const file = new File(['replacement'], 'replacement.pdf', { type: 'application/pdf' })
    const fileInput = screen.getByLabelText('Signed paper application file')
    await user.upload(fileInput, file)
    fireEvent.submit(fileInput.closest('form')!)

    await waitFor(() => expect(replaced).toBe(true))
    expect(await screen.findByText(new RegExp(uploadedVersion.intakeDocumentVersionId))).toBeVisible()
    expect(editableName).toHaveValue('Staff Corrected Customer')
  })

  it('does not restore pre-OCR profile data after the reviewed value is authoritatively saved', async () => {
    const oldOcrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`
    const newOcrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${uploadedVersion.intakeDocumentVersionId}/ocr`
    const savedCustomer = { ...customer, profile: { ...customer.profile, fullName: 'Reviewed Customer' } }
    const replacementEvidence = [{ ...evidence[0]!, currentVersionId: uploadedVersion.intakeDocumentVersionId,
      versions: [...evidence[0]!.versions, uploadedVersion] }]
    let profilePuts = 0
    let saved = false
    let replaced = false
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return saved ? savedCustomer : customer
      if (path === `/staff/customers/${customerId}/profile` && options?.method === 'PUT') {
        profilePuts += 1
        saved = true
        return savedCustomer
      }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return replaced ? replacementEvidence : evidence
      if (path === oldOcrBase) return ocrJob(versionId)
      if (path === `${oldOcrBase}/review`) return ocrReview('UCL_PAPER_APPLICATION', { fullName: 'Reviewed Customer' })
      if (path === newOcrBase) throw new ApiError(404, 'OCR_JOB_NOT_FOUND', 'Missing', path, '2026-09-20T08:00:00Z')
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) { replaced = true; return uploadedVersion }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)

    await user.click(await screen.findByRole('button', { name: 'Apply reviewed values' }))
    const editableName = screen.getAllByLabelText('Full name').find((field) => !(field as HTMLInputElement).readOnly)!
    expect(editableName).toHaveValue('Reviewed Customer')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByText('Operation confirmed')).toBeVisible()
    expect(profilePuts).toBe(1)

    const file = new File(['replacement'], 'replacement.pdf', { type: 'application/pdf' })
    const fileInput = screen.getByLabelText('Signed paper application file')
    await user.upload(fileInput, file)
    fireEvent.submit(fileInput.closest('form')!)

    await waitFor(() => expect(replaced).toBe(true))
    expect(await screen.findByText(new RegExp(uploadedVersion.intakeDocumentVersionId))).toBeVisible()
    expect(editableName).toHaveValue('Reviewed Customer')
    expect(editableName).not.toHaveValue('Paper Customer')
    expect(profilePuts).toBe(1)
  })

  it('removes untouched OCR-applied values when the source evidence version is replaced', async () => {
    const oldOcrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`
    const newOcrBase = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${uploadedVersion.intakeDocumentVersionId}/ocr`
    let replaced = false
    const replacementEvidence = [{ ...evidence[0]!, currentVersionId: uploadedVersion.intakeDocumentVersionId,
      versions: [...evidence[0]!.versions, uploadedVersion] }]
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return replaced ? replacementEvidence : evidence
      if (path === oldOcrBase) return ocrJob(versionId)
      if (path === `${oldOcrBase}/review`) return ocrReview('UCL_PAPER_APPLICATION', { requestedAmount: '17000000' })
      if (path === newOcrBase) throw new ApiError(404, 'OCR_JOB_NOT_FOUND', 'Missing', path, '2026-09-20T08:00:00Z')
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) { replaced = true; return uploadedVersion }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.click(await screen.findByRole('button', { name: 'Apply reviewed values' }))
    const amount = screen.getAllByLabelText('Requested amount').find((field) => field.getAttribute('type') === 'number')!
    expect(amount).toHaveValue(17000000)

    const file = new File(['replacement'], 'replacement.pdf', { type: 'application/pdf' })
    const fileInput = screen.getByLabelText('Signed paper application file')
    await user.upload(fileInput, file)
    fireEvent.submit(fileInput.closest('form')!)

    await waitFor(() => expect(replaced).toBe(true))
    expect(await screen.findByText(new RegExp(uploadedVersion.intakeDocumentVersionId))).toBeVisible()
    expect(await screen.findByText(/values from a replaced evidence version were removed/i)).toBeVisible()
    expect(amount).toHaveValue(null)
  })

  it('renders a safe retryable load error without exposing backend detail', async () => {
    vi.mocked(api.apiRequest).mockRejectedValue(new ApiError(
      503, 'SERVICE_UNAVAILABLE', 'unsafe database detail', `/staff/assisted-originations/${caseId}`,
      '2026-09-17T08:00:00Z', '77777777-7777-4777-8777-777777777777',
    ))
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByRole('heading', { name: 'Intake unavailable' }, { timeout: 5_000 })).toBeVisible()
    expect(screen.queryByText('unsafe database detail')).not.toBeInTheDocument()
  })

  it('reuses one upload request identity and the original baseline after a lost response', async () => {
    const uploads: FormData[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return uploads.length ? evidence : []
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) {
        uploads.push((options as { body: FormData }).body)
        if (uploads.length === 1) throw new NetworkError()
        return uploadedVersion
      }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    const input = await screen.findByLabelText('Signed paper application file')
    const file = new File(['%PDF-same-logical-upload'], 'application.pdf', { type: 'application/pdf' })
    await user.upload(input, file)
    fireEvent.submit(input.closest('form')!)
    await waitFor(() => expect(uploads).toHaveLength(1))
    expect(await screen.findByText(/upload result is unknown/i)).toBeVisible()
    fireEvent.submit(input.closest('form')!)
    await waitFor(() => expect(uploads).toHaveLength(2))

    expect(uploads[0]!.get('uploadRequestId')).toBe(uploads[1]!.get('uploadRequestId'))
    expect(uploads[0]!.get('expectedCurrentVersionId')).toBeNull()
    expect(uploads[1]!.get('expectedCurrentVersionId')).toBeNull()
    expect(findUnresolvedOperation(
      'INTAKE_EVIDENCE_UPLOAD', evidenceRecoveryResource(caseId, 'UCL_PAPER_APPLICATION'),
    )).toBeUndefined()
    expect(await screen.findByText('Operation confirmed')).toBeVisible()
  })

  it('restores an actor-bound unresolved upload after refresh without persisting the file', async () => {
    const uploads: FormData[] = []
    let evidenceReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) {
        evidenceReads += 1
        return evidenceReads === 1 ? [] : evidence
      }
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) {
        uploads.push((options as { body: FormData }).body)
        if (uploads.length === 1) throw new NetworkError()
        return uploadedVersion
      }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    const first = renderRoute(`/staff/origination/${caseId}`)
    const firstInput = await screen.findByLabelText('Signed paper application file')
    const file = new File(['%PDF-private-content'], 'private-customer-name.pdf', { type: 'application/pdf' })
    await user.upload(firstInput, file)
    fireEvent.submit(firstInput.closest('form')!)
    expect(await screen.findByText(/upload result is unknown/i)).toBeVisible()
    const stored = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(stored).not.toContain('%PDF-private-content')
    expect(stored).not.toContain('private-customer-name.pdf')
    first.unmount()

    renderRoute(`/staff/origination/${caseId}`)
    expect(await screen.findByText(/prior upload result is unresolved/i)).toBeVisible()
    const recoveredInput = screen.getByLabelText('Signed paper application file')
    expect(recoveredInput).toHaveValue('')
    await user.upload(recoveredInput, file)
    fireEvent.submit(recoveredInput.closest('form')!)
    await waitFor(() => expect(uploads).toHaveLength(2))
    expect(uploads[1]!.get('uploadRequestId')).toBe(uploads[0]!.get('uploadRequestId'))
    expect(uploads[1]!.get('expectedCurrentVersionId')).toBeNull()
  })

  it('blocks changed upload content while the prior result is unresolved', async () => {
    const uploads: FormData[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return []
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) {
        uploads.push((options as { body: FormData }).body)
        throw new NetworkError()
      }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    const input = await screen.findByLabelText('Signed paper application file')
    await user.upload(input, new File(['first'], 'application.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)
    expect(await screen.findByText(/upload result is unknown/i)).toBeVisible()
    await user.upload(input, new File(['changed'], 'application.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)

    expect(await screen.findByText(/does not match the unresolved upload/i)).toBeVisible()
    expect(uploads).toHaveLength(1)
  })

  it('retains the upload identity on idempotency conflict and refetches on stale version', async () => {
    const ids: unknown[] = []
    let stale = false
    let evidenceReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) { evidenceReads += 1; return evidence }
      if (path.endsWith('/evidence/UCL_PAPER_APPLICATION/versions')) {
        const body = (options as { body: FormData }).body
        ids.push(body.get('uploadRequestId'))
        throw new ApiError(409, stale ? 'STALE_DOCUMENT_VERSION' : 'IDEMPOTENCY_KEY_REUSED', 'Conflict', String(path), '2026-09-17T08:00:00Z')
      }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    const input = await screen.findByLabelText('Signed paper application file')
    await user.upload(input, new File(['same'], 'replacement.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)
    expect(await screen.findByText(/retained for reconciliation/i)).toBeVisible()
    fireEvent.submit(input.closest('form')!)
    await waitFor(() => expect(ids).toHaveLength(2))
    expect(ids[1]).toBe(ids[0])

    sessionStorage.clear()
    stale = true
    await user.upload(input, new File(['new'], 'new.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)
    expect(await screen.findByText(/review the refreshed version/i)).toBeVisible()
    expect(ids).toHaveLength(3)
    expect(evidenceReads).toBeGreaterThan(1)
    expect(findUnresolvedOperation(
      'INTAKE_EVIDENCE_UPLOAD', evidenceRecoveryResource(caseId, 'UCL_PAPER_APPLICATION'),
    )).toBeUndefined()
  })

  it('reconciles a lost Customer association without repeating the command', async () => {
    let caseReads = 0
    let associationPosts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/assisted-originations/${caseId}`) { caseReads += 1; return intake({ customerId: caseReads === 1 ? null : customerId }) }
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return []
      if (path === '/staff/customers/search') return customer
      if (path.endsWith('/customer')) { associationPosts += 1; throw new NetworkError() }
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.type(await screen.findByLabelText('Exact value'), 'CUS-000000123')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select Customer' }))

    expect(await screen.findByText(/selected Customer was confirmed/i)).toBeVisible()
    expect(associationPosts).toBe(1)
  })

  it('reconciles a lost abandonment without repeating the terminal command', async () => {
    let caseReads = 0
    let abandonPosts = 0
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) { caseReads += 1; return intake(caseReads === 1 ? {} : { status: 'ABANDONED', terminalAt: '2026-09-17T09:00:00' }) }
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      if (path.endsWith('/abandon') && options?.method === 'POST') { abandonPosts += 1; throw new NetworkError() }
      if (path === '/staff/assisted-originations?status=OPEN') return []
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.click(await screen.findByRole('button', { name: 'Abandon intake' }))

    expect(await screen.findByRole('heading', { name: 'Paper intake', level: 1 })).toBeVisible()
    expect(abandonPosts).toBe(1)
  })

  it('does not repeat an intake or Customer create when the response is unknown', async () => {
    let intakeCreates = 0
    let customerCreates = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === '/staff/assisted-originations?status=OPEN') return []
      if (path === '/staff/assisted-originations' && options?.method === 'POST') { intakeCreates += 1; throw new NetworkError() }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    const list = renderRoute('/staff/origination')
    await user.click(await screen.findByRole('button', { name: 'Start UCL intake' }))
    expect(await screen.findByText(/create result is unresolved/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Start UCL intake' })).toBeDisabled()
    expect(intakeCreates).toBe(1)
    list.unmount()

    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake({ customerId: null })
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return []
      if (path === '/staff/customers' && options?.method === 'POST') { customerCreates += 1; throw new NetworkError() }
      if (path === '/staff/customers/search') return customer
      throw new Error(`Unexpected request ${path}`)
    })
    renderRoute(`/staff/origination/${caseId}`)
    await user.type(await screen.findByLabelText('Full name'), 'Paper Customer')
    await user.type(screen.getByLabelText('Identity reference'), '012345678901')
    await user.type(screen.getByLabelText('Phone'), '0900000000')
    await user.type(screen.getByLabelText('Employment status'), 'EMPLOYED')
    await user.type(screen.getByLabelText('Residential address'), '1 Meridian Street')
    await user.click(screen.getByLabelText('Signed application consent recorded'))
    await user.click(screen.getByLabelText('Data processing consent recorded'))
    await user.click(screen.getByRole('button', { name: 'Create and select Customer' }))

    expect(await screen.findByText(/matching Customer was found/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Create and select Customer' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Select Customer' })).toBeEnabled()
    expect(customerCreates).toBe(1)
  })

  it('does not repeat unknown profile or bank mutations', async () => {
    let profilePuts = 0
    let bankPosts = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/assisted-originations/${caseId}`) return intake()
      if (path === `/staff/customers/${customerId}/profile` && options?.method === 'PUT') { profilePuts += 1; throw new NetworkError() }
      if (path === `/staff/customers/${customerId}`) return customer
      if (path === `/staff/customers/${customerId}/bank-accounts` && options?.method === 'POST') { bankPosts += 1; throw new NetworkError() }
      if (path === `/staff/customers/${customerId}/bank-accounts`) return []
      if (path === `/staff/assisted-originations/${caseId}/evidence`) return evidence
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderRoute(`/staff/origination/${caseId}`)
    await user.click(await screen.findByRole('button', { name: 'Save profile' }))
    expect(await screen.findByText(/saved profile was confirmed/i)).toBeVisible()
    expect(profilePuts).toBe(1)

    await user.type(screen.getByLabelText('Bank code'), 'MER')
    await user.type(screen.getByLabelText('Bank name'), 'Meridian Bank')
    await user.type(screen.getByLabelText('Account holder'), 'Paper Customer')
    await user.type(screen.getByLabelText('Account number'), '1234567890')
    await user.click(screen.getByRole('button', { name: 'Add bank account' }))
    expect(await screen.findByText(/cannot prove this add request/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Add bank account' })).toBeDisabled()
    expect(bankPosts).toBe(1)
  })
})
