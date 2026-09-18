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
import { NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'

vi.mock('@/features/auth/api/auth-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/auth/api/auth-api')>('@/features/auth/api/auth-api')
  return { ...actual, refresh: vi.fn(), logout: vi.fn() }
})

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return { ...actual, apiRequest: vi.fn() }
})

const applicationId = '11111111-1111-4111-8111-111111111111'
const itemId = '22222222-2222-4222-8222-222222222222'
const currentVersionId = '33333333-3333-4333-8333-333333333333'
const historicalVersionId = '44444444-4444-4444-8444-444444444444'

const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-04T10:00:00Z',
  userId: '55555555-5555-4555-8555-555555555555', email: 'reviewer@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'], permissions: ['document:review'],
}

const version = (documentVersionId: string, versionNumber: number) => ({
  documentVersionId,
  versionNumber,
  originalFilename: `evidence-${versionNumber}.pdf`,
  detectedMimeType: 'application/pdf',
  byteSize: 128,
  uploadedAt: '2026-09-04T08:00:00',
})

function fixture(evidenceStatus: string, overrides: Record<string, unknown> = {}) {
  const current = version(currentVersionId, 2)
  return {
    loanApplicationId: applicationId,
    applicationStatus: 'UNDER_REVIEW',
    checklistStage: 'SUBMISSION',
    uploadComplete: true,
    processingReady: evidenceStatus === 'ACCEPTED' || evidenceStatus === 'WAIVED',
    items: [{
      checklistItemId: itemId,
      documentType: 'BANK_STATEMENT',
      requirementStatus: 'REQUIRED',
      evidenceStatus,
      uploadComplete: true,
      processingReady: evidenceStatus === 'ACCEPTED' || evidenceStatus === 'WAIVED',
      currentVersion: current,
      versionHistory: [version(historicalVersionId, 1), current],
      reviewHistory: evidenceStatus === 'AWAITING_REVIEW' || evidenceStatus === 'FUTURE_REVIEW_STATE'
        ? []
        : [{
            documentVersionId: currentVersionId,
            outcome: evidenceStatus === 'ACCEPTED' ? 'ACCEPT_DOCUMENT'
              : evidenceStatus === 'WAIVED' ? 'WAIVE_DOCUMENT' : 'REQUEST_REPLACEMENT',
            waiverReasonCode: evidenceStatus === 'WAIVED' ? 'DOCUMENT_NOT_APPLICABLE' : null,
            decidedAt: '2026-09-04T08:30:00',
          }],
    }],
    ...overrides,
  }
}

function renderDocumentWorkspace(selectedVersionId = currentVersionId) {
  const router = createTestRouter([
    `/staff/applications/${applicationId}/documents?checklistItemId=${itemId}&documentVersionId=${selectedVersionId}`,
  ])
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider><RouterProvider router={router} /></AuthProvider>
    </QueryClientProvider>,
  )
}

function renderWorkspace(
  evidenceStatus: string,
  selectedVersionId = currentVersionId,
  overrides: Record<string, unknown> = {},
) {
  vi.mocked(api.apiRequest).mockResolvedValue(fixture(evidenceStatus, overrides))
  return renderDocumentWorkspace(selectedVersionId)
}

