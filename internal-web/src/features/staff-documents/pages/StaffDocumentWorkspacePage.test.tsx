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
            reviewDecisionId: '99999999-9999-4999-8999-999999999999',
            documentVersionId: currentVersionId,
            correctionReasonCode: null, customerInstruction: null, reviewer: null,
            restrictedStaffNoteReadable: true, restrictedStaffNotes: null,
            outcome: evidenceStatus === 'ACCEPTED' ? 'ACCEPT_DOCUMENT'
              : evidenceStatus === 'WAIVED' ? 'WAIVE_DOCUMENT' : 'REQUEST_REPLACEMENT',
            waiverReasonCode: evidenceStatus === 'WAIVED' ? 'DOCUMENT_NOT_APPLICABLE' : null,
            decidedAt: '2026-09-04T08:30:00',
          }],
    }],
    ...overrides,
  }
}

const collateralCase = {
  loanApplicationId: applicationId,
  applicationNumber: 'COL-20260904-000001',
  productCode: 'COLLATERAL_LOAN',
  productType: 'SECURED',
  originationChannel: 'CUSTOMER_DIGITAL',
  requestedAmount: 100000000,
  requestedTermMonths: 12,
  status: 'UNDER_REVIEW',
  submittedAt: '2026-09-04T08:00:00',
  customerReadiness: { active: true, profileComplete: true, hasPrimaryActiveBankAccount: true, verificationStatus: 'VERIFIED' },
  customerContext: null,
  collateralContext: { collateralType: 'CAR', description: 'Vehicle', estimatedValue: 3200000000,
    ownershipStatus: 'Owner', conditionNote: 'Very good' },
  formalReviewRecorded: false,
  assignedLoanOfficer: null,
  lifecycleHistory: [],
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
    expect(screen.getByRole('heading', { name: 'Application', level: 1 })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Documents' })).toHaveAttribute('aria-current', 'page')
    expect(screen.queryByRole('link', { name: 'Overview' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Copy application ID' })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.map(([path]) => path)).toEqual([`/staff/loan-applications/${applicationId}/documents`])
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

  it('shows collateral facts beside the selected ownership evidence and review form', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: ['document:review', 'loan:read'] })
    const documentCase = fixture('AWAITING_REVIEW')
    documentCase.items[0]!.documentType = 'COLLATERAL_OWNERSHIP_EVIDENCE'
    vi.mocked(api.apiRequest).mockImplementation(async (path) => path.endsWith('/documents') ? documentCase : collateralCase)
    renderDocumentWorkspace()

    expect(await screen.findByText('Submitted collateral facts')).toBeVisible()
    expect(screen.getByRole('heading', { name: collateralCase.applicationNumber, level: 1 })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Documents' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Vehicle')).toBeVisible()
    expect(screen.getByText('Very good')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'evidence-2.pdf' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Review outcome' })).toBeVisible()
  })

  it('keeps ownership-evidence review independent of loan:read', async () => {
    const documentCase = fixture('AWAITING_REVIEW')
    documentCase.items[0]!.documentType = 'COLLATERAL_OWNERSHIP_EVIDENCE'
    vi.mocked(api.apiRequest).mockResolvedValue(documentCase)
    renderDocumentWorkspace()
    expect(await screen.findByRole('heading', { name: 'Review outcome' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Application', level: 1 })).toBeVisible()
    expect(screen.queryByText('Submitted collateral facts')).not.toBeInTheDocument()
    expect(vi.mocked(api.apiRequest).mock.calls.map(([path]) => path)).toEqual([`/staff/loan-applications/${applicationId}/documents`])
  })

  it('does not show collateral context for another document type', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: ['document:review', 'loan:read'] })
    vi.mocked(api.apiRequest).mockImplementation(async (path) => path.endsWith('/documents') ? fixture('AWAITING_REVIEW') : collateralCase)
    renderDocumentWorkspace()

    expect(await screen.findByRole('heading', { name: 'Review outcome' })).toBeVisible()
    expect(screen.queryByText('Submitted collateral facts')).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: collateralCase.applicationNumber, level: 1 })).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path]) => path === `/staff/loan-applications/${applicationId}`)).toBe(true)
  })

  it('keeps document review available when supplemental application context fails', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, permissions: ['document:review', 'loan:read'] })
    const documentCase = fixture('AWAITING_REVIEW')
    documentCase.items[0]!.documentType = 'COLLATERAL_OWNERSHIP_EVIDENCE'
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path.endsWith('/documents')) return documentCase
      throw new ApiError(503, 'SYSTEM_STATE_CONFLICT', 'unsafe detail', path, '2026-09-04T08:00:00Z', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
    })
    renderDocumentWorkspace()

    expect(await screen.findByRole('heading', { name: 'Review outcome' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Application context unavailable' }, { timeout: 5000 })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Application', level: 1 })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Documents' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('heading', { name: 'evidence-2.pdf' })).toBeVisible()
  })

  it('shows initial upload only for assisted documents-pending applications with the exact permission', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({
      ...staff, permissions: ['document:review', 'document:upload:assisted'],
    })
    renderWorkspace('AWAITING_REVIEW', currentVersionId, {
      originationChannel: 'STAFF_ASSISTED', applicationStatus: 'DOCUMENTS_PENDING',
    })

    expect(await screen.findByLabelText('Upload Bank statement')).toBeVisible()
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
    expect(screen.queryByLabelText('Upload Bank statement')).not.toBeInTheDocument()
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
    const input = await screen.findByLabelText('Upload Bank statement')
    const file = new File(['same application evidence'], 'income.pdf', { type: 'application/pdf' })
    await user.upload(input, file)
    fireEvent.submit(input.closest('form')!)
    expect(await screen.findByText(/could not confirm the upload/i)).toBeVisible()
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
    const input = await screen.findByLabelText('Upload Bank statement')
    await user.upload(input, new File(['first'], 'income.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)
    expect(await screen.findByText(/could not confirm the upload/i)).toBeVisible()
    await user.upload(input, new File(['changed'], 'income.pdf', { type: 'application/pdf' }))
    fireEvent.submit(input.closest('form')!)

    expect(await screen.findByText(/selected file or original document version differs from the unresolved upload/i)).toBeVisible()
    expect(uploads).toBe(1)
    const stored = sessionStorage.getItem('meridian.staff.unresolved-operations.v1') ?? ''
    expect(stored).not.toContain('income.pdf')
    expect(stored).not.toContain('first')
  })
})


