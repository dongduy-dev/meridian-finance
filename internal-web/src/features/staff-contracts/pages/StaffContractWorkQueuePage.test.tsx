import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { caseFixture } from '../api/contracts.test'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-07T10:00:00Z',
  userId: '22222222-2222-4222-8222-222222222222', email: 'accounting@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'], permissions: ['loan:contract:read'],
}

function queue(page: number, overrides: Record<string, unknown> = {}) {
  return {
    page, size: 25, totalElements: 26, totalPages: 2,
    items: [caseFixture({ applicationNumber: `UCL-${page + 1}`, ...overrides })],
  }
}

function renderPage() {
  const router = createTestRouter(['/staff/work/contracts'])
  render(<QueryClientProvider client={createQueryClient()}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
}

describe('Staff contract work queue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('uses server-side product filtering and deterministic paging', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => queue(String(path).includes('page=1') ? 1 : 0))
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByText('UCL-1')).toBeVisible()
    await user.selectOptions(screen.getByLabelText('Product'), 'UNSECURED_CONSUMER_LOAN')
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) =>
      String(path).includes('productCode=UNSECURED_CONSUMER_LOAN'))).toBe(true)
    await user.click(screen.getByRole('button', { name: /Next/ }))
    expect(await screen.findByText('UCL-2')).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => String(path).includes('page=1'))).toBe(true)
  })

  it('renders work stage and blocker summaries from the server', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(queue(0, {
      workStage: 'READINESS_BLOCKED',
      readiness: {
        ...caseFixture().readiness,
        ready: false,
        blockerCodes: ['DOCUMENTS_NOT_PROCESSING_READY'],
      },
    }))
    renderPage()

    expect(await screen.findByText('Readiness blocked')).toBeVisible()
    expect(screen.getByText('Documents are not processing-ready.')).toBeVisible()
  })

  it('renders unknown stage, status, and blocker values neutrally', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(queue(0, {
      workStage: 'FUTURE_STAGE',
      currentContract: { ...caseFixture().currentContract, status: 'FUTURE_STATUS' },
      readiness: { ...caseFixture().readiness, ready: true, blockerCodes: ['FUTURE_BLOCKER'] },
    }))
    renderPage()

    expect(await screen.findByText('Work stage unavailable')).toBeVisible()
    expect(screen.getByText(/Contract v1 · Status unavailable/)).toBeVisible()
    expect(document.body.textContent).toContain('Unknown readiness blocker. Refresh authoritative evidence.')
    expect(screen.getByText(/Unknown operational evidence requires an authoritative refresh/)).toBeVisible()
  })
})
