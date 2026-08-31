import { describe, expect, it, vi } from 'vitest'

import type { ApiClient, ProtectedRequestCoordinator } from '@/lib/api'

import { createDocumentApi } from './document-api'

const applicationId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const itemId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
const versionId = 'cccccccc-cccc-cccc-cccc-cccccccccccc'
const checklist = {
  checklistId: 'dddddddd-dddd-dddd-dddd-dddddddddddd', loanApplicationId: applicationId,
  stage: 'FUTURE_STAGE', uploadComplete: false, processingReady: false,
  items: [{ checklistItemId: itemId, documentType: 'FUTURE_TYPE', requirementStatus: 'FUTURE_REQUIREMENT', customerStatus: 'FUTURE_STATUS', uploadComplete: true, processingReady: false, currentVersion: null }],
}
const version = { documentVersionId: versionId, checklistItemId: itemId, versionNumber: 2, originalFilename: 'evidence.pdf', mimeType: 'application/pdf', byteSize: 2048, uploadedAt: '2026-08-31T09:00:00' }

function setup(response: unknown, replay = false) {
  const request = vi.fn().mockResolvedValue(response)
  const coordinator: ProtectedRequestCoordinator = {
    requestProtected: vi.fn(async (operation) => {
      if (replay) await operation('expired')
      return operation('fresh')
    }),
  }
  return { api: createDocumentApi(coordinator, { request } as ApiClient), request }
}

it('reads the protected checklist and preserves nullable versions, booleans, and evolving strings', async () => {
  const { api, request } = setup(checklist)
  expect(await api.getChecklist(applicationId)).toEqual(checklist)
  expect(request.mock.calls[0]?.[0]).toBe(`/loan-applications/${applicationId}/documents`)
  expect(new Headers(request.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe('Bearer fresh')
})

describe('multipart upload', () => {
  it('omits the first-upload baseline and preserves one FormData body across auth replay', async () => {
    const { api, request } = setup(version, true)
    const file = new File(['pdf'], 'evidence.pdf', { type: 'application/pdf' })
    await api.uploadDocument({ loanApplicationId: applicationId, checklistItemId: itemId, uploadRequestId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', file })
    expect(request).toHaveBeenCalledTimes(2)
    const firstBody = request.mock.calls[0]?.[1]?.body as FormData
    expect(firstBody).toBe(request.mock.calls[1]?.[1]?.body)
    expect(firstBody.get('uploadRequestId')).toBe('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee')
    expect(firstBody.get('file')).toBe(file)
    expect(firstBody.has('expectedCurrentVersionId')).toBe(false)
    expect(new Headers(request.mock.calls[0]?.[1]?.headers).has('Content-Type')).toBe(false)
  })

  it('includes the exact current version for replacement', async () => {
    const { api, request } = setup(version)
    await api.uploadDocument({ loanApplicationId: applicationId, checklistItemId: itemId, uploadRequestId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', expectedCurrentVersionId: versionId, file: new File(['pdf'], 'replacement.pdf', { type: 'application/pdf' }) })
    const body = request.mock.calls[0]?.[1]?.body as FormData
    expect(body.get('expectedCurrentVersionId')).toBe(versionId)
  })
})