describe('Document provenance audiences and signed forms', () => {
  beforeEach(() => { vi.clearAllMocks(); sessionStorage.clear(); vi.mocked(authApi.refresh).mockResolvedValue(staff) })

  it('lets an Approver inspect historical content and provenance with no review or upload controls', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue({ ...staff, roles: ['APPROVER'], permissions: ['approval:decide', 'document:upload:assisted', 'document:waive'] })
    const base = fixture('AWAITING_REVIEW')
    const value = { ...base, items: [{ ...base.items[0]!, reviewHistory: [{ reviewDecisionId: '99999999-9999-4999-8999-999999999999', documentVersionId: historicalVersionId,
      outcome: 'REQUEST_REPLACEMENT', waiverReasonCode: null, correctionReasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED', customerInstruction: 'Provide all pages',
      reviewer: { userId: '00000000-0000-0000-0000-000000000302', displayName: 'Deni Loan Officer', email: 'deni@meridian.local' },
      decidedAt: '2026-09-04T08:30:00', restrictedStaffNoteReadable: true, restrictedStaffNotes: 'Internal document assessment' }] }] }
    vi.mocked(api.apiRequest).mockResolvedValue(value)
    renderDocumentWorkspace(historicalVersionId)
    expect(await screen.findByText('Deni Loan Officer')).toBeVisible()
    expect(screen.getByText('Provide all pages')).toBeVisible()
    expect(screen.getByText('Internal document assessment')).toBeVisible()
    expect(screen.getByRole('button', { name: 'View document' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Review outcome' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Upload Bank statement')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Document review' })).not.toBeInTheDocument()
    vi.mocked(api.apiRequest).mockRejectedValueOnce(new NetworkError())
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    expect(await screen.findByText('Viewer unavailable')).toBeVisible()
    expect(api.apiRequest).toHaveBeenLastCalledWith(`/staff/loan-applications/${applicationId}/documents/${itemId}/versions/${historicalVersionId}/content`, expect.objectContaining({ responseType: 'blob' }))
  })

  it('shows all three signed form histories separately and opens the selected historical version', async () => {
    const value = fixture('ACCEPTED', { originationChannel: 'STAFF_ASSISTED' })
    const signed = ['CUSTOMER_OFFER_RESPONSE', 'CUSTOMER_CONTRACT_ACKNOWLEDGMENT', 'CUSTOMER_CANCELLATION_REQUEST'].map((evidenceType, index) => ({
      documentId: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa${index}`, evidenceType,
      approvedOfferId: index === 0 ? itemId : null, declaredOfferDecision: index === 0 ? 'ACCEPT' : null,
      loanContractId: index === 1 ? itemId : null, contractVersion: index === 1 ? 2 : null, correctionRequestId: index === 2 ? itemId : null,
      currentVersion: version(currentVersionId, 2), versionHistory: [version(historicalVersionId, 1), version(currentVersionId, 2)],
    }))
    vi.mocked(api.apiRequest).mockImplementation(async (path) => path.endsWith('/assisted-action-evidence') ? signed : value)
    renderDocumentWorkspace()
    expect(await screen.findByText('Customer cancellation request')).toBeVisible()
    expect(screen.getByText('Customer offer response')).toBeVisible()
    expect(screen.getByText('Customer decision: Accepted')).toBeVisible()
    expect(screen.getByText('Customer contract acknowledgment')).toBeVisible()
    await userEvent.setup().selectOptions(screen.getAllByLabelText('Signed evidence version')[0]!, historicalVersionId)
    expect(screen.getByText('Historical signed evidence selected')).toBeVisible()
    vi.mocked(api.apiRequest).mockRejectedValueOnce(new NetworkError())
    await userEvent.setup().click(screen.getAllByRole('button', { name: 'View signed evidence' })[0]!)
    expect(await screen.findByText('Viewer unavailable')).toBeVisible()
    expect(api.apiRequest).toHaveBeenLastCalledWith(`/staff/loan-applications/${applicationId}/assisted-action-evidence/CUSTOMER_OFFER_RESPONSE/versions/${historicalVersionId}/content`, expect.objectContaining({ responseType: 'blob' }))
    expect(screen.queryByRole('button', { name: /Upload signed|Record Customer/ })).not.toBeInTheDocument()
  })

  it('retries a signed metadata failure without losing the checklist', async () => {
    let recovered = false
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (!path.endsWith('/assisted-action-evidence')) return fixture('ACCEPTED', { originationChannel: 'STAFF_ASSISTED' })
      if (!recovered) throw new NetworkError()
      return []
    })
    renderDocumentWorkspace()
    expect(await screen.findByText('Meridian could not load reliable details. Try again before taking action.', {}, { timeout: 5000 })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'evidence-2.pdf' })).toBeVisible()
    recovered = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No signed Customer action evidence exists.')).toBeVisible()
  })
})
