import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestRouter } from '@/app/router/router'
import type { AuthResponse } from '@/features/auth/api/auth-api'
import * as authApi from '@/features/auth/api/auth-api'
import { AuthProvider } from '@/features/auth/model/auth-context'
import * as api from '@/lib/api'
import { NetworkError } from '@/lib/api'
import {
  bindUnresolvedOperations,
  digestFile,
  digestOperationPayload,
  saveUnresolvedOperation,
} from '@/lib/operation/unresolved-operation'
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
const requestId = '22222222-2222-4222-8222-222222222222'
const taskId = '33333333-3333-4333-8333-333333333333'
const itemId = '44444444-4444-4444-8444-444444444444'
const operationId = '55555555-5555-4555-8555-555555555555'
const baselineId = '77777777-7777-4777-8777-777777777777'
const cancellationEvidenceVersionId = '88888888-8888-4888-8888-888888888888'
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-04T10:00:00Z',
  userId: '66666666-6666-4666-8666-666666666666', email: 'staff@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'],
  permissions: ['loan:correction:staff', 'document:upload:staff', 'document:upload:assisted-correction'],
}
const cancellationStaff: AuthResponse = {
  ...staff,
  permissions: [
    ...staff.permissions,
    'loan:cancel:staff',
    'document:upload:assisted-action',
  ],
}

function caseFixture(proofState: string, assisted = false, withCancellationEvidence = false) {
  return {
    loanApplicationId: applicationId,
    applicationNumber: 'UCL-20260904-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    originationChannel: assisted ? 'STAFF_ASSISTED' : 'CUSTOMER_DIGITAL',
    applicationStatus: 'RETURNED_FOR_REVISION',
    correctionHistory: [],
    correctionRequest: {
      correctionRequestId: requestId,
      status: 'OPEN',
      reasonCode: 'DOCUMENT_REVIEW_REQUIRED',
      createdAt: '2026-09-04T08:00:00',
      makerCheckerBlockedForCurrentActor: false,
      allTasksComplete: false,
      staffResubmissionReady: false,
      tasks: [{
        taskId,
        responsibleParty: assisted ? 'CUSTOMER' : 'STAFF',
        status: 'OPEN',
        scope: assisted ? 'DOCUMENT_REPLACEMENT' : 'SUPPORTING_DOCUMENT_UPLOAD',
        documentType: 'BANK_STATEMENT',
        checklistItemId: itemId,
        baselineDocumentVersionId: assisted ? baselineId : null,
        reasonCode: 'DOCUMENT_REVIEW_REQUIRED',
        customerInstruction: assisted ? 'Provide a clearer current bank statement.' : null,
        staffInstruction: assisted ? null : 'Upload supporting evidence.',
        createdAt: '2026-09-04T08:00:00',
        completedAt: null,
        proofState,
        customerSourceViaStaff: assisted,
        uploadActionAvailable: true,
        completionActionAvailable: true,
      }],
    },
    assistedCancellation: assisted ? {
      available: true,
      correctionRequestId: requestId,
      evidence: withCancellationEvidence ? {
        documentId: '99999999-9999-4999-8999-999999999999',
        documentVersionId: cancellationEvidenceVersionId,
        evidenceType: 'CUSTOMER_CANCELLATION_REQUEST',
        declaredOfferDecision: null,
        targetId: requestId,
        targetVersion: null,
        versionNumber: 1,
        detectedMimeType: 'application/pdf',
        byteSize: 120,
        uploadedAt: '2026-09-04T08:30:00',
      } : null,
      evidenceUploadAvailable: true,
      cancellationCommandAvailable: withCancellationEvidence,
      completedCancellation: null,
    } : {
      available: false,
      correctionRequestId: null,
      evidence: null,
      evidenceUploadAvailable: false,
      cancellationCommandAvailable: false,
      completedCancellation: null,
    },
  }
}

