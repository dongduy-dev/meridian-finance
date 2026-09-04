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
const staff: AuthResponse = {
  tokenType: 'Bearer', accessToken: 'staff-token', expiresAt: '2026-09-04T10:00:00Z',
  userId: '66666666-6666-4666-8666-666666666666', email: 'staff@meridian.local',
  userType: 'STAFF', customerId: null, roles: ['LOAN_OFFICER'],
  permissions: ['loan:correction:staff', 'document:upload:staff'],
}

function caseFixture(proofState: string) {
  return {
    loanApplicationId: applicationId,
    applicationNumber: 'UCL-20260904-000001',
    productCode: 'UNSECURED_CONSUMER_LOAN',
    applicationStatus: 'RETURNED_FOR_REVISION',
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
        responsibleParty: 'STAFF',
        status: 'OPEN',
        scope: 'SUPPORTING_DOCUMENT_UPLOAD',
        documentType: 'BANK_STATEMENT',
        checklistItemId: itemId,
        baselineDocumentVersionId: null,
        reasonCode: 'DOCUMENT_REVIEW_REQUIRED',
        staffInstruction: 'Upload supporting evidence.',
        createdAt: '2026-09-04T08:00:00',
        completedAt: null,
        proofState,
      }],
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

    await screen.findByRole('heading', { name: 'UCL-20260904-000001' })
    await user.upload(
      screen.getByLabelText('Upload proof'),
      new File(['different bytes'], 'different.pdf', { type: 'application/pdf' }),
    )
    await user.click(screen.getByRole('button', { name: 'Upload Staff document' }))

    expect(await screen.findByText(/previous operation result is still unknown/i)).toBeVisible()
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
})

function renderWorkspace() {
  const router = createTestRouter([`/staff/applications/${applicationId}/corrections?taskId=${taskId}`])
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AuthProvider><RouterProvider router={router} /></AuthProvider>
    </QueryClientProvider>,
  )
}