describe('Staff document workspace review eligibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
  })

  it('offers review only for the authoritative awaiting-review current version', async () => {
    renderWorkspace('AWAITING_REVIEW')

    expect(await screen.findByRole('heading', { name: 'Review outcome' })).toBeVisible()
  })

  it.each(['ACCEPTED', 'WAIVED', 'REPLACEMENT_REQUESTED', 'FUTURE_REVIEW_STATE'])(
    'keeps a current %s evidence value read-only',
    async (evidenceStatus) => {
      renderWorkspace(evidenceStatus)

      expect(await screen.findByRole('heading', { name: 'evidence-2.pdf' })).toBeVisible()
      expect(screen.queryByRole('heading', { name: 'Review outcome' })).not.toBeInTheDocument()
    },
  )

  it('keeps a historical version read-only even while current evidence awaits review', async () => {
    renderWorkspace('AWAITING_REVIEW', historicalVersionId)

    expect(await screen.findByRole('heading', { name: 'Historical version selected' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Review outcome' })).not.toBeInTheDocument()
  })

  it('shows initial upload only for assisted documents-pending applications with the exact permission', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff, permissions: ['document:review', 'document:upload:assisted'],
    })
    renderWorkspace('AWAITING_REVIEW', currentVersionId, {
      originationChannel: 'STAFF_ASSISTED', applicationStatus: 'DOCUMENTS_PENDING',
    })

    expect(await screen.findByLabelText('Upload BANK_STATEMENT')).toBeVisible()
  })

  it.each([
    ['CUSTOMER_DIGITAL', 'DOCUMENTS_PENDING', ['document:review', 'document:upload:assisted']],
    ['STAFF_ASSISTED', 'SUBMITTED', ['document:review', 'document:upload:assisted']],
    ['STAFF_ASSISTED', 'DOCUMENTS_PENDING', ['document:review']],
  ])('hides assisted upload for channel %s, status %s, permissions %s', async (
    originationChannel, applicationStatus, permissions,
  ) => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions })
    renderWorkspace('AWAITING_REVIEW', currentVersionId, { originationChannel, applicationStatus })

    expect(await screen.findByRole('heading', { name: 'evidence-2.pdf' })).toBeVisible()
    expect(screen.queryByLabelText('Upload BANK_STATEMENT')).not.toBeInTheDocument()
  })

  it('reuses the request identity and original version baseline after a lost upload response', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff, permissions: ['document:review', 'document:upload:assisted'],
    })
    const uploads: FormData[] = []
    vi.mocked(api.apiRequest).mockImplementation(async (path, options) => {
      if (path === `/staff/loan-applications/${applicationId}/documents/${itemId}/versions`) {
        uploads.push((options as { body: FormData }).body)
        if (uploads.length === 1) throw new NetworkError()
        return {}
      }
      return fixture('AWAITING_REVIEW', {
        originationChannel: 'STAFF_ASSISTED', applicationStatus: 'DOCUMENTS_PENDING',
      })
    })
    const user = userEvent.setup()
    renderDocumentWorkspace()
    const input = await screen.findByLabelText('Upload BANK_STATEMENT')
    const file = new File(['same application evidence'], 'income.pdf', { type: 'application/pdf' })
    await user.upload(input, file)
    fireEvent.submit(input.closest('form')!)
    expect(await screen.findByText(/upload result is unknown/i)).toBeVisible()
    fireEvent.submit(input.closest('form')!)
    await waitFor(() => expect(uploads).toHaveLength(2))

    expect(uploads[1]!.get('uploadRequestId')).toBe(uploads[0]!.get('uploadRequestId'))
    expect(uploads[0]!.get('expectedCurrentVersionId')).toBe(currentVersionId)
    expect(uploads[1]!.get('expectedCurrentVersionId')).toBe(currentVersionId)
  })

  it('does not send changed content while an application upload result is unresolved', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff, permissions: ['document:review', 'document:upload:assisted'],
    })
    let uploads = 0
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path === `/staff/loan-applications/${applicationId}/documents/${itemId}/versions`) {
        uploads += 1
        throw new NetworkError()
      }
      return fixture('AWAITING_REVIEW', {
        originationChannel: 'STAFF_ASSISTED', applicationStatus: 'DOCUMENTS_PENDING',
      })
    })
    const user = userEvent.setup()
    renderDocumentWorkspace()
    const input = await screen.findByLabelText('Upload BANK_STATEMENT')
    await user.upload(input, new File(['first'], 'income.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)
    expect(await screen.findByText(/upload result is unknown/i)).toBeVisible()
    await user.upload(input, new File(['changed'], 'income.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)

    expect(await screen.findByText(/differs from the unresolved upload/i)).toBeVisible()
    expect(uploads).toBe(1)
    const stored = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(stored).not.toContain('income.pdf')
    expect(stored).not.toContain('first')
  })
})
