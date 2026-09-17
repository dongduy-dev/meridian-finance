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
import { ApiError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'

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

function renderRoute(path: string) {
  const router = createTestRouter([path])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return router
}

function requestPath(call: unknown[]): string { return String(call[0]) }

describe('assisted origination pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
      if (path.includes('/evidence/UCL_PAPER_APPLICATION/versions')) return {
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

  it('does not issue hidden Customer or Document queries without their capabilities', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(staff(['loan:originate:staff']))
    vi.mocked(api.apiRequest).mockResolvedValue(intake())
    renderRoute(`/staff/origination/${caseId}`)

    expect(await screen.findByRole('heading', { name: 'UCL intake', level: 1 })).toBeVisible()
    expect(screen.getByText(/Customer intake permissions are required/)).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.map(requestPath)).toEqual([`/staff/assisted-originations/${caseId}`])
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
})
