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

const companyId = '22222222-2222-2222-2222-222222222222'
const company = { id: companyId, companyCode: 'ACME', name: 'Acme Ltd', status: 'ACTIVE', salaryAdvancePolicyLimit: 20_000_000 }
const employee = {
  id: '22222222-2222-4222-8222-222222222222', partnerCompanyId: companyId,
  importBatchId: '33333333-3333-4333-8333-333333333333', employeeCode: 'EMP-001',
  identityReference: 'ID-SECRET', salaryAmount: 10_000_000, salaryAdvanceLimit: 4_000_000,
  employmentStatus: 'ACTIVE', active: true,
}
const csvHeaders = 'employeeCode,identityReference,salaryAmount,salaryAdvanceLimit,employmentStatus,active'
const csvRow = 'EMP-NEW,ID-NEW,10000000,4000000,ACTIVE,true'
const actor = (permissions: string[]): AuthResponse => ({
  tokenType: 'Bearer', accessToken: 'token', expiresAt: '2026-09-16T12:00:00Z',
  userId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', email: 'admin@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['BACK_OFFICE_ADMIN'], permissions,
})

function renderPath(path: string) {
  const router = createTestRouter([path])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

function storedBrowserText() {
  const values: string[] = []
  for (let index = 0; index < sessionStorage.length; index += 1) values.push(sessionStorage.getItem(sessionStorage.key(index) ?? '') ?? '')
  for (let index = 0; index < localStorage.length; index += 1) values.push(localStorage.getItem(localStorage.key(index) ?? '') ?? '')
  return values.join('\n')
}

function mockReads() {
  vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
    if ((options as RequestInit | undefined)?.method) throw new Error('Unexpected command')
    if (String(path).endsWith('/employees?activeOnly=false')) return [employee]
    if (String(path).endsWith('/employee-import-batches')) return []
    if (String(path) === `/partner-companies/${companyId}`) return company
    if (String(path) === '/partner-companies') return [company]
    throw new Error(`Unexpected path ${path}`)
  })
}