describe('Staff correction operation recovery', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(authApi.refresh).mockResolvedValue(staff)
    await bindUnresolvedOperations(staff)
  })

  it('shows completed Customer-requested cancellation and the Staff recorder without command controls', async () => {
    const value = caseFixture('SATISFIED', true, true)
    vi.mocked(api.apiRequest).mockResolvedValue({
      ...value, applicationStatus: 'CANCELLED',
      correctionRequest: { ...value.correctionRequest, status: 'CANCELLED', tasks: [] },
      assistedCancellation: {
        ...value.assistedCancellation, available: false, evidenceUploadAvailable: false,
        cancellationCommandAvailable: false,
        completedCancellation: { action: 'CUSTOMER_REQUESTED_CANCELLATION',
          recordedBy: { userId: '00000000-0000-0000-0000-000000000302', displayName: 'Deni Loan Officer', email: 'deni@meridian.local' },
          recordedAt: '2026-09-04T09:00:00', evidence: value.assistedCancellation.evidence },
      },
    })
    renderWorkspace()
    expect(await screen.findByText('Customer-requested cancellation recorded')).toBeVisible()
    expect(screen.getByText('Customer request')).toBeVisible()
    expect(screen.getByText('Customer request recorded from signed evidence')).toBeVisible()
    expect(screen.queryByText('Customer decision recorded from signed evidence')).not.toBeInTheDocument()
    expect(screen.getByText('Recorded by')).toBeVisible()
    expect(screen.getByText('Deni Loan Officer')).toBeVisible()
    expect(screen.getByRole('button', { name: 'View signed request' })).toBeVisible()
    vi.mocked(api.apiRequest).mockRejectedValueOnce(new NetworkError())
    await userEvent.setup().click(screen.getByRole('button', { name: 'View signed request' }))
    expect(await screen.findByText('Viewer unavailable')).toBeVisible()
    expect(api.apiRequest).toHaveBeenLastCalledWith(
      `/staff/loan-applications/${applicationId}/assisted-action-evidence/CUSTOMER_CANCELLATION_REQUEST/versions/${cancellationEvidenceVersionId}/content`,
      expect.objectContaining({ responseType: 'blob' }),
    )
    expect(screen.queryByRole('button', { name: 'Review Customer-requested cancellation' })).not.toBeInTheDocument()
    expect(screen.queryByText('Customer — self-service')).not.toBeInTheDocument()
  })

  it('blocks a changed Staff upload while the previous result is unresolved', async () => {
    const priorFile = new File(['prior exact bytes'], 'prior.pdf', { type: 'application/pdf' })
    saveUnresolvedOperation({
      type: 'STAFF_UPLOAD', resource: `upload:${taskId}`, operationId,
      payloadDigest: await digestOperationPayload({
        taskId, baseline: null, fileHash: await digestFile(priorFile),
      }),
      unresolvedAt: '2026-09-04T08:15:00Z',
    })
    vi.mocked(api.apiRequest).mockResolvedValue(caseFixture('MISSING'))
    renderWorkspace()
    const user = userEvent.setup()

    const identity = await screen.findByRole('heading', { name: 'UCL-20260904-000001', level: 1 })
    await waitFor(() => expect(identity).toHaveFocus())
    expect(within(screen.getByRole('navigation', { name: 'Application sections' })).getByRole('link', { name: 'Corrections', current: 'page' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Unsecured Consumer Loan')).toBeVisible()
    await user.upload(
      screen.getByLabelText('Upload proof'),
      new File(['different bytes'], 'different.pdf', { type: 'application/pdf' }),
    )
    await user.click(screen.getByRole('button', { name: 'Upload Staff document' }))

    expect(await screen.findByText(/previous action result is still not confirmed/i)).toBeVisible()
    expect(vi.mocked(api.apiRequest).mock.calls.some(([path, options]) =>
      String(path).endsWith('/versions') && (options as RequestInit | undefined)?.method === 'POST')).toBe(false)
  })

  it('reuses the completionRequestId for the same task after restoration', async () => {
    saveUnresolvedOperation({
      type: 'TASK_COMPLETION', resource: `complete:${taskId}`, operationId,
      payloadDigest: await digestOperationPayload({ taskId }),
      unresolvedAt: '2026-09-04T08:15:00Z',
    })
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith(`/staff-corrections/tasks/${taskId}/complete`)) {
        return {
          taskId, correctionRequestId: requestId, loanApplicationId: applicationId,
          status: 'COMPLETED', scope: 'SUPPORTING_DOCUMENT_UPLOAD',
          documentType: 'BANK_STATEMENT', checklistItemId: itemId,
          baselineDocumentVersionId: null, reasonCode: 'DOCUMENT_REVIEW_REQUIRED',
          staffInstruction: 'Upload supporting evidence.', createdAt: '2026-09-04T08:00:00',
          completedAt: '2026-09-04T08:30:00',
        }
      }
      return caseFixture('SATISFIED')
    })
    renderWorkspace()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Complete Staff task' }))

    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.some(([path, options]) =>
      String(path).endsWith(`/staff-corrections/tasks/${taskId}/complete`)
      && (options as { body?: { completionRequestId?: string } } | undefined)?.body?.completionRequestId === operationId,
    )).toBe(true))
  })

  it('preserves confirmed Staff resubmission and reconciles its request', async () => {
    let submitted = false
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (path.endsWith('/resubmit')) {
        submitted = true
        return { loanApplicationId: applicationId, status: 'SUBMITTED' }
      }
      const value = caseFixture('SATISFIED')
      return { ...value, applicationStatus: submitted ? 'SUBMITTED' : value.applicationStatus,
        correctionRequest: { ...value.correctionRequest, allTasksComplete: true,
          staffResubmissionReady: !submitted, status: submitted ? 'RESUBMITTED' : 'OPEN',
          tasks: value.correctionRequest.tasks.map((task) => ({ ...task, status: 'COMPLETED' })) } }
    })
    renderWorkspace()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Review Staff resubmission' }))
    expect(screen.getByRole('dialog', { name: 'Confirm Staff resubmission' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Confirm resubmission' }))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Review Staff resubmission' })).not.toBeInTheDocument())
    const posts = vi.mocked(api.apiRequest).mock.calls.filter(([path, options]) =>
      path.endsWith('/resubmit') && (options as RequestInit)?.method === 'POST')
    expect(posts).toHaveLength(1)
    expect((posts[0]![1] as { body: { resubmissionRequestId: string } }).body.resubmissionRequestId).toMatch(/^[0-9a-f-]{36}$/)
    expect(within(screen.getByRole('navigation', { name: 'Application sections' })).getByRole('link', { name: 'Corrections', current: 'page' })).toBeVisible()
  })

  it('labels an assisted Customer task and reuses its completion identity on the purpose-specific route', async () => {
    saveUnresolvedOperation({
      type: 'TASK_COMPLETION', resource: `complete:${taskId}`, operationId,
      payloadDigest: await digestOperationPayload({ taskId }),
      unresolvedAt: '2026-09-04T08:15:00Z',
    })
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith(`/staff-corrections/loan-applications/${applicationId}/customer-tasks/${taskId}/complete`)) {
        return { status: 'COMPLETED' }
      }
      return caseFixture('SATISFIED', true)
    })
    renderWorkspace()
    const user = userEvent.setup()

    expect(await screen.findByText(/Customer-sourced task · Staff records Customer-provided evidence/)).toBeVisible()
    expect(screen.getByText('Provide a clearer current bank statement.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Record Customer task complete' }))

    await waitFor(() => expect(vi.mocked(api.apiRequest).mock.calls.some(([path, options]) =>
      String(path).endsWith(`/staff-corrections/loan-applications/${applicationId}/customer-tasks/${taskId}/complete`)
      && (options as { body?: { completionRequestId?: string } } | undefined)?.body?.completionRequestId === operationId,
    )).toBe(true))
  })

  it('uploads assisted Customer evidence against the exact baseline version', async () => {
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith('/versions')) return { documentVersionId: operationId }
      return caseFixture('MISSING', true)
    })
    renderWorkspace()
    const user = userEvent.setup()

    await user.upload(
      await screen.findByLabelText('Upload proof'),
      new File(['replacement bytes'], 'replacement.pdf', { type: 'application/pdf' }),
    )
    expect(screen.getByLabelText('Upload proof')).toHaveClass('file:bg-selected')
    expect(screen.getByRole('button', { name: 'Upload Customer-provided evidence' })).toHaveClass('max-w-full', 'whitespace-normal')
    await user.click(screen.getByRole('button', { name: 'Upload Customer-provided evidence' }))

    await waitFor(() => {
      const uploadCall = vi.mocked(api.apiRequest).mock.calls.find(([path]) => String(path).endsWith('/versions'))
      expect(uploadCall).toBeDefined()
      const body = (uploadCall?.[1] as { body?: FormData } | undefined)?.body
      expect(body?.get('expectedCurrentVersionId')).toBe(baselineId)
    })
  })

  it('uploads a signed Customer cancellation request against the exact correction target', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(cancellationStaff)
    await bindUnresolvedOperations(cancellationStaff)
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).includes('CUSTOMER_CANCELLATION_REQUEST/versions')) {
        return {
          documentVersionId: cancellationEvidenceVersionId,
          versionNumber: 1,
          detectedMimeType: 'application/pdf',
          byteSize: 120,
          uploadedAt: '2026-09-04T08:30:00',
        }
      }
      return caseFixture('MISSING', true)
    })
    renderWorkspace()
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Customer-requested cancellation' })).toBeVisible()
    expect(screen.getByText(/Record cancellation only at the Customer's signed request/i)).toBeVisible()
    await user.upload(
      screen.getByLabelText('Signed request'),
      new File(['signed request'], 'cancellation.pdf', { type: 'application/pdf' }),
    )
    await user.click(screen.getByRole('button', { name: 'Upload signed request' }))

    await waitFor(() => {
      const uploadCall = vi.mocked(api.apiRequest).mock.calls.find(([path]) =>
        String(path).includes('CUSTOMER_CANCELLATION_REQUEST/versions'))
      expect(uploadCall).toBeDefined()
      const body = (uploadCall?.[1] as { body?: FormData } | undefined)?.body
      expect(body?.get('correctionRequestId')).toBe(requestId)
      expect(body?.get('file')).toBeInstanceOf(File)
    })
  })

  it('records the Customer-requested cancellation with exact correction and evidence identities', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(cancellationStaff)
    await bindUnresolvedOperations(cancellationStaff)
    vi.mocked(api.apiRequest).mockImplementation(async (path) => {
      if (String(path).endsWith(`/staff/loan-applications/${applicationId}/cancellation`)) {
        return {
          loanApplicationId: applicationId,
          resultingStatus: 'CANCELLED',
          cancelledAt: '2026-09-04T09:00:00',
          idempotentReplay: false,
        }
      }
      return caseFixture('SATISFIED', true, true)
    })
    renderWorkspace()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', {
      name: 'Review Customer-requested cancellation',
    }))
    expect(screen.getByText(/Customer requested and signed this cancellation/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Record Customer request' }))

    await waitFor(() => {
      const commandCall = vi.mocked(api.apiRequest).mock.calls.find(([path]) =>
        String(path).endsWith(`/staff/loan-applications/${applicationId}/cancellation`))
      const body = (commandCall?.[1] as {
        body?: { expectedCorrectionRequestId?: string; evidenceDocumentVersionId?: string }
      } | undefined)?.body
      expect(body?.expectedCorrectionRequestId).toBe(requestId)
      expect(body?.evidenceDocumentVersionId).toBe(cancellationEvidenceVersionId)
    })
  })
})

function renderWorkspace() {
  const router = createTestRouter([`/staff/applications/${applicationId}/corrections?taskId=${taskId}`])
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider><RouterProvider router={router} /></AuthProvider>
    </QueryClientProvider>,
  )
}
