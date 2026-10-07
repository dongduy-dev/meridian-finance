import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { SessionState } from '@/features/auth/model/auth-session'
import type { ApiBinaryResponse } from '@/lib/api'
import * as documents from '../api/staff-documents-api'
import * as intake from '@/features/staff-origination/api/staff-origination-api'
import * as identity from '@/features/customer-identity/api'
import { DocumentContentViewer } from './DocumentContentViewer'

vi.mock('../api/staff-documents-api', () => ({ getAssistedActionEvidenceContent: vi.fn(), getDocumentContent: vi.fn() }))
vi.mock('@/features/staff-origination/api/staff-origination-api', () => ({ getIntakeEvidenceContent: vi.fn() }))
vi.mock('@/features/customer-identity/api', () => ({ identityContent: vi.fn() }))
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
  vi.mocked(intake.getIntakeEvidenceContent).mockResolvedValue(response)
  vi.mocked(identity.identityContent).mockResolvedValue(response)
})
afterEach(() => vi.unstubAllGlobals())

describe('private exact-version viewer', () => {
  it('downloads the already authorized bytes with the UTF-8 response filename and revokes on unmount', async () => {
    vi.mocked(documents.getAssistedActionEvidenceContent).mockResolvedValue({ ...response,
      contentDisposition: "attachment; filename*=UTF-8''signed%20%C4%91%C6%A1n.pdf" })
    const view = render(<DocumentContentViewer manager={manager} loanApplicationId={app} evidenceType="CUSTOMER_OFFER_RESPONSE" documentVersionId={historical} filename="Signed Customer action evidence" />)
    expect(screen.queryByRole('link', { name: 'Download document' })).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    const download = screen.getByRole('link', { name: 'Download document' })
    expect(download).toHaveAttribute('href', 'blob:private-evidence')
    expect(download).toHaveAttribute('download', 'signed đơn.pdf')
    download.addEventListener('click', event => event.preventDefault())
    await userEvent.setup().click(download)
    expect(createUrl).toHaveBeenCalledOnce()
    expect(createUrl).toHaveBeenCalledWith(response.blob)
    expect(documents.getAssistedActionEvidenceContent).toHaveBeenCalledOnce()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
    view.unmount()
    expect(revokeUrl).toHaveBeenCalledWith('blob:private-evidence')
  })

  it.each(['attachment; filename="../unsafe.pdf"', "attachment; filename*=UTF-8''%00unsafe.pdf", "attachment; filename*=UTF-8''%XX"])(
    'uses the known safe version filename when disposition is unsafe: %s', async contentDisposition => {
      vi.mocked(documents.getAssistedActionEvidenceContent).mockResolvedValue({ ...response, contentDisposition })
      render(<DocumentContentViewer manager={manager} loanApplicationId={app} evidenceType="CUSTOMER_OFFER_RESPONSE" documentVersionId={version} filename="known.pdf" />)
      await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
      expect(screen.getByRole('link', { name: 'Download document' })).toHaveAttribute('download', 'known.pdf')
    },
  )

  it('keeps identity content on its purpose-specific path with explicit download', async () => {
    state = { ...state, status: 'authenticated', actor: { userId: 'staff-one', email: 'staff@meridian.local', roles: ['LOAN_OFFICER'], permissions: ['customer:identity:verify'] } }
    render(<DocumentContentViewer manager={manager} identityVerificationId={version} filename="identity.png" />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    expect(identity.identityContent).toHaveBeenCalledWith(manager, version)
    expect(screen.getByRole('link', { name: 'Download document' })).toHaveAttribute('download', 'identity.png')
    expect(documents.getDocumentContent).not.toHaveBeenCalled()
  })

  it('reads exact intake evidence and clears content immediately when intake authority is lost', async () => {
    state = { ...state, status: 'authenticated', actor: { userId: 'staff-one', email: 'staff@meridian.local', roles: ['LOAN_OFFICER'], permissions: ['loan:originate:staff', 'document:upload:intake'] } }
    render(<DocumentContentViewer manager={manager} intakeCaseId={app} intakeEvidenceType="UCL_PAPER_APPLICATION" documentVersionId={historical} filename="paper.pdf" />)
    expect(intake.getIntakeEvidenceContent).not.toHaveBeenCalled()
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    expect(intake.getIntakeEvidenceContent).toHaveBeenCalledWith(manager, app, 'UCL_PAPER_APPLICATION', historical)
    act(() => { state = { status: 'authenticated', epoch: 2, actor: { userId: 'staff-one', email: 'staff@meridian.local', roles: ['LOAN_OFFICER'], permissions: ['loan:originate:staff'] } }; notify() })
    expect(revokeUrl).toHaveBeenCalledWith('blob:private-evidence')
    expect(screen.queryByRole('link', { name: 'Download document' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('does not request intake bytes without exact authority', () => {
    render(<DocumentContentViewer manager={manager} intakeCaseId={app} intakeEvidenceType="CUSTOMER_IDENTITY" documentVersionId={version} filename="identity.pdf" />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(intake.getIntakeEvidenceContent).not.toHaveBeenCalled()
  })

  it('shows a safe content error and never silently reads a different version', async () => {
    vi.mocked(documents.getAssistedActionEvidenceContent).mockRejectedValue(new Error('private storage details'))
    render(<DocumentContentViewer manager={manager} loanApplicationId={app} evidenceType="CUSTOMER_OFFER_RESPONSE" documentVersionId={historical} filename="signed.pdf" />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    expect(screen.getByText('Document content could not be loaded. Refresh the evidence and try again.')).toBeVisible()
    expect(screen.queryByText('private storage details')).not.toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(createUrl).not.toHaveBeenCalled()
  })

  it('closing a pending read prevents its late response from recreating content', async () => {
    let resolve!: (value: ApiBinaryResponse) => void
    vi.mocked(documents.getAssistedActionEvidenceContent).mockReturnValue(new Promise(done => { resolve = done }))
    render(<DocumentContentViewer manager={manager} loanApplicationId={app} evidenceType="CUSTOMER_OFFER_RESPONSE" documentVersionId={historical} filename="signed.pdf" />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'View document' }))
    await userEvent.setup().click(screen.getByRole('button', { name: 'Close viewer' }))
    await act(async () => resolve(response))
    expect(createUrl).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'View document' })).toBeVisible()
  })

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
