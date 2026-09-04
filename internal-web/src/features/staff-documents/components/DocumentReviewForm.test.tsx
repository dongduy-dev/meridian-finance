import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { createQueryClient } from '@/lib/query/query-client'
import {
  bindUnresolvedOperations,
  digestOperationPayload,
  saveUnresolvedOperation,
} from '@/lib/operation/unresolved-operation'
import { DocumentReviewForm } from './DocumentReviewForm'

const applicationId = '11111111-1111-4111-8111-111111111111'
const itemId = '22222222-2222-4222-8222-222222222222'
const versionId = '33333333-3333-4333-8333-333333333333'
const operationId = '44444444-4444-4444-8444-444444444444'
const resource = `${applicationId}:${itemId}`
const semanticPayload = {
  outcome: 'ACCEPT_DOCUMENT', waiverReasonCode: '', customerInstruction: '',
  restrictedStaffNotes: '', version: versionId,
}
const item = {
  checklistItemId: itemId,
  documentType: 'BANK_STATEMENT',
  requirementStatus: 'REQUIRED',
  evidenceStatus: 'AWAITING_REVIEW',
  uploadComplete: true,
  processingReady: false,
  currentVersion: {
    documentVersionId: versionId, versionNumber: 1, originalFilename: 'evidence.pdf',
    detectedMimeType: 'application/pdf', byteSize: 128, uploadedAt: '2026-09-04T08:00:00',
  },
  versionHistory: [],
  reviewHistory: [],
}

describe('Document review operation recovery', () => {
  beforeEach(async () => {
    sessionStorage.clear()
    await bindUnresolvedOperations({
      userId: '55555555-5555-4555-8555-555555555555',
      roles: ['LOAN_OFFICER'],
      permissions: ['document:review'],
    })
  })

  it('reuses the retained reviewRequestId for the same semantic payload', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({
      reviewDecisionId: '66666666-6666-4666-8666-666666666666',
      checklistItemId: itemId,
      documentVersionId: versionId,
      outcome: 'ACCEPT_DOCUMENT',
      waiverReasonCode: null,
      decidedAt: '2026-09-04T08:30:00',
    })
    await saveRecovery(await digestOperationPayload(semanticPayload))
    renderForm(protectedRequest)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Review final details' }))
    await user.click(await screen.findByRole('button', { name: 'Confirm review' }))

    await waitFor(() => expect(protectedRequest).toHaveBeenCalledTimes(1))
    expect(protectedRequest.mock.calls[0]?.[1]).toMatchObject({
      body: expect.objectContaining({ reviewRequestId: operationId }),
    })
  })

  it('blocks changed semantics while the previous result is unresolved without calling the backend', async () => {
    const protectedRequest = vi.fn()
    await saveRecovery(await digestOperationPayload(semanticPayload))
    renderForm(protectedRequest)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/Restricted Staff notes/), 'changed restricted note')
    await user.click(screen.getByRole('button', { name: 'Review final details' }))

    expect(await screen.findByText(/previous operation result is still unknown/i)).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(protectedRequest).not.toHaveBeenCalled()
  })
})

function renderForm(protectedRequest: ReturnType<typeof vi.fn>) {
  render(
    <QueryClientProvider client={createQueryClient()}>
      <DocumentReviewForm
        manager={{ protectedRequest } as unknown as AuthSessionManager}
        loanApplicationId={applicationId}
        item={item}
        canWaive={false}
        stale={false}
      />
    </QueryClientProvider>,
  )
}

async function saveRecovery(payloadDigest: string) {
  saveUnresolvedOperation({
    type: 'DOCUMENT_REVIEW', resource, operationId, payloadDigest,
    unresolvedAt: '2026-09-04T08:15:00Z',
  })
}
