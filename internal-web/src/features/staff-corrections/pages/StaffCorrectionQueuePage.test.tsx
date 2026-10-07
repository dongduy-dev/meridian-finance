import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import type { StaffCorrectionTask } from '../api/contracts'
import { staffCorrectionKeys } from '../api/queries'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-10-07T10:00:00Z',
  userId: '55555555-5555-4555-8555-555555555555', email: 'reviewer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['loan:correction:staff'],
}
const task: StaffCorrectionTask = {
  taskId: '33333333-3333-4333-8333-333333333333',
  correctionRequestId: '22222222-2222-4222-8222-222222222222',
  loanApplicationId: '11111111-1111-4111-8111-111111111111',
  status: 'OPEN', scope: 'SUPPORTING_DOCUMENT_UPLOAD', documentType: 'BANK_STATEMENT',
  checklistItemId: '44444444-4444-4444-8444-444444444444', baselineDocumentVersionId: null,
  reasonCode: 'DOCUMENT_REVIEW_REQUIRED', staffInstruction: 'Upload supporting evidence.',
  createdAt: '2026-10-07T08:00:00', completedAt: null,
}
const readFailure = new api.ApiError(500, 'INTERNAL_FAILURE', 'private database detail', '/queue', 'now')
const emptyHeading = 'No Staff corrections on this page'

function renderPage() {
  const client = createQueryClient()
  const router = createTestRouter(['/staff/work/corrections'])
  render(<QueryClientProvider client={client}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return client
}

describe('Staff correction queue freshness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('shows an initial read error without rows or an empty claim and allows explicit retry', async () => {
    vi.mocked(api.apiRequest).mockRejectedValue(readFailure)
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Work queue unavailable' }, { timeout: 3_000 })).toBeVisible()
    expect(screen.queryByRole('heading', { name: emptyHeading })).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: `Open correction task ${task.taskId}` })).not.toBeInTheDocument()
    expect(screen.queryByText('Latest queue refresh unavailable')).not.toBeInTheDocument()
    expect(screen.queryByText(/private database detail/)).not.toBeInTheDocument()

    vi.mocked(api.apiRequest).mockResolvedValue([])
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: emptyHeading })).toBeVisible()
  })

  it('shows the authoritative empty state after a successful read', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue([])
    renderPage()
    expect(await screen.findByRole('heading', { name: emptyHeading })).toBeVisible()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByText('Latest queue refresh unavailable')).not.toBeInTheDocument()
    expect(api.apiRequest).toHaveBeenCalledWith('/staff-corrections/tasks?status=OPEN&page=0&size=20', expect.anything())
  })

  it('preserves populated results and case navigation with a stale warning after a failed refresh', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue([task])
    const client = renderPage()
    const linkName = `Open correction task ${task.taskId}`
    expect(await screen.findByRole('link', { name: linkName })).toBeVisible()

    vi.mocked(api.apiRequest).mockRejectedValue(readFailure)
    await act(async () => { await client.invalidateQueries({ queryKey: staffCorrectionKeys.queue(0, 20) }) })
    expect(await screen.findByRole('heading', { name: 'Latest queue refresh unavailable' })).toBeVisible()
    expect(screen.getByText(/Previous results may be stale.*do not confirm that work is still pending or that the queue is empty/)).toBeVisible()
    expect(screen.getByRole('link', { name: linkName })).toHaveAttribute('href',
      `/staff/applications/${task.loanApplicationId}/corrections?taskId=${task.taskId}`)
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.queryByRole('heading', { name: 'Work queue unavailable' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: emptyHeading })).not.toBeInTheDocument()
    expect(screen.queryByText(/private database detail/)).not.toBeInTheDocument()

    vi.mocked(api.apiRequest).mockResolvedValue([])
    await act(async () => { await client.invalidateQueries({ queryKey: staffCorrectionKeys.queue(0, 20) }) })
    expect(await screen.findByRole('heading', { name: emptyHeading })).toBeVisible()
    expect(screen.queryByText('Latest queue refresh unavailable')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: linkName })).not.toBeInTheDocument()
  })

  it('warns after a failed refresh of cached empty results without claiming the queue is still empty', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue([])
    const client = renderPage()
    expect(await screen.findByRole('heading', { name: emptyHeading })).toBeVisible()

    vi.mocked(api.apiRequest).mockRejectedValue(readFailure)
    await act(async () => { await client.invalidateQueries({ queryKey: staffCorrectionKeys.queue(0, 20) }) })
    expect(await screen.findByRole('heading', { name: 'Latest queue refresh unavailable' })).toBeVisible()
    expect(screen.getByText(/Previous results may be stale.*do not confirm that work is still pending or that the queue is empty/)).toBeVisible()
    expect(screen.queryByRole('heading', { name: emptyHeading })).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Work queue unavailable' })).not.toBeInTheDocument()
  })
})
