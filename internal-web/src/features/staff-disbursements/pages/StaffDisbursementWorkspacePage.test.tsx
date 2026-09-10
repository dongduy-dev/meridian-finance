import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { ApiError, NetworkError } from '@/lib/api'
import { findUnresolvedOperation } from '@/lib/operation/unresolved-operation'
import { createQueryClient } from '@/lib/query/query-client'
import {
  applicationId,
  confirmationFixture,
  contractId,
  disbursedCase,
  pendingCase,
  queueFixture,
} from '../api/contracts.test'
import { REVEALED_DESTINATION_BACKGROUND_TIMEOUT_MS } from './StaffDisbursementWorkspacePage'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const requestId = '66666666-6666-4666-8666-666666666666'
const secretReference = 'BANK-SENSITIVE-REFERENCE-987'
const fullAccountNumber = '1234567890'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-10T10:00:00Z',
  userId: '77777777-7777-4777-8777-777777777777', email: 'accounting@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['ACCOUNTING_OFFICER'], permissions: ['loan:disburse'],
}

function destinationReveal(version = 1) {
  return {
    contractId,
    contractVersion: version,
    bankCode: 'VCB',
    bankName: 'Vietcombank',
    accountHolderName: 'MERIDIAN CUSTOMER',
    accountNumber: fullAccountNumber,
  }
}

function renderPage() {
  const queryClient = createQueryClient()
  const router = createTestRouter([`/staff/applications/${applicationId}/disbursement`])
  const result = render(<QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider>)
  return { ...result, queryClient, router }
}

async function enterEvidence(user: ReturnType<typeof userEvent.setup>, reference = secretReference) {
  const referenceInput = await screen.findByLabelText('External transfer reference')
  await user.clear(referenceInput)
  await user.type(referenceInput, reference)
  await user.type(screen.getByLabelText('Disbursement value date'), '2026-09-10')
  await user.type(screen.getByLabelText('First repayment date'), '2026-10-10')
}

function disbursementPostCalls() {
  return vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
    String(path).endsWith('/disbursements') && (options as RequestInit | undefined)?.method === 'POST')
}

function revealCalls() {
  return vi.mocked(api.apiRequest).mock.calls.filter(([path]) => String(path).endsWith('/reveal'))
}

