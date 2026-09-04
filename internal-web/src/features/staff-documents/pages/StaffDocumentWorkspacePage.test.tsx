import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
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

function fixture(evidenceStatus: string) {
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
  }
}

function renderWorkspace(evidenceStatus: string, selectedVersionId = currentVersionId) {
  vi.mocked(api.apiRequest).mockResolvedValue(fixture(evidenceStatus))
  const router = createTestRouter([
    `/staff/applications/${applicationId}/documents?checklistItemId=${itemId}&documentVersionId=${selectedVersionId}`,
  ])
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider><RouterProvider router={router} /></AuthProvider>
    </QueryClientProvider>,
  )
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
})
