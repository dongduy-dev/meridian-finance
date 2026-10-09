import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'

import { AuthContext } from '@/features/auth/auth-context'
import { createTestAuthManager } from '@/test/auth'
import { stubDocumentDownload } from '@/test/document-download'

import { CurrentDocumentSummary } from './CurrentDocumentSummary'

const getDocumentContent = vi.hoisted(() => vi.fn())
vi.mock('../document-api', () => ({ createDocumentApi: () => ({ getDocumentContent }) }))

const applicationId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const itemId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
const version = {
  documentVersionId: 'cccccccc-cccc-cccc-cccc-cccccccccccc', checklistItemId: itemId,
  versionNumber: 1, originalFilename: 'income.pdf', mimeType: 'application/pdf',
  byteSize: 2048, uploadedAt: '2026-08-31T09:00:00',
}

function renderDocument() {
  const manager = createTestAuthManager()
  return render(<AuthContext.Provider value={{ manager, state: manager.getSnapshot() }}>
    <CurrentDocumentSummary loanApplicationId={applicationId} checklistItemId={itemId} version={version} />
  </AuthContext.Provider>)
}

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  getDocumentContent.mockReset()
})

it('blocks duplicate attempts while pending and revokes the temporary URL after download', async () => {
  const user = userEvent.setup()
  const browser = stubDocumentDownload()
  let resolve!: (blob: Blob) => void
  getDocumentContent.mockReturnValue(new Promise<Blob>(done => { resolve = done }))
  renderDocument()
  await user.click(screen.getByRole('button', { name: 'Download document' }))
  const pending = screen.getByRole('button', { name: 'Downloading…' })
  expect(pending).toBeDisabled()
  await user.click(pending)
  expect(getDocumentContent).toHaveBeenCalledTimes(1)
  expect(getDocumentContent).toHaveBeenCalledWith(applicationId, itemId, version.documentVersionId)

  vi.useFakeTimers()
  const blob = new Blob(['%PDF'], { type: 'application/pdf' })
  await act(async () => resolve(blob))
  expect(browser.createObjectURL).toHaveBeenCalledWith(blob)
  expect(browser.downloads).toEqual([{ filename: version.originalFilename, url: 'blob:current-document' }])
  expect(screen.getByRole('button', { name: 'Download document' })).toBeEnabled()
  act(() => vi.advanceTimersByTime(1000))
  expect(browser.revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:current-document')
})

it('revokes surviving URLs on unmount and clears the delayed cleanup', async () => {
  const user = userEvent.setup()
  const browser = stubDocumentDownload()
  getDocumentContent.mockResolvedValue(new Blob(['%PDF']))
  const view = renderDocument()
  vi.useFakeTimers({ shouldAdvanceTime: true })
  await user.click(screen.getByRole('button', { name: 'Download document' }))
  expect(browser.downloads).toHaveLength(1)
  view.unmount()
  expect(browser.revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:current-document')
  act(() => vi.advanceTimersByTime(1000))
  expect(browser.revokeObjectURL).toHaveBeenCalledTimes(1)
})

it('does not create a download when the response arrives after unmount', async () => {
  const user = userEvent.setup()
  const browser = stubDocumentDownload()
  let resolve!: (blob: Blob) => void
  getDocumentContent.mockReturnValue(new Promise<Blob>(done => { resolve = done }))
  const view = renderDocument()
  await user.click(screen.getByRole('button', { name: 'Download document' }))
  view.unmount()
  await act(async () => resolve(new Blob(['%PDF'])))
  expect(browser.createObjectURL).not.toHaveBeenCalled()
  expect(browser.downloads).toEqual([])
})
