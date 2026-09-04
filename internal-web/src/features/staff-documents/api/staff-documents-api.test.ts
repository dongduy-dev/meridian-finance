import { describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getDocumentContent, getDocumentReviewQueue, uploadStaffDocument } from './staff-documents-api'

describe('Staff document API', () => {
  it('uses the exact list-only review queue contract', async () => {
    const protectedRequest = vi.fn().mockResolvedValue([])
    await getDocumentReviewQueue({ protectedRequest } as unknown as AuthSessionManager, 2, 20)
    expect(protectedRequest).toHaveBeenCalledWith('/document-review-items?status=AWAITING_REVIEW&page=2&size=20')
  })

  it('requests Blob content through protected transport', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({ blob: new Blob(), contentType: 'application/pdf' })
    await getDocumentContent(
      { protectedRequest } as unknown as AuthSessionManager,
      'application', 'item', 'version',
    )
    expect(protectedRequest).toHaveBeenCalledWith(
      '/staff/loan-applications/application/documents/item/versions/version/content',
      { responseType: 'blob' },
    )
  })

  it('sends Staff upload as FormData with exact expected version evidence', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({})
    const file = new File(['pdf'], 'proof.pdf', { type: 'application/pdf' })
    await uploadStaffDocument(
      { protectedRequest } as unknown as AuthSessionManager,
      'application', 'item', 'operation', 'baseline', file,
    )
    const options = protectedRequest.mock.calls[0]?.[1]
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.body.get('expectedCurrentVersionId')).toBe('baseline')
    expect(options.headers).toBeUndefined()
  })
})
