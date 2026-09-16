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

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const salary = {
  productCode: 'SALARY_ADVANCE', productType: 'SALARY_BASED', name: 'Salary Advance',
  description: 'Short-term lending.', active: true, minAmount: 500_000, maxAmount: 10_000_000,
}
const collateral = {
  productCode: 'COLLATERAL_LOAN', productType: 'SECURED', name: 'Collateral Loan',
  description: null, active: false, minAmount: 5_000_000, maxAmount: 200_000_000,
}
const actor = (): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'token', expiresAt: '2026-09-16T12:00:00Z',
  userId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', email: 'admin@meridian.local',
  userType: 'STAFF', customerId: null, roles: [], permissions: ['loan:product:manage'],
})

function renderPage() {
  const router = createTestRouter(['/admin/products'])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Loan Product administration page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(actor())
    vi.mocked(api.apiRequest).mockResolvedValue([collateral, salary])
  })

  it('renders backend order, active and inactive products, and safe unknown values', async () => {
    vi.mocked(api.apiRequest).mockResolvedValueOnce([
      { ...collateral, productCode: 'FUTURE_PRODUCT', productType: 'FUTURE_TYPE', name: 'Untrusted label' },
      { ...salary, name: 'Customer Salary Advance' },
    ])
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Loan Products' })).toBeVisible()
    const cards = await screen.findAllByRole('heading', { level: 2 })
    expect(cards.map((heading) => heading.textContent)).toEqual(['Unknown product', 'Customer Salary Advance'])
    expect(screen.getByText('FUTURE_PRODUCT')).toBeVisible()
    expect(screen.getByText(/Unknown type/)).toBeVisible()
    expect(screen.getByText('Inactive')).toBeVisible()
    expect(screen.getByText('Active')).toBeVisible()
  })

  it('renders loading and empty states', async () => {
    let resolve!: (value: unknown) => void
    vi.mocked(api.apiRequest).mockImplementationOnce(() => new Promise((done) => { resolve = done }))
    renderPage()
    expect(await screen.findByText('Loading Loan Products…')).toBeVisible()
    resolve([])
    expect(await screen.findByText(/No Loan Products are configured/)).toBeVisible()
  })

  it('renders an initial error with retry guidance and request correlation', async () => {
    vi.mocked(api.apiRequest).mockRejectedValue(new ApiError(
      400, 'UNEXPECTED_RESPONSE', 'failed', '/admin/loan-products', 'now', 'request-1',
    ))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Loan Products unavailable' })).toBeVisible()
    expect(screen.getByText(/request-1/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('validates inverted limits before issuing a command', async () => {
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Salary Advance' })
    const minimum = screen.getAllByLabelText('Minimum amount')[1]!
    const maximum = screen.getAllByLabelText('Maximum amount')[1]!
    await user.clear(minimum)
    await user.type(minimum, '20000000')
    await user.clear(maximum)
    await user.type(maximum, '10000000')
    await user.click(screen.getAllByRole('button', { name: 'Save amount limits' })[1]!)
    expect(await screen.findByText('Maximum amount must be greater than or equal to minimum amount.')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toHaveLength(0)
  })

  it('waits for backend confirmation before showing changed limits and refreshes afterward', async () => {
    let listReads = 0
    let confirm!: (value: unknown) => void
    let authoritative = salary
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (!(options as RequestInit | undefined)?.method) {
        listReads += 1
        return [collateral, authoritative]
      }
      if (String(path).endsWith('/limits')) return new Promise((done) => {
        confirm = (value) => {
          authoritative = value as typeof salary
          done(value)
        }
      })
      throw new Error(`Unexpected path ${path}`)
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Salary Advance' })
    const maximum = screen.getAllByLabelText('Maximum amount')[1]!
    await user.clear(maximum)
    await user.type(maximum, '12000000')
    await user.click(screen.getAllByRole('button', { name: 'Save amount limits' })[1]!)
    expect(screen.getByText(/10.000.000/)).toBeVisible()
    confirm({ ...salary, maxAmount: 12_000_000 })
    await waitFor(() => expect(listReads).toBeGreaterThan(1))
    expect((maximum as HTMLInputElement).value).toBe('12000000')
  })

  it('does not retry an unknown limit result and retains last confirmed state', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if (!(options as RequestInit | undefined)?.method) return [salary]
      throw new NetworkError('response lost')
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Salary Advance' })
    const maximum = screen.getByLabelText('Maximum amount')
    await user.clear(maximum)
    await user.type(maximum, '12000000')
    await user.click(screen.getByRole('button', { name: 'Save amount limits' }))
    expect(await screen.findByRole('heading', { name: 'Limits were not confirmed' })).toBeVisible()
    expect(screen.getByText(/Refresh authoritative product state/)).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.filter(([, options]) => (options as RequestInit | undefined)?.method === 'PUT')).toHaveLength(1)
    expect(screen.getByText(/10.000.000/)).toBeVisible()
  })

  it('changes activation only after confirmation and never retries a failed command', async () => {
    let confirm!: (value: unknown) => void
    let authoritative = salary
    vi.mocked(api.apiRequest).mockImplementation(async (_path, options) => {
      if (!(options as RequestInit | undefined)?.method) return [authoritative]
      return new Promise((done) => {
        confirm = (value) => {
          authoritative = value as typeof salary
          done(value)
        }
      })
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Salary Advance' })
    await user.click(screen.getByRole('button', { name: 'Deactivate product' }))
    expect(screen.getByText('Active')).toBeVisible()
    confirm({ ...salary, active: false })
    expect(await screen.findByText('Inactive')).toBeVisible()

    vi.mocked(api.apiRequest).mockRejectedValueOnce(new ApiError(
      422, 'INVALID_PRODUCT_LIMITS', 'Rejected.', '/admin/loan-products/SALARY_ADVANCE/activation', 'now',
    ))
    await user.click(screen.getByRole('button', { name: 'Activate product' }))
    expect(await screen.findByRole('heading', { name: 'Activation state was not confirmed' })).toBeVisible()
    expect(screen.getByText('Inactive')).toBeVisible()
  })
})
