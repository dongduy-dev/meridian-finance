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
import type { DigitalAccess } from '../api/customer-access-api'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const customerId = '99999999-9999-9999-9999-999999999999'
const customer = { customerId, customerNumber: 'CUS-000000123', status: 'ACTIVE',
  verificationStatus: 'UNVERIFIED', profileCompletionStatus: 'INCOMPLETE', primaryActiveBankAccountPresent: false,
  profile: { fullName: 'Existing Customer', phoneNumber: '0900000000', residentialAddress: 'Meridian Street',
    employmentStatus: 'EMPLOYED', employerName: null, termsConsentAccepted: false, dataProcessingConsentAccepted: false } }
const absent = { customerId, enabled: false, email: null, emailVerified: false }
const enabled = { customerId, enabled: true, email: 'customer@example.com', emailVerified: false }
const actor = (permissions: string[] = ['customer:intake:manage']): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'token', expiresAt: '2026-09-17T12:00:00Z',
  userId: '00000000-0000-0000-0000-000000000305', email: 'staff@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions,
})

function renderPage() {
  const router = createTestRouter(['/staff/customer-access'])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return router
}

async function selectCustomer() {
  const user = userEvent.setup()
  await screen.findByRole('heading', { name: 'Customer digital access' })
  await user.type(screen.getByLabelText('Exact value'), 'CUS-000000123')
  await user.click(screen.getByRole('button', { name: 'Search Customer' }))
  await user.click(await screen.findByRole('button', { name: 'Select Customer' }))
  return user
}

describe('Customer digital access Staff workflow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(actor())
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).endsWith('/search') ? customer : absent)
  })

  it('requires the exact route permission before any Customer request', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['customer:intake:manage:all']))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'No operational access' })).toBeVisible()
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('uses exact search, re-entry and authoritative status without a password or case ID', async () => {
    let current: DigitalAccess = absent
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/search')) return customer
      if ((options as RequestInit | undefined)?.method === 'POST') { current = enabled; return enabled }
      return current
    })
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByRole('heading', { name: 'Customer digital access' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Enable digital access' })).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Exact search'), 'identity')
    await user.type(screen.getByLabelText('Exact value'), 'ID-EXACT')
    await user.click(screen.getByRole('button', { name: 'Search Customer' }))
    expect(await screen.findByText('Status: Active')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.find(([path]) => String(path).endsWith('/search'))?.[1])
      .toMatchObject({ method: 'POST', body: { identityReference: 'ID-EXACT' } })
    expect(screen.queryByRole('button', { name: 'Enable digital access' })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Select Customer' }))
    expect(await screen.findByText('Not enabled')).toBeVisible()
    expect(screen.getByText(/one secure activation link to verify their email and set a password/)).toBeVisible()
    expect(screen.queryByText(/Forgot password/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'CUSTOMER@EXAMPLE.COM')
    await user.type(screen.getByLabelText('Identity reference'), 'ID-EXACT')
    await user.click(screen.getByRole('button', { name: 'Enable digital access' }))
    expect(await screen.findByText('Enabled')).toBeVisible()
    expect(await screen.findByText(/complete the secure activation link sent to their email/)).toBeVisible()
    expect(screen.queryByText(/Forgot password/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enable digital access' })).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.find(([path, options]) =>
      String(path).endsWith('/digital-access') && (options as RequestInit | undefined)?.method === 'POST')?.[1])
      .toMatchObject({ body: { email: 'customer@example.com', identityReference: 'ID-EXACT' } })
    expect(localStorage.getItem('ID-EXACT')).toBeNull()
    expect(sessionStorage.getItem('ID-EXACT')).toBeNull()
  })

  it('reconciles an unknown activation result with GET and never resends POST', async () => {
    let current: DigitalAccess = absent
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/search')) return customer
      if ((options as RequestInit | undefined)?.method === 'POST') {
        current = enabled
        throw new NetworkError('response lost')
      }
      return current
    })
    renderPage()
    const user = await selectCustomer()
    expect(await screen.findByText('Not enabled')).toBeVisible()
    await user.type(screen.getByLabelText('Email'), 'customer@example.com')
    await user.type(screen.getByLabelText('Identity reference'), 'ID-EXACT')
    await user.click(screen.getByRole('button', { name: 'Enable digital access' }))
    expect(await screen.findByText('Enabled')).toBeVisible()
    expect(await screen.findByText(/complete the secure activation link sent to their email/)).toBeVisible()
    expect(screen.queryByText(/Forgot password/)).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      String(path).endsWith('/digital-access') && (options as RequestInit | undefined)?.method === 'POST')).toHaveLength(1)
  })

  it.each([
    ['EMAIL_ALREADY_REGISTERED', 'An account with this email already exists.'],
    ['CUSTOMER_DIGITAL_ACCESS_OWNERSHIP_NOT_VERIFIED', 'The identity reference did not match this Customer.'],
  ])('shows a safe %s error', async (code, message) => {
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/search')) return customer
      if ((options as RequestInit | undefined)?.method === 'POST') throw new ApiError(409, code, 'safe', String(path), 'now')
      return absent
    })
    renderPage()
    const user = await selectCustomer()
    await screen.findByText('Not enabled')
    await user.type(screen.getByLabelText('Email'), 'customer@example.com')
    await user.type(screen.getByLabelText('Identity reference'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Enable digital access' }))
    await waitFor(() => expect(screen.getByText(message)).toBeVisible())
  })
})