describe('Staff disbursement workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(requestId)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('renders masked authoritative evidence and only the three external-transfer inputs without revealing on load', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(pendingCase())
    renderPage()

    expect(await screen.findByRole('heading', { name: 'UCL-20260910-000001' })).toBeVisible()
    expect(screen.getByText('****7890')).toBeVisible()
    expect(screen.queryByText(fullAccountNumber)).not.toBeInTheDocument()
    expect(screen.getByLabelText('External transfer reference')).toBeVisible()
    expect(screen.getByLabelText('Disbursement value date')).toBeVisible()
    expect(screen.getByLabelText('First repayment date')).toBeVisible()
    expect(screen.queryByLabelText(/amount|destination|interest|term|fee|schedule|customer/i)).not.toBeInTheDocument()
    expect(screen.getByText(/Meridian does not initiate the transfer/i)).toBeVisible()
    expect(revealCalls()).toHaveLength(0)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Review disbursement confirmation' }))
    expect(screen.getByText(/Enter the external transfer reference/)).toBeVisible()
    expect(screen.getByLabelText('External transfer reference')).toHaveFocus()
  })

  it('keeps reveal data out of query and browser storage and clears it on hide and back navigation', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/reveal')) return destinationReveal()
      if (String(path).includes('/staff/disbursement-work')) return queueFixture()
      return pendingCase()
    })
    const { queryClient, router } = renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByText(fullAccountNumber)).toBeVisible()
    expect(JSON.stringify(queryClient.getQueryCache().getAll().map((entry) => entry.state.data)))
      .not.toContain(fullAccountNumber)
    expect(JSON.stringify(sessionStorage)).not.toContain(fullAccountNumber)
    expect(screen.queryByRole('button', { name: /copy/i })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Hide destination' }))
    expect(screen.queryByText(fullAccountNumber)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByText(fullAccountNumber)).toBeVisible()
    await act(() => router.navigate('/staff/work/disbursements'))
    expect(await screen.findByRole('heading', { name: 'Ready-disbursement queue' })).toBeVisible()
    await act(() => router.navigate(-1))
    expect(await screen.findByRole('heading', { name: 'UCL-20260910-000001' })).toBeVisible()
    expect(screen.queryByText(fullAccountNumber)).not.toBeInTheDocument()
  })

  it('clears a revealed destination after the feature-local background timeout', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).endsWith('/reveal') ? destinationReveal() : pendingCase())
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByText(fullAccountNumber)).toBeVisible()

    vi.useFakeTimers()
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    document.dispatchEvent(new Event('visibilitychange'))
    await act(() => vi.advanceTimersByTimeAsync(REVEALED_DESTINATION_BACKGROUND_TIMEOUT_MS))
    expect(screen.queryByText(fullAccountNumber)).not.toBeInTheDocument()
  })

  it('clears a revealed destination when the authoritative contract version changes', async () => {
    let currentCase = pendingCase()
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).endsWith('/reveal') ? destinationReveal() : currentCase)
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByText(fullAccountNumber)).toBeVisible()
    currentCase = pendingCase({
      currentContract: { ...pendingCase().currentContract, contractVersion: 2 },
    })
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    await waitFor(() => expect(screen.queryByText(fullAccountNumber)).not.toBeInTheDocument())
    expect(screen.getByText('Exact version').parentElement).toHaveTextContent('2')
  })

  it('clears a revealed destination when the case leaves DISBURSEMENT_PENDING', async () => {
    let currentCase = pendingCase()
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).endsWith('/reveal') ? destinationReveal() : currentCase)
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByText(fullAccountNumber)).toBeVisible()
    currentCase = disbursedCase()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByRole('heading', { name: 'Activation result' })).toBeVisible()
    expect(screen.queryByText(fullAccountNumber)).not.toBeInTheDocument()
  })

  it('clears a revealed destination on logout and session loss', async () => {
    vi.mocked(authApi.logout).mockResolvedValue(undefined)
    vi.mocked(api.apiRequest).mockImplementation(async (path) =>
      String(path).endsWith('/reveal') ? destinationReveal() : pendingCase())
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByText(fullAccountNumber)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Staff sign in' })).toBeVisible()
    expect(screen.queryByText(fullAccountNumber)).not.toBeInTheDocument()
  })

  it.each([
    ['network', new NetworkError('response lost')],
    ['5xx', new ApiError(502, 'UPSTREAM_FAILURE', 'unavailable', '/reveal', 'now')],
  ])('does not automatically retry a %s reveal failure and revalidates before an explicit second reveal', async (_label, failure) => {
    let revealAttempts = 0
    const sequence: string[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/reveal')) {
        sequence.push('reveal')
        revealAttempts += 1
        if (revealAttempts === 1) throw failure
        return destinationReveal()
      }
      sequence.push('case')
      return pendingCase()
    })
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByRole('heading', { name: 'Reveal result unavailable' })).toBeVisible()
    expect(revealCalls()).toHaveLength(1)
    const beforeRetry = sequence.length
    await user.click(screen.getByRole('button', { name: 'Reveal destination' }))
    expect(await screen.findByText(fullAccountNumber)).toBeVisible()
    expect(sequence.slice(beforeRetry)).toEqual(['case', 'reveal'])
    expect(revealCalls()).toHaveLength(2)
  })

  it('keeps a network result unknown even when GET looks disbursed, persists digest-only metadata, and exactly replays on action', async () => {
    let attempts = 0
    let serverLooksDisbursed = false
    const submitted: Record<string, unknown>[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/disbursements')) {
        attempts += 1
        submitted.push({ ...(options as { body: Record<string, unknown> }).body })
        serverLooksDisbursed = true
        if (attempts === 1) throw new NetworkError('response lost')
        return confirmationFixture(true)
      }
      return serverLooksDisbursed ? disbursedCase() : pendingCase()
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user, secretReference.toLowerCase())
    await user.click(screen.getByRole('button', { name: 'Review disbursement confirmation' }))
    const dialog = screen.getByRole('dialog')
    expect(dialog.textContent).not.toContain(secretReference)
    expect(dialog.textContent).not.toContain(fullAccountNumber)
    await user.click(screen.getByRole('button', { name: 'Confirm record' }))

    expect(await screen.findByText(/cannot prove this exact request identity/i)).toBeVisible()
    expect(disbursementPostCalls()).toHaveLength(1)
    const persisted = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(persisted).toContain('DISBURSEMENT_CONFIRMATION')
    expect(persisted).toContain(requestId)
    expect(persisted).toMatch(/[a-f0-9]{64}/)
    expect(persisted).not.toContain(secretReference)
    expect(persisted).not.toContain(fullAccountNumber)
    expect(findUnresolvedOperation('DISBURSEMENT_CONFIRMATION', applicationId)?.semanticPayload)
      .toBeUndefined()

    await user.click(screen.getByRole('button', { name: 'Retry exact operation' }))
    await waitFor(() => expect(disbursementPostCalls()).toHaveLength(2))
    expect(await screen.findByText(/Recovered the previously recorded disbursement/i)).toBeVisible()
    expect(disbursementPostCalls()).toHaveLength(2)
    expect(submitted[1]).toEqual(submitted[0])
    expect(submitted[0]).toEqual({
      requestId,
      expectedContractVersion: 1,
      externalTransferReference: secretReference,
      disbursementValueDate: '2026-09-10',
      firstRepaymentDate: '2026-10-10',
    })
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
    expect(findUnresolvedOperation('DISBURSEMENT_CONFIRMATION', applicationId)).toBeUndefined()
    expect(screen.queryByDisplayValue(secretReference)).not.toBeInTheDocument()
  })

  it.each([500, 502])('treats HTTP %s as result unknown with one POST and preserves request correlation', async (status) => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/disbursements')) {
        throw new ApiError(status, 'INTERNAL_ERROR', 'response failed', '/disbursements', 'now', 'server-correlation')
      }
      return pendingCase()
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review disbursement confirmation' }))
    await user.click(screen.getByRole('button', { name: 'Confirm record' }))

    expect(await screen.findByText(/cannot prove this exact request identity/i)).toBeVisible()
    expect(screen.getByText(/Support reference: server-correlation/i)).toBeVisible()
    expect(disbursementPostCalls()).toHaveLength(1)
  })

  it('reload recovery blocks a different digest and reuses the old UUID after matching re-entry', async () => {
    let attempts = 0
    const submitted: Record<string, unknown>[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (String(path).endsWith('/disbursements')) {
        attempts += 1
        submitted.push({ ...(options as { body: Record<string, unknown> }).body })
        if (attempts === 1) throw new NetworkError('response lost')
        return confirmationFixture(true)
      }
      return attempts > 1 ? disbursedCase() : pendingCase()
    })
    const first = renderPage()
    let user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review disbursement confirmation' }))
    await user.click(screen.getByRole('button', { name: 'Confirm record' }))
    expect(await screen.findByRole('heading', { name: 'Previous confirmation result unknown' })).toBeVisible()
    first.unmount()

    renderPage()
    user = userEvent.setup()
    expect(await screen.findByRole('heading', { name: 'Previous confirmation result unknown' })).toBeVisible()
    await enterEvidence(user, 'DIFFERENT-REFERENCE')
    await user.click(screen.getByRole('button', { name: 'Retry exact operation' }))
    expect(await screen.findByText(/does not match the unresolved operation/i)).toBeVisible()
    expect(disbursementPostCalls()).toHaveLength(1)

    await user.clear(screen.getByLabelText('External transfer reference'))
    await user.type(screen.getByLabelText('External transfer reference'), secretReference)
    await user.click(screen.getByRole('button', { name: 'Retry exact operation' }))
    await waitFor(() => expect(disbursementPostCalls()).toHaveLength(2))
    expect(await screen.findByText(/Recovered the previously recorded disbursement/i)).toBeVisible()
    expect(disbursementPostCalls()).toHaveLength(2)
    expect(submitted[1]).toEqual(submitted[0])
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
  })

  it('does not POST again after definite success when durable refresh fails', async () => {
    let posted = false
    let readsAvailable = true
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/disbursements')) {
        posted = true
        readsAvailable = false
        return confirmationFixture()
      }
      if (posted && !readsAvailable) throw new NetworkError('refresh unavailable')
      return posted ? disbursedCase() : pendingCase()
    })
    renderPage()
    const user = userEvent.setup()
    await enterEvidence(user)
    await user.click(screen.getByRole('button', { name: 'Review disbursement confirmation' }))
    await user.click(screen.getByRole('button', { name: 'Confirm record' }))

    expect(await screen.findByText(/Disbursement confirmed; refreshed state unavailable/i)).toBeVisible()
    expect(disbursementPostCalls()).toHaveLength(1)
    readsAvailable = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText(/confirmed disbursement is now reconciled/i)).toBeVisible()
    expect(disbursementPostCalls()).toHaveLength(1)
  })

  it('renders durable activation and final schedule without servicing actions', async () => {
    vi.mocked(api.apiRequest).mockResolvedValue(disbursedCase())
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Activation result' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Final repayment schedule' })).toBeVisible()
    expect(screen.getByText('LA-33333333333343338333333333333333')).toBeVisible()
    expect(screen.queryByRole('button', { name: /repay|settle|close|payoff/i })).not.toBeInTheDocument()
    expect(document.body.textContent).not.toContain(secretReference)
    expect(document.body.textContent).not.toContain(fullAccountNumber)
  })
})
