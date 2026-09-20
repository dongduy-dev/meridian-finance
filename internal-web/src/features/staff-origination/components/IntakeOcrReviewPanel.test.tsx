import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import { createQueryClient } from '@/lib/query/query-client'
import { originationKeys } from '../api/queries'
import { IntakeOcrReviewPanel } from './IntakeOcrReviewPanel'

const caseId = '11111111-1111-4111-8111-111111111111'
const versionId = '33333333-3333-4333-8333-333333333333'
const resultId = '55555555-5555-4555-8555-555555555555'
const base = `/staff/assisted-originations/${caseId}/evidence/UCL_PAPER_APPLICATION/versions/${versionId}/ocr`

const job = (state: string, overrides: Record<string, unknown> = {}) => ({
  ocrJobId: '44444444-4444-4444-8444-444444444444',
  intakeDocumentVersionId: versionId, state, disposition: state === 'COMPLETED' ? 'PENDING_REVIEW' : null,
  attemptCount: 1, failureCategory: null, createdAt: '2026-09-20T08:00:00',
  updatedAt: '2026-09-20T08:01:00', completedAt: state === 'COMPLETED' ? '2026-09-20T08:01:00' : null,
  failedAt: null, ...overrides,
})

const projection = (overrides: Record<string, unknown> = {}) => ({
  ocrResultId: resultId, evidenceType: 'UCL_PAPER_APPLICATION', disposition: 'PENDING_REVIEW',
  suggestions: [{ fieldName: 'fullName', proposedValue: 'OCR Applicant', confidence: 0.93 }],
  reviewedFields: {}, reviewedAt: null, ...overrides,
})

function renderPanel(protectedRequest: ReturnType<typeof vi.fn>) {
  const client = createQueryClient()
  const manager = { protectedRequest } as unknown as AuthSessionManager
  const view = render(<QueryClientProvider client={client}><IntakeOcrReviewPanel
    manager={manager} caseId={caseId} evidenceType="UCL_PAPER_APPLICATION"
    versionId={versionId} intakeOpen
  /></QueryClientProvider>)
  return { client, ...view }
}

describe('IntakeOcrReviewPanel', () => {
  beforeEach(() => sessionStorage.clear())

  it('starts explicit extraction and polls only the active exact job', async () => {
    let statusReads = 0
    const request = vi.fn(async (path: string, options?: { method?: string }) => {
      if (path === base && !options?.method) {
        statusReads += 1
        if (statusReads === 1) throw new ApiError(404, 'OCR_JOB_NOT_FOUND', 'Missing', path, '2026-09-20T08:00:00Z')
        return job('PROCESSING')
      }
      if (path === base && options?.method === 'POST') return job('PENDING')
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderPanel(request)

    await user.click(await screen.findByRole('button', { name: 'Extract fields' }))
    expect(await screen.findByText(/field extraction pending/i)).toBeVisible()
    expect(request.mock.calls.filter((call) => call[1]?.method === 'POST')).toHaveLength(1)
    await waitFor(() => expect(statusReads).toBeGreaterThan(1), { timeout: 2_500 })
  })

  it('keeps manual intake available after a controlled OCR failure', async () => {
    const request = vi.fn().mockResolvedValue(job('FAILED', {
      failureCategory: 'PROVIDER_UNAVAILABLE', failedAt: '2026-09-20T08:01:00',
    }))
    renderPanel(request)

    expect(await screen.findByText(/OCR failed: PROVIDER_UNAVAILABLE/i)).toBeVisible()
    expect(screen.getByText(/Continue the manual intake workflow/i)).toBeVisible()
  })

  it('renders confidence, submits Staff corrections, excludes consent, and drops sensitive cache on unmount', async () => {
    let submitted: unknown
    const request = vi.fn(async (path: string, options?: { method?: string; body?: unknown }) => {
      if (path === base) return job('COMPLETED')
      if (path === `${base}/review` && !options?.method) return projection()
      if (path === `${base}/review` && options?.method === 'POST') {
        submitted = options.body
        return projection({
          disposition: 'REVIEWED', suggestions: [],
          reviewedFields: { fullName: 'Corrected Applicant' }, reviewedAt: '2026-09-20T08:05:00',
        })
      }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    const { client, unmount } = renderPanel(request)

    const fullName = await screen.findByDisplayValue('OCR Applicant')
    expect(screen.getByText(/OCR: OCR Applicant · 93%/)).toBeVisible()
    expect(screen.queryByLabelText(/consent/i)).not.toBeInTheDocument()
    await user.clear(fullName)
    await user.type(fullName, 'Corrected Applicant')
    await user.click(screen.getByRole('button', { name: 'Confirm final OCR review' }))

    expect(await screen.findByText('Final OCR review completed.')).toBeVisible()
    expect(submitted).toMatchObject({
      expectedOcrResultId: resultId,
      reviewedFields: { fullName: 'Corrected Applicant' },
    })
    expect(sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? '').not.toContain('Corrected Applicant')
    unmount()
    await waitFor(() => expect(client.getQueryData(
      originationKeys.ocrReview(caseId, 'UCL_PAPER_APPLICATION', versionId),
    )).toBeUndefined())
  })

  it('reconciles a lost review response through GET without repeating POST or persisting values', async () => {
    let reviewReads = 0
    let posts = 0
    const request = vi.fn(async (path: string, options?: { method?: string }) => {
      if (path === base) return job('COMPLETED')
      if (path === `${base}/review` && !options?.method) {
        reviewReads += 1
        return reviewReads === 1 ? projection() : projection({
          disposition: 'REVIEWED', suggestions: [],
          reviewedFields: { fullName: 'Recovered Applicant' }, reviewedAt: '2026-09-20T08:05:00',
        })
      }
      if (path === `${base}/review` && options?.method === 'POST') {
        posts += 1
        throw new NetworkError()
      }
      throw new Error(`Unexpected request ${path}`)
    })
    const user = userEvent.setup()
    renderPanel(request)

    const fullName = await screen.findByDisplayValue('OCR Applicant')
    await user.clear(fullName)
    await user.type(fullName, 'Recovered Applicant')
    await user.click(screen.getByRole('button', { name: 'Confirm final OCR review' }))

    expect(await screen.findByText(/confirmed from the authoritative result/i)).toBeVisible()
    expect(posts).toBe(1)
    expect(sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? '').not.toContain('Recovered Applicant')
  })
})
