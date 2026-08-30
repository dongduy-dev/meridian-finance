import { queryClient } from '@/app/providers/query-client'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { AuthSessionManager } from '@/features/auth/auth-session'
import type { Customer, CustomerBankAccount } from '@/features/account/account-api'
import { ApiError } from '@/lib/api'
import { createAuthApiMock, createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

const incompleteCustomer = {
  customerId: '22222222-2222-4222-8222-222222222222',
  customerNumber: 'CUS-000000001',
  status: 'ACTIVE',
  verificationStatus: 'UNVERIFIED',
  profileCompletionStatus: 'INCOMPLETE',
  primaryActiveBankAccountPresent: false,
  profile: null,
}

const completeCustomer = {
  ...incompleteCustomer,
  profileCompletionStatus: 'COMPLETE',
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

const firstAccount = {
  customerBankAccountId: '33333333-3333-4333-8333-333333333333',
  bankCode: 'ONE',
  bankNameSnapshot: 'Bank One',
  accountHolderName: 'Customer Demo',
  maskedAccountNumber: '****1111',
  accountNumberLastFour: '1111',
  status: 'ACTIVE',
  primaryAccount: true,
  createdAt: '2026-08-30T08:00:00',
  updatedAt: '2026-08-30T08:00:00',
  deactivatedAt: null,
}

const secondAccount = {
  ...firstAccount,
  customerBankAccountId: '44444444-4444-4444-8444-444444444444',
  bankCode: 'TWO',
  bankNameSnapshot: 'Bank Two',
  maskedAccountNumber: '****2222',
  accountNumberLastFour: '2222',
  primaryAccount: false,
}

function response(body: unknown, status = 200, headers?: HeadersInit) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

function errorResponse(errorCode: string, message: string, status = 409) {
  return response(
    {
      timestamp: '2026-08-30T08:00:00Z',
      status,
      errorCode,
      message,
      path: '/api/v1/customers/me',
    },
    status,
    { 'X-Request-ID': '55555555-5555-4555-8555-555555555555' },
  )
}

function renderRoute(path: string, fetchImplementation: typeof fetch) {
  vi.stubGlobal('fetch', fetchImplementation)
  const router = createTestRouter([path])
  render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return router
}

afterEach(() => {
  queryClient.clear()
  vi.unstubAllGlobals()
})

describe('Customer account routes and profile', () => {
  it('protects account routes and preserves the intended destination', async () => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid refresh token.' }),
    )
    const manager = new AuthSessionManager(api, vi.fn())
    const router = createTestRouter(['/account/bank-accounts'])
    render(<AppProviders router={router} authManager={manager} />)

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.state).toEqual({ from: '/account/bank-accounts' })
  })

  it('redirects /account to Profile and submits identity reference only for initial completion', async () => {
    const user = userEvent.setup()
    let profile: Customer = incompleteCustomer
    let updateBody: Record<string, unknown> | undefined
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/customers/me/profile') && init?.method === 'PUT') {
        updateBody = JSON.parse(String(init.body)) as Record<string, unknown>
        profile = { ...completeCustomer }
        return response(profile)
      }
      if (url.endsWith('/customers/me')) return response(profile)
      throw new Error(`Unexpected request: ${url}`)
    })
    const router = renderRoute('/account', fetchMock)

    expect(await screen.findByRole('heading', { level: 1, name: 'Profile' })).toBeVisible()
    await waitFor(() => expect(router.state.location.pathname).toBe('/account/profile'))
    expect(await screen.findByLabelText(/Identity reference/)).toBeVisible()

    await user.type(screen.getByLabelText(/Full name/), 'Customer Demo')
    await user.type(screen.getByLabelText(/Phone number/), '0901234567')
    await user.type(screen.getByLabelText(/Identity reference/), 'IDREF-SENSITIVE-001')
    await user.type(screen.getByLabelText(/Residential address/), '1 Meridian Street')
    await user.type(screen.getByLabelText(/Employment status/), 'SALARIED')
    await user.click(screen.getByLabelText('I accept the Meridian Customer terms.'))
    await user.click(screen.getByLabelText('I consent to the processing of my data for this Customer account.'))
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Identity reference: On file')).toBeVisible()
    expect(screen.queryByLabelText(/Identity reference/)).not.toBeInTheDocument()
    expect(screen.queryByText('IDREF-SENSITIVE-001')).not.toBeInTheDocument()
    expect(updateBody).toMatchObject({ identityReference: 'IDREF-SENSITIVE-001' })
    expect(updateBody).not.toHaveProperty('customerId')
    expect(screen.getByText('Your required profile details are on file.')).toBeVisible()
    expect(JSON.stringify(queryClient.getMutationCache().getAll().map((mutation) => mutation.state)))
      .not.toContain('IDREF-SENSITIVE-001')
  })

  it('renders completed identity as On file and omits it from ordinary profile updates', async () => {
    const user = userEvent.setup()
    let updateBody: Record<string, unknown> | undefined
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/customers/me/profile') && init?.method === 'PUT') {
        updateBody = JSON.parse(String(init.body)) as Record<string, unknown>
        return response({
          ...completeCustomer,
          profile: { ...completeCustomer.profile, phoneNumber: '0909999999' },
        })
      }
      return response(completeCustomer)
    })
    renderRoute('/account/profile', fetchMock)

    expect(await screen.findByText('Identity reference: On file')).toBeVisible()
    await user.clear(screen.getByLabelText(/Phone number/))
    await user.type(screen.getByLabelText(/Phone number/), '0909999999')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Profile saved')).toBeVisible()
    expect(updateBody).not.toHaveProperty('identityReference')
    expect(updateBody).not.toHaveProperty('customerId')
  })

  it('keeps backend business rejection separate from field validation', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/customers/me/profile') && init?.method === 'PUT') {
        return errorResponse('IDENTITY_REFERENCE_ALREADY_IN_USE', 'Identity reference is already associated with another customer.')
      }
      return response(incompleteCustomer)
    })
    renderRoute('/account/profile', fetchMock)

    await user.type(await screen.findByLabelText(/Full name/), 'Customer Demo')
    await user.type(screen.getByLabelText(/Phone number/), '0901234567')
    await user.type(screen.getByLabelText(/Identity reference/), 'IDREF-CONFLICT-001')
    await user.type(screen.getByLabelText(/Residential address/), '1 Meridian Street')
    await user.type(screen.getByLabelText(/Employment status/), 'SALARIED')
    await user.click(screen.getByLabelText('I accept the Meridian Customer terms.'))
    await user.click(screen.getByLabelText('I consent to the processing of my data for this Customer account.'))
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Identity reference is already associated with another customer.')).toBeVisible()
    expect(screen.getByText(/Support reference: 55555555/)).toBeVisible()
    expect(screen.queryByText('Profile saved')).not.toBeInTheDocument()
  })
})