describe('Partner administration pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    localStorage.clear()
    mockReads()
  })

  it('shows sensitive employee evidence only on the authorized detail route without placing it in URLs or storage', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read']))
    renderPath(`/admin/partners/${companyId}`)
    expect(await screen.findByRole('heading', { name: 'Acme Ltd' })).toBeVisible()
    expect(screen.getByText('EMP-001')).toBeVisible()
    expect(screen.getByText('ID-SECRET')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Save details' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Upload CSV')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Partner Employee CSV')).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.every(([path]) => !String(path).includes('EMP-001') && !String(path).includes('ID-SECRET'))).toBe(true)
    expect(storedBrowserText()).not.toContain('EMP-001')
    expect(storedBrowserText()).not.toContain('ID-SECRET')
  })

  it('shows company and import commands only with read plus manage', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    renderPath(`/admin/partners/${companyId}`)
    expect(await screen.findByRole('button', { name: 'Save details' })).toBeVisible()
    expect(screen.getByLabelText('Upload CSV')).toBeChecked()
    expect(screen.getByLabelText('Partner Employee CSV')).toBeVisible()
    const user = userEvent.setup()
    await user.click(screen.getByLabelText('Enter manually'))
    expect(screen.getByRole('button', { name: 'Import employees' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Add employee row' })).toBeVisible()
  })

  it('renders the backend-ordered company list safely for a read-only actor', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read']))
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path) === '/partner-companies') return [{ ...company, status: 'FUTURE_STATE' }]
      throw new Error(`Unexpected path ${path}`)
    })

    renderPath('/admin/partners')

    expect(await screen.findByRole('heading', { name: 'Partner Companies' })).toBeVisible()
    expect(await screen.findByText('ACME')).toBeVisible()
    expect(screen.getByText('Unknown status')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Open' })).toHaveAttribute('href', `/admin/partners/${companyId}`)
    expect(screen.queryByRole('button', { name: 'Create company' })).not.toBeInTheDocument()
  })

  it('creates a company only from the supported fields and refreshes the list', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let listReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path) === '/partner-companies' && !(options as RequestInit | undefined)?.method) {
        listReads += 1
        return []
      }
      if (String(path) === '/partner-companies' && (options as RequestInit).method === 'POST') return company
      throw new Error(`Unexpected path ${path}`)
    })
    renderPath('/admin/partners')
    const user = userEvent.setup()
    await screen.findByText('No Partner Companies are configured.')
    await user.type(screen.getByLabelText('Company code'), 'ACME')
    await user.type(screen.getByLabelText('Company name'), 'Acme Ltd')
    await user.clear(screen.getByLabelText('Salary Advance policy limit'))
    await user.type(screen.getByLabelText('Salary Advance policy limit'), '20000000')
    await user.click(screen.getByRole('button', { name: 'Create company' }))

    await waitFor(() => expect(listReads).toBeGreaterThan(1))
    const command = vi.mocked(api.apiRequest).mock.calls.find(([, options]) => (options as RequestInit | undefined)?.method === 'POST')
    expect(command?.[0]).toBe('/partner-companies')
    expect((command?.[1] as { body: unknown }).body).toEqual({
      companyCode: 'ACME', name: 'Acme Ltd', status: 'ACTIVE', salaryAdvancePolicyLimit: 20_000_000,
    })
  })

  it('keeps detail editing and status change as distinct commands', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      const method = (options as RequestInit | undefined)?.method
      if (method === 'PUT') return { ...company, name: 'Updated Ltd' }
      if (method === 'POST' && String(path).endsWith('/status')) return { ...company, status: 'SUSPENDED' }
      if (String(path).endsWith('/employees?activeOnly=false')) return [employee]
      if (String(path).endsWith('/employee-import-batches')) return []
      if (String(path) === `/partner-companies/${companyId}`) return company
      if (String(path) === '/partner-companies') return [company]
      throw new Error(`Unexpected path ${path}`)
    })
    renderPath(`/admin/partners/${companyId}`)
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Acme Ltd' })
    await user.clear(screen.getByLabelText('Company name'))
    await user.type(screen.getByLabelText('Company name'), 'Updated Ltd')
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    expect(await screen.findByText('Company details updated.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Set suspended' }))
    expect(await screen.findByText('Company status updated.')).toBeVisible()

    expect(vi.mocked(api.apiRequest).mock.calls.some(([path, options]) =>
      path === `/partner-companies/${companyId}` && (options as RequestInit | undefined)?.method === 'PUT')).toBe(true)
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path, options]) =>
      path === `/partner-companies/${companyId}/status`
      && (options as RequestInit | undefined)?.method === 'POST'
      && (options as { body?: unknown }).body !== undefined)).toBe(true)
  })

  it('shows a confirmed mixed import and refreshes employees and history', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let employeeReads = 0
    let historyReads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST' && String(path).endsWith('/employee-import-batches')) {
        return { importBatchId: '77777777-7777-4777-8777-777777777777', partnerCompanyId: companyId, effectiveMonth: '2026-09', status: 'COMPLETED', validRowCount: 1, invalidRowCount: 1, rejections: [{ rowIndex: 2, errorCode: 'INVALID_SALARY_AMOUNT', reason: 'Salary amount must be nonnegative.' }] }
      }
      if (String(path).endsWith('/employees?activeOnly=false')) { employeeReads += 1; return [employee] }
      if (String(path).endsWith('/employee-import-batches')) { historyReads += 1; return [] }
      if (String(path) === `/partner-companies/${companyId}`) return company
      if (String(path) === '/partner-companies') return [company]
      throw new Error(`Unexpected path ${path}`)
    })
    renderPath(`/admin/partners/${companyId}`)
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Acme Ltd' })
    await user.click(screen.getByLabelText('Enter manually'))
    await user.click(screen.getByRole('button', { name: 'Add employee row' }))
    expect(screen.getByText('Employee row 2')).toBeVisible()
    await user.click(screen.getAllByRole('button', { name: 'Remove row' })[1]!)
    expect(screen.queryByText('Employee row 2')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Effective month'), '2026-09')
    await user.type(screen.getByLabelText('Employee code'), 'EMP-NEW')
    await user.type(screen.getByLabelText('Identity reference'), 'ID-NEW')
    await user.clear(screen.getByLabelText('Salary amount'))
    await user.type(screen.getByLabelText('Salary amount'), '10000000')
    await user.clear(screen.getByLabelText('Salary Advance limit'))
    await user.type(screen.getByLabelText('Salary Advance limit'), '4000000')
    await user.click(screen.getByRole('button', { name: 'Import employees' }))

    expect(await screen.findByText('1 valid row(s), 1 invalid row(s).')).toBeVisible()
    expect(screen.getByText(/Row 2: Salary amount must be nonnegative/)).toBeVisible()
    await waitFor(() => {
      expect(employeeReads).toBeGreaterThan(1)
      expect(historyReads).toBeGreaterThan(1)
    })
  })

  it('treats an idempotency conflict as definitive and does not offer exact retry', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST' && String(path).endsWith('/employee-import-batches')) {
        throw new ApiError(409, 'IDEMPOTENCY_KEY_REUSED', 'Request ID was reused.', String(path), '2026-09-16T08:00:00Z')
      }
      if (String(path).endsWith('/employees?activeOnly=false')) return [employee]
      if (String(path).endsWith('/employee-import-batches')) return []
      if (String(path) === `/partner-companies/${companyId}`) return company
      return [company]
    })
    renderPath(`/admin/partners/${companyId}`)
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Acme Ltd' })
    await user.click(screen.getByLabelText('Enter manually'))
    await user.type(screen.getByLabelText('Effective month'), '2026-09')
    await user.type(screen.getByLabelText('Employee code'), 'EMP-NEW')
    await user.type(screen.getByLabelText('Identity reference'), 'ID-NEW')
    await user.click(screen.getByRole('button', { name: 'Import employees' }))

    expect(await screen.findByText('Request ID was reused.')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Retry exact import' })).not.toBeInTheDocument()
  })

  it('renders a valid CSV review and sends structured JSON without the raw file', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let submitted: unknown
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST' && String(path).endsWith('/employee-import-batches')) {
        submitted = (options as { body: unknown }).body
        return { importBatchId: '55555555-5555-4555-8555-555555555555', partnerCompanyId: companyId, effectiveMonth: '2026-09', status: 'COMPLETED', validRowCount: 1, invalidRowCount: 0, rejections: [] }
      }
      if (String(path).endsWith('/employees?activeOnly=false')) return [employee]
      if (String(path).endsWith('/employee-import-batches')) return []
      if (String(path) === `/partner-companies/${companyId}`) return company
      return [company]
    })
    renderPath(`/admin/partners/${companyId}`)
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Acme Ltd' })
    await user.type(screen.getByLabelText('Effective month'), '2026-09')
    await user.upload(screen.getByLabelText('Partner Employee CSV'), new File(
      [`${csvHeaders}\n${csvRow}\n`],
      'partner-employees.csv',
      { type: 'text/csv' },
    ))

    expect(await screen.findByText('Selected file: partner-employees.csv')).toBeVisible()
    expect(screen.getByText('Total data rows').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByText('Client-valid rows').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByText('Client-problem rows').nextElementSibling).toHaveTextContent('0')
    expect(screen.getByRole('table', { name: 'Partner Employee CSV preview' })).toHaveTextContent('EMP-NEW')
    expect(storedBrowserText()).not.toContain('EMP-NEW')
    expect(storedBrowserText()).not.toContain('ID-NEW')

    await user.click(screen.getByRole('button', { name: 'Import CSV rows' }))
    await screen.findByText('Employee import completed.')
    expect(submitted).toEqual({
      requestId: expect.any(String),
      effectiveMonth: '2026-09',
      rows: [{
        employeeCode: 'EMP-NEW', identityReference: 'ID-NEW', salaryAmount: 10_000_000,
        salaryAdvanceLimit: 4_000_000, employmentStatus: 'ACTIVE', active: true,
      }],
    })
    expect(submitted).not.toBeInstanceOf(FormData)
    expect(JSON.stringify(submitted)).not.toContain(csvHeaders)
  })

  it('shows client CSV shape problems and does not offer submission', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    renderPath(`/admin/partners/${companyId}`)
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Acme Ltd' })
    await user.upload(screen.getByLabelText('Partner Employee CSV'), new File(
      [`${csvHeaders}\nEMP-NEW,ID-SECRET,not-money,4000000,ACTIVE,true`],
      'invalid-partner-employees.csv',
      { type: 'text/csv' },
    ))

    expect(await screen.findByText('Data row 1 — Salary amount must be a nonnegative number.')).toBeVisible()
    expect(screen.getByText('Client-problem rows').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByRole('table', { name: 'Partner Employee CSV preview' })).toHaveTextContent('Problem')
    expect(screen.getByRole('button', { name: 'Import CSV rows' })).toBeDisabled()
    expect(vi.mocked(api.apiRequest).mock.calls.every(([, options]) => (options as RequestInit | undefined)?.method !== 'POST')).toBe(true)
  })

  it('retains an unknown CSV import only in page memory and explicitly retries the exact request and rows', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('44444444-4444-4444-8444-444444444444')
    let attempts = 0
    const submitted: unknown[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST' && String(path).endsWith('/employee-import-batches')) {
        attempts += 1
        submitted.push((options as { body: unknown }).body)
        if (attempts === 1) throw new NetworkError('response lost')
        return { importBatchId: '55555555-5555-4555-8555-555555555555', partnerCompanyId: companyId, effectiveMonth: '2026-09', status: 'COMPLETED', validRowCount: 1, invalidRowCount: 0, rejections: [] }
      }
      if (String(path).endsWith('/employees?activeOnly=false')) return [employee]
      if (String(path).endsWith('/employee-import-batches')) return []
      if (String(path) === `/partner-companies/${companyId}`) return company
      return [company]
    })
    renderPath(`/admin/partners/${companyId}`)
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Acme Ltd' })
    await user.type(screen.getByLabelText('Effective month'), '2026-09')
    await user.upload(screen.getByLabelText('Partner Employee CSV'), new File(
      [`${csvHeaders}\n${csvRow}`],
      'partner-employees.csv',
      { type: 'text/csv' },
    ))
    await user.click(await screen.findByRole('button', { name: 'Import CSV rows' }))
    expect(await screen.findByRole('heading', { name: 'Import result unknown' })).toBeVisible()
    expect(storedBrowserText()).not.toContain('EMP-NEW')
    expect(storedBrowserText()).not.toContain('ID-NEW')
    expect(screen.getByLabelText('Effective month')).toBeDisabled()
    expect(screen.getByLabelText('Partner Employee CSV')).toBeDisabled()
    expect(screen.getByLabelText('Enter manually')).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Retry exact import' }))
    expect(await screen.findByText('Employee import completed.')).toBeVisible()
    await waitFor(() => expect(submitted).toHaveLength(2))
    expect(submitted[1]).toEqual(submitted[0])
    expect(submitted[0]).toMatchObject({ requestId: '44444444-4444-4444-8444-444444444444', effectiveMonth: '2026-09' })
  })

  it('bounds a large CSV preview while submitting every parsed row in order', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(actor(['partner:read', 'partner:manage']))
    let submittedRows: unknown[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if ((options as RequestInit | undefined)?.method === 'POST' && String(path).endsWith('/employee-import-batches')) {
        submittedRows = (options as { body: { rows: unknown[] } }).body.rows
        return { importBatchId: '55555555-5555-4555-8555-555555555555', partnerCompanyId: companyId, effectiveMonth: '2026-09', status: 'COMPLETED', validRowCount: 30, invalidRowCount: 0, rejections: [] }
      }
      if (String(path).endsWith('/employees?activeOnly=false')) return [employee]
      if (String(path).endsWith('/employee-import-batches')) return []
      if (String(path) === `/partner-companies/${companyId}`) return company
      return [company]
    })
    const rows = Array.from({ length: 30 }, (_, index) => `EMP-${String(index + 1).padStart(3, '0')},ID-${index + 1},10000000,4000000,ACTIVE,true`)
    renderPath(`/admin/partners/${companyId}`)
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Acme Ltd' })
    await user.type(screen.getByLabelText('Effective month'), '2026-09')
    await user.upload(screen.getByLabelText('Partner Employee CSV'), new File(
      [[csvHeaders, ...rows].join('\n')],
      'large-partner-import.csv',
      { type: 'text/csv' },
    ))

    expect(await screen.findByText('Showing the first 25 of 30 rows. The full parsed batch will be submitted.')).toBeVisible()
    expect(screen.getByRole('table', { name: 'Partner Employee CSV preview' }).querySelectorAll('tbody tr')).toHaveLength(25)
    await user.click(screen.getByRole('button', { name: 'Import CSV rows' }))
    await screen.findByText('Employee import completed.')
    expect(submittedRows).toHaveLength(30)
    expect(submittedRows[0]).toMatchObject({ employeeCode: 'EMP-001' })
    expect(submittedRows[29]).toMatchObject({ employeeCode: 'EMP-030' })
  })
})
