import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { SessionState } from '@/features/auth/model/auth-session'
import type { ApiBinaryResponse } from '@/lib/api'
import * as documents from '../api/staff-documents-api'
import { DocumentContentViewer } from './DocumentContentViewer'

vi.mock('../api/staff-documents-api', () => ({ getAssistedActionEvidenceContent: vi.fn(), getDocumentContent: vi.fn() }))
const app = '11111111-1111-4111-8111-111111111111'
const version = '22222222-2222-4222-8222-222222222222'
const historical = '33333333-3333-4333-8333-333333333333'
const response: ApiBinaryResponse = { blob: new Blob(['signed historical bytes']), contentType: 'application/pdf' }
let state: SessionState
let notify: () => void
let manager: AuthSessionManager
const createUrl = vi.fn(() => 'blob:private-evidence')
const revokeUrl = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  state = { status: 'authenticated', epoch: 1, actor: { userId: 'staff-one', email: 'staff@meridian.local', roles: ['LOAN_OFFICER'], permissions: ['document:review'] } }
  manager = { getSnapshot: () => state, subscribe: (listener: () => void) => { notify = listener; return () => {} } } as AuthSessionManager
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revokeUrl }))
  vi.mocked(documents.getAssistedActionEvidenceContent).mockResolvedValue(response)
})
afterEach(() => vi.unstubAllGlobals())

describe('private exact-version viewer', () => {
  it('does not fetch checklist content before explicit view, displays a safe image, and revokes its URL on close', async () => {
    vi.mocked(documents.getDocumentContent).mockResolvedValue({ blob: new Blob(['image'], { type: 'image/png' }), contentType: 'image/png' })
    render(<DocumentContentViewer manager={manager} loanApplicationId={app} checklistItemId="item" documentVersionId={version} filename="proof.png" />)
    expect(documents.getDocumentContent).not.toHaveBeenCalled()
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    expect(documents.getDocumentContent).toHaveBeenCalledWith(manager, app, 'item', version)
    expect(screen.getByRole('img', { name: 'Document evidence: proof.png' })).toBeVisible()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Close viewer' }))
    expect(revokeUrl).toHaveBeenCalledWith('blob:private-evidence')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it.each(['CUSTOMER_OFFER_RESPONSE', 'CUSTOMER_CONTRACT_ACKNOWLEDGMENT', 'CUSTOMER_CANCELLATION_REQUEST'])('reads %s only on demand and revokes bytes on version change', async (evidenceType) => {
    const props = { manager, loanApplicationId: app, evidenceType, documentVersionId: historical, filename: 'signed.pdf' }
    const view = render(<DocumentContentViewer {...props} />)
    expect(documents.getAssistedActionEvidenceContent).not.toHaveBeenCalled()
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    expect(documents.getAssistedActionEvidenceContent).toHaveBeenCalledWith(manager, app, evidenceType, historical)
    expect(screen.getByLabelText('Document viewer for signed.pdf')).toBeVisible()
    view.rerender(<DocumentContentViewer {...props} documentVersionId={version} />)
    expect(revokeUrl).toHaveBeenCalledWith('blob:private-evidence')
    expect(screen.queryByLabelText('Document viewer for signed.pdf')).not.toBeInTheDocument()
    expect(sessionStorage.length).toBe(0)
  })

  it('clears content when the authenticated actor changes and on logout', async () => {
    render(<DocumentContentViewer manager={manager} loanApplicationId={app} evidenceType="CUSTOMER_OFFER_RESPONSE" documentVersionId={version} filename="signed.pdf" />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    act(() => { state = { ...state, status: 'authenticated', epoch: 2, actor: { userId: 'staff-two', email: 'second@meridian.local', roles: [], permissions: ['document:review'] } }; notify() })
    expect(revokeUrl).toHaveBeenCalledWith('blob:private-evidence')
    expect(screen.queryByLabelText('Document viewer for signed.pdf')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    act(() => { state = { status: 'anonymous', epoch: 3 }; notify() })
    expect(revokeUrl).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('does not retain an in-flight response after unmount', async () => {
    let resolve!: (value: ApiBinaryResponse) => void
    vi.mocked(documents.getAssistedActionEvidenceContent).mockReturnValue(new Promise((done) => { resolve = done }))
    const view = render(<DocumentContentViewer manager={manager} loanApplicationId={app} evidenceType="CUSTOMER_CANCELLATION_REQUEST" documentVersionId={version} filename="signed.pdf" />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    view.unmount()
    await act(async () => resolve(response))
    expect(createUrl).not.toHaveBeenCalled()
  })

  it('rejects an unsupported MIME type without creating an object URL', async () => {
    vi.mocked(documents.getAssistedActionEvidenceContent).mockResolvedValue({ ...response, contentType: 'text/html' })
    render(<DocumentContentViewer manager={manager} loanApplicationId={app} evidenceType="CUSTOMER_OFFER_RESPONSE" documentVersionId={version} filename="signed.pdf" />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    expect(screen.getByText('This document type cannot be displayed safely.')).toBeVisible()
    expect(createUrl).not.toHaveBeenCalled()
  })
})