describe('Customer bank-account experience', () => {
  it('renders the empty state and clears the full account number after a successful masked response', async () => {
    const user = userEvent.setup()
    let accounts: CustomerBankAccount[] = []
    let submittedBody: Record<string, unknown> | undefined
    const addedAccount = { ...firstAccount, maskedAccountNumber: '****7890', accountNumberLastFour: '7890' }
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/customers/me/bank-accounts') && init?.method === 'POST') {
        submittedBody = JSON.parse(String(init.body)) as Record<string, unknown>
        accounts = [addedAccount]
        return response(addedAccount, 201)
      }
      if (url.endsWith('/customers/me/bank-accounts')) return response(accounts)
      if (url.endsWith('/customers/me')) {
        return response({ ...completeCustomer, primaryActiveBankAccountPresent: accounts.length > 0 })
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    renderRoute('/account/bank-accounts', fetchMock)

    expect(await screen.findByText('No bank accounts yet')).toBeVisible()
    await user.type(await screen.findByLabelText(/Bank code/), 'VCB')
    await user.type(screen.getByLabelText(/Bank name/), 'Vietcombank')
    await user.type(screen.getByLabelText(/Account holder name/), 'Customer Demo')
    await user.type(screen.getByLabelText(/Account number/), '1234567890')
    await user.click(screen.getByRole('button', { name: 'Add bank account' }))

    expect(await screen.findByText('****7890')).toBeVisible()
    expect(screen.getByLabelText(/Account number/)).toHaveValue('')
    expect(screen.queryByText('1234567890')).not.toBeInTheDocument()
    expect(submittedBody).toEqual({
      bankCode: 'VCB',
      bankNameSnapshot: 'Vietcombank',
      accountHolderName: 'Customer Demo',
      accountNumber: '1234567890',
    })
    expect(submittedBody).not.toHaveProperty('customerId')
    expect(screen.getByText('Primary')).toBeVisible()
    expect(JSON.stringify(queryClient.getMutationCache().getAll().map((mutation) => mutation.state)))
      .not.toContain('1234567890')
  })

  it('confirms make-primary and deactivate mutations and refreshes authoritative state', async () => {
    const user = userEvent.setup()
    let accounts: CustomerBankAccount[] = [{ ...firstAccount }, { ...secondAccount }]
    const commands: string[] = []
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith(`/${secondAccount.customerBankAccountId}/make-primary`) && init?.method === 'POST') {
        commands.push('make-primary')
        accounts = accounts.map((account) => ({ ...account, primaryAccount: account.customerBankAccountId === secondAccount.customerBankAccountId }))
        return response(accounts[1])
      }
      if (url.endsWith(`/${firstAccount.customerBankAccountId}/deactivate`) && init?.method === 'POST') {
        commands.push('deactivate')
        accounts = accounts.map((account) => account.customerBankAccountId === firstAccount.customerBankAccountId
          ? { ...account, status: 'DEACTIVATED', primaryAccount: false, deactivatedAt: '2026-08-30T10:00:00' }
          : account)
        return response(accounts[0])
      }
      if (url.endsWith('/customers/me/bank-accounts')) return response(accounts)
      if (url.endsWith('/customers/me')) return response({ ...completeCustomer, primaryActiveBankAccountPresent: true })
      throw new Error(`Unexpected request: ${url}`)
    })
    renderRoute('/account/bank-accounts', fetchMock)

    const secondCard = (await screen.findByRole('heading', { name: 'Bank Two' })).closest('[class*="rounded-lg"]') as HTMLElement
    const makePrimaryTrigger = within(secondCard).getByRole('button', { name: 'Make primary' })
    await user.click(makePrimaryTrigger)
    let primaryDialog = await screen.findByRole('dialog', { name: 'Make this the primary account?' })
    expect(within(primaryDialog).getByText(/\*\*\*\*2222/)).toBeVisible()
    await user.click(within(primaryDialog).getByRole('button', { name: 'Cancel' }))
    expect(makePrimaryTrigger).toHaveFocus()

    await user.click(makePrimaryTrigger)
    primaryDialog = await screen.findByRole('dialog', { name: 'Make this the primary account?' })
    await user.click(within(primaryDialog).getByRole('button', { name: 'Make primary' }))
    await waitFor(() => expect(commands).toContain('make-primary'))

    const firstCard = screen.getByRole('heading', { name: 'Bank One' }).closest('[class*="rounded-lg"]') as HTMLElement
    await user.click(within(firstCard).getByRole('button', { name: 'Deactivate' }))
    const deactivateDialog = await screen.findByRole('dialog', { name: 'Deactivate this bank account?' })
    await user.click(within(deactivateDialog).getByRole('button', { name: 'Deactivate account' }))

    await waitFor(() => expect(commands).toEqual(['make-primary', 'deactivate']))
    expect(await within(firstCard).findByText('Inactive')).toBeVisible()
  })

  it('shows mutation conflicts and refetches instead of leaving stale success state', async () => {
    const user = userEvent.setup()
    let listReads = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith(`/${secondAccount.customerBankAccountId}/make-primary`) && init?.method === 'POST') {
        return errorResponse('BANK_ACCOUNT_UPDATE_NOT_ALLOWED', 'Inactive bank account cannot be made primary.')
      }
      if (url.endsWith('/customers/me/bank-accounts')) {
        listReads += 1
        return response([{ ...firstAccount }, { ...secondAccount }])
      }
      return response({ ...completeCustomer, primaryActiveBankAccountPresent: true })
    })
    renderRoute('/account/bank-accounts', fetchMock)

    const secondCard = (await screen.findByRole('heading', { name: 'Bank Two' })).closest('[class*="rounded-lg"]') as HTMLElement
    await user.click(within(secondCard).getByRole('button', { name: 'Make primary' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Make primary' }))

    expect(await screen.findByText('Inactive bank account cannot be made primary.')).toBeVisible()
    await waitFor(() => expect(listReads).toBeGreaterThan(1))
    expect(screen.queryByText('Bank accounts updated')).not.toBeInTheDocument()
  })
})
