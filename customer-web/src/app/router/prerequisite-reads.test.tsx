import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { AppProviders } from '@/app/providers/AppProviders'
import { queryClient } from '@/app/providers/query-client'
import { applicationKeys } from '@/features/applications/application-queries'
import { contractKeys } from '@/features/contracts/contract-queries'
import { correctionKeys } from '@/features/corrections/correction-queries'
import { documentKeys } from '@/features/documents/document-queries'
import { offerKeys } from '@/features/offers/offer-queries'
import { createTestAuthManager } from '@/test/auth'
import { createTestRouter } from './router'

const id = '10000000-0000-4000-8000-000000000001'
const itemId = '20000000-0000-4000-8000-000000000001'
const versionId = '30000000-0000-4000-8000-000000000001'
const taskId = '40000000-0000-4000-8000-000000000001'
const now = '2026-10-03T10:00:00'
const summary = { loanApplicationId: id, applicationNumber: 'UCL-FICTIONAL-001', productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED', originationChannel: 'CUSTOMER_DIGITAL', requestedAmount: 6_000_000, requestedTermMonths: 6,
  status: 'RETURNED_FOR_REVISION', submittedAt: now, lifecycleActive: true, requiredAction: 'COMPLETE_CORRECTIONS' }
const repayment = { installmentNumber: 1, principalDue: 1_000_000, interestDue: 90_000, feeDue: 0, totalDue: 1_090_000 }
const terms = { approvedPrincipal: 6_000_000, approvedTermMonths: 6, interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL',
  flatMonthlyInterestRate: 0.015, totalInterest: 540_000, feeAmount: 0, totalRepaymentAmount: 6_540_000, repaymentMethod: 'MONTHLY_INSTALLMENT' }
const offer = { ...terms, approvedOfferId: versionId, loanApplicationId: id, status: 'PENDING', generatedAt: now,
  expiresAt: '2026-10-10T10:00:00', acceptedAt: null, declinedAt: null, expiredAt: null, availableActions: ['ACCEPT', 'DECLINE'],
  repaymentItems: [{ ...repayment, repaymentTiming: 'MONTHLY_INSTALLMENT' }] }
const contract = { ...terms, contractId: versionId, contractReference: 'CTR-FICTIONAL-V1', contractVersion: 1, status: 'PREPARED',
  repaymentPreview: [repayment], disbursementBankAccount: { bankCode: 'FICTIONAL', bankNameSnapshot: 'Fictional Bank',
    accountHolderName: 'ARI FICTIONAL', maskedAccountNumber: '****6789', primaryAtCapture: true, activeAtCapture: true, capturedAt: now },
  preparedAt: now, acknowledgedAt: null, readinessConfirmedAt: null, availableCustomerAction: 'ACKNOWLEDGE' }
const task = { correctionTaskId: taskId, correctionRequestId: versionId, status: 'OPEN', scope: 'DOCUMENT_REPLACEMENT',
  documentType: 'INCOME_PROOF', checklistItemId: itemId, reasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED',
  customerInstruction: 'Replace the fictional income evidence.', createdAt: now, completedAt: null }
const checklist = { checklistId: taskId, loanApplicationId: id, stage: 'CORRECTION', uploadComplete: true, processingReady: false,
  items: [{ checklistItemId: itemId, documentType: 'INCOME_PROOF', requirementStatus: 'REQUIRED', customerStatus: 'REPLACEMENT_REQUESTED',
    uploadComplete: true, processingReady: false, currentVersion: { documentVersionId: versionId, checklistItemId: itemId,
      versionNumber: 1, originalFilename: 'fictional-income.pdf', mimeType: 'application/pdf', byteSize: 40, uploadedAt: now } }] }
const keys = { detail: applicationKeys.detail(id), index: applicationKeys.index(), checklist: documentKeys.checklist(id),
  tasks: correctionKeys.tasks(id), offer: offerKeys.detail(id), contract: contractKeys.current(id) }
type Read = keyof typeof keys

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function fixture(path: string, completed = false, documentStatus = 'REPLACEMENT_REQUESTED', channel = 'CUSTOMER_DIGITAL') {
  const failed = new Set<Read>()
  const data = { detail: { ...summary, originationChannel: channel }, index: [{ ...summary, originationChannel: channel }], offer: { ...offer }, contract: { ...contract, availableCustomerAction: contract.availableCustomerAction as string | null },
    tasks: [{ ...task, ...(completed ? { status: 'COMPLETED', completedAt: now } : {}) }],
    checklist: { ...checklist, items: [{ ...checklist.items[0], customerStatus: documentStatus }] } }
  const reads: Record<Read, number> = { detail: 0, index: 0, offer: 0, contract: 0, tasks: 0, checklist: 0 }
  let posts = 0
  let postsAllowed = false
  let deferred: { read: Read; promise: Promise<Response> } | undefined
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (init?.method === 'POST') {
      posts++
      if (!postsAllowed) throw new Error('Unexpected business POST')
      return json({ timestamp: now, status: 422, errorCode: 'VALIDATION_FAILED', message: 'Controlled command rejection.', path: url }, 422)
    }
    const read = url.endsWith(`/loan-applications/${id}/documents`) ? 'checklist'
      : url.endsWith('/approved-offer') ? 'offer' : url.endsWith('/contracts/current') ? 'contract'
        : url.endsWith('/corrections/tasks') ? 'tasks' : url.endsWith(`/loan-applications/${id}`) ? 'detail'
          : url.endsWith('/loan-applications') ? 'index' : undefined
    if (!read) throw new Error(`Unexpected GET ${url}`)
    reads[read]++
    if (deferred?.read === read) return deferred.promise
    if (failed.has(read)) return json({ timestamp: now, status: 403, errorCode: 'FORBIDDEN', message: 'Read unavailable.', path: url }, 403)
    return json(data[read])
  })
  vi.stubGlobal('fetch', fetchMock)
  render(<AppProviders router={createTestRouter([`/applications/${id}/${path}`])} authManager={createTestAuthManager()} />)
  return { failed, data, reads, posts: () => posts, allowPosts: () => { postsAllowed = true }, defer: (read: Read, promise: Promise<Response>) => { deferred = { read, promise } },
    release: () => { deferred = undefined }, refresh: async (read: Read) => {
      await act(async () => { await queryClient.refetchQueries({ queryKey: keys[read], exact: true }) })
      // Query observers notify asynchronously after the fetch promise settles.
      await act(async () => { await new Promise(resolve => setTimeout(resolve, 0)) })
    } }
}
afterEach(() => { cleanup(); queryClient.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

it('does not repeat a cached no-documents claim after a checklist refresh fails', async () => {
  const f = fixture('documents')
  f.data.checklist.items.splice(0)
  expect(await screen.findByText('No documents are currently required')).toBeVisible()
  f.failed.add('checklist'); await f.refresh('checklist')
  expect(screen.getByText('Documents could not be loaded')).toBeVisible()
  expect(screen.queryByText('No documents are currently required')).not.toBeInTheDocument()
  expect(screen.queryByText('All required documents have been provided or are no longer needed.')).not.toBeInTheDocument()
})

it.each(['upload', 'correction-upload', 'accept', 'decline', 'acknowledge', 'complete', 'cancel', 'resubmit'] as const)('guards the %s handler when refetch starts before the rendered control can update', async command => {
  const page = command === 'upload' ? 'documents' : ['accept', 'decline'].includes(command) ? 'offer'
    : command === 'acknowledge' ? 'contract' : 'corrections'
  const f = fixture(page, command === 'resubmit'); const user = userEvent.setup()
  let button: HTMLElement
  if (command === 'upload' || command === 'correction-upload') {
    await user.upload(await screen.findByLabelText('Choose replacement file'), new File(['fictional'], 'new.pdf', { type: 'application/pdf' }))
    button = screen.getByRole('button', { name: 'Replace document' })
  } else if (command === 'decline' || command === 'cancel') {
    const name = command === 'decline' ? 'Decline offer' : 'Cancel application'
    await user.click(await screen.findByRole('button', { name }))
    button = within(screen.getByRole('dialog')).getByRole('button', { name })
  } else if (command === 'acknowledge') {
    await user.click(await screen.findByRole('button', { name: 'Confirm review of version 1' }))
    button = within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm version 1' })
  } else {
    button = await screen.findByRole('button', { name: command === 'accept' ? 'Accept offer' : command === 'complete' ? 'Mark as complete' : 'Submit updates' })
  }
  expect(button).toBeEnabled()
  let resolve!: (response: Response) => void
  f.defer('detail', new Promise(done => { resolve = done }))
  let refresh!: Promise<void>
  await act(async () => {
    refresh = queryClient.refetchQueries({ queryKey: keys.detail })
    fireEvent.click(button)
    expect(f.posts()).toBe(0)
  })
  f.release(); await act(async () => { resolve(json(f.data.detail)); await refresh })
  expect(f.posts()).toBe(0)
})

it.each(['documents', 'offer', 'contract', 'corrections'])('keeps STAFF_ASSISTED %s read-only before, during and after failed detail refresh', async page => {
  const f = fixture(page, false, 'REPLACEMENT_REQUESTED', 'STAFF_ASSISTED')
  await waitFor(() => expect(screen.getAllByText(/coordinat/i)[0]).toBeVisible())
  const assertReadOnly = () => {
    expect(screen.queryByRole('button', { name: /Upload document|Replace document|Accept offer|Decline offer|Confirm review|Confirm version|Mark as complete|Submit updates|Cancel application/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Choose.*file/)).not.toBeInTheDocument()
    expect(f.posts()).toBe(0)
  }
  assertReadOnly()
  f.failed.add('detail'); await f.refresh('detail'); assertReadOnly()
  f.failed.delete('detail'); await f.refresh('detail'); assertReadOnly()
  if (page === 'corrections') { expect(f.reads.tasks).toBe(0); expect(f.reads.checklist).toBe(0) }
})

it.each(['detail', 'checklist'] as const)('Documents blocks cached upload/replace after failed %s and restores eligible controls', async read => {
  const f = fixture('documents')
  const user = userEvent.setup()
  const picker = await screen.findByLabelText('Choose replacement file')
  await user.upload(picker, new File(['fictional'], 'new.pdf', { type: 'application/pdf' }))
  const button = screen.getByRole('button', { name: 'Replace document' })
  expect(button).toBeEnabled()
  f.failed.add(read); await f.refresh(read)
  expect(screen.getByText('fictional-income.pdf')).toBeVisible()
  expect(screen.getByText(/could not be loaded/)).toBeVisible()
  expect(screen.queryByLabelText('Choose replacement file')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Upload document|Replace document/ })).not.toBeInTheDocument()
  fireEvent.click(button); expect(f.posts()).toBe(0)
  expect(queryClient.getQueryData(keys.checklist)).toBeDefined()
  f.failed.delete(read); await f.refresh(read)
  expect(await screen.findByLabelText('Choose replacement file')).toBeVisible()
  // A successful GET restores only the action returned by the current checklist.
  f.data.checklist.items[0]!.customerStatus = 'ACCEPTED'; await f.refresh('checklist')
  expect(screen.queryByLabelText('Choose replacement file')).not.toBeInTheDocument()
})
it('Documents blocks uploads while detail revalidation is unresolved, then restores upload after success', async () => {
  const f = fixture('documents', false, 'NOT_UPLOADED')
  await screen.findByLabelText('Choose file')
  let resolve!: (response: Response) => void
  f.defer('detail', new Promise(done => { resolve = done }))
  let refresh!: Promise<void>
  await act(async () => { refresh = queryClient.refetchQueries({ queryKey: keys.detail }) })
  await waitFor(() => expect(screen.queryByLabelText('Choose file')).not.toBeInTheDocument())
  expect(screen.getByText('fictional-income.pdf')).toBeVisible()
  f.release(); await act(async () => { resolve(json(f.data.detail)); await refresh })
  expect(await screen.findByLabelText('Choose file')).toBeVisible()
  expect(f.posts()).toBe(0)
})

it.each(['detail', 'offer'] as const)('Offer closes an open decline dialog after failed %s without losing cached terms', async read => {
  const f = fixture('offer'); const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Decline offer' }))
  const submit = within(screen.getByRole('dialog')).getByRole('button', { name: 'Decline offer' })
  f.failed.add(read); await f.refresh(read)
  expect(screen.getByRole('heading', { name: 'Approved offer' })).toBeVisible()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Accept offer|Decline offer/ })).not.toBeInTheDocument()
  expect(screen.queryByText('No response required')).not.toBeInTheDocument()
  fireEvent.click(submit); expect(f.posts()).toBe(0)
  f.failed.delete(read); await f.refresh(read)
  await waitFor(() => expect(screen.getAllByRole('button', { name: 'Decline offer' }).length).toBeGreaterThan(0))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Keep offer' }))
  expect(screen.getByRole('button', { name: 'Accept offer' })).toBeEnabled()
  f.data.offer.availableActions = []; await f.refresh('offer')
  expect(screen.getByText('No response required')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Accept offer' })).not.toBeInTheDocument()
})

it.each(['detail', 'contract'] as const)('Contract blocks an open acknowledgment dialog after failed %s and restores only current actions', async read => {
  const f = fixture('contract'); const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Confirm review of version 1' }))
  const submit = within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm version 1' })
  f.failed.add(read); await f.refresh(read)
  expect(screen.getByText('CTR-FICTIONAL-V1')).toBeVisible()
  expect(screen.getByText('****6789')).toBeVisible()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Confirm review|Confirm version/ })).not.toBeInTheDocument()
  expect(screen.queryByText('No action needed')).not.toBeInTheDocument()
  fireEvent.click(submit); expect(f.posts()).toBe(0)
  f.failed.delete(read); await f.refresh(read)
  expect(await screen.findByRole('button', { name: 'Confirm version 1' })).toBeEnabled()
  f.data.contract.availableCustomerAction = null; await f.refresh('contract')
  expect(screen.getByText('No action needed')).toBeVisible()
  expect(screen.queryByRole('button', { name: /Confirm review|Confirm version/ })).not.toBeInTheDocument()
})

it.each(['detail', 'tasks', 'checklist'] as const)('Corrections blocks completion and replacement after failed prerequisite %s', async read => {
  const f = fixture('corrections'); const user = userEvent.setup()
  const complete = await screen.findByRole('button', { name: 'Mark as complete' })
  await user.upload(screen.getByLabelText('Choose replacement file'), new File(['fictional'], 'new.pdf', { type: 'application/pdf' }))
  const upload = screen.getByRole('button', { name: 'Replace document' })
  expect(screen.getByRole('button', { name: 'Cancel application' })).toBeEnabled()
  f.failed.add(read); await f.refresh(read)
  expect(screen.getByText(task.customerInstruction)).toBeVisible()
  expect(screen.queryByRole('button', { name: /Mark as complete|Replace document|Submit updates/ })).not.toBeInTheDocument()
  if (read === 'detail') expect(screen.queryByRole('button', { name: 'Cancel application' })).not.toBeInTheDocument()
  else expect(screen.getByRole('button', { name: 'Cancel application' })).toBeEnabled()
  expect(screen.queryByLabelText('Choose replacement file')).not.toBeInTheDocument()
  fireEvent.click(complete); fireEvent.click(upload); expect(f.posts()).toBe(0)
  if (read === 'detail') {
    const { tasks, checklist: checklistReads } = f.reads
    await act(async () => { await Promise.all([
      queryClient.invalidateQueries({ queryKey: keys.tasks }), queryClient.invalidateQueries({ queryKey: keys.checklist }),
    ]) })
    expect(f.reads.tasks).toBe(tasks); expect(f.reads.checklist).toBe(checklistReads)
  }
  f.failed.delete(read); await f.refresh(read)
  expect(await screen.findByRole('button', { name: 'Mark as complete' })).toBeEnabled()
  expect(screen.getByLabelText('Choose replacement file')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Cancel application' })).toBeEnabled()
})
it.each(['detail', 'index', 'tasks'] as const)('Corrections blocks resubmission after failed prerequisite %s and rechecks current requiredAction', async read => {
  const f = fixture('corrections', true)
  const submit = await screen.findByRole('button', { name: 'Submit updates' })
  f.failed.add(read); await f.refresh(read)
  expect(screen.getByText(task.customerInstruction)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Submit updates' })).not.toBeInTheDocument()
  if (read === 'detail') expect(screen.queryByRole('button', { name: 'Cancel application' })).not.toBeInTheDocument()
  else expect(screen.getByRole('button', { name: 'Cancel application' })).toBeEnabled()
  expect(screen.queryByText('All requested changes are complete')).not.toBeInTheDocument()
  fireEvent.click(submit); expect(f.posts()).toBe(0)
  f.failed.delete(read); await f.refresh(read)
  expect(await screen.findByRole('button', { name: 'Submit updates' })).toBeEnabled()
  f.data.index[0]!.requiredAction = 'NONE'; await f.refresh('index')
  expect(screen.queryByRole('button', { name: 'Submit updates' })).not.toBeInTheDocument()
})

it.each(['complete', 'upload'] as const)('keeps correction %s available and executable after an unrelated index failure', async command => {
  const f = fixture('corrections'); const user = userEvent.setup()
  await screen.findByRole('button', { name: 'Mark as complete' })
  f.failed.add('index'); await f.refresh('index')
  expect(screen.getByText(task.customerInstruction)).toBeVisible()
  expect(screen.getByRole('button', { name: 'Mark as complete' })).toBeEnabled()
  expect(screen.getByLabelText('Choose replacement file')).toBeVisible()
  await user.upload(screen.getByLabelText('Choose replacement file'), new File(['fictional'], 'new.pdf', { type: 'application/pdf' }))
  expect(screen.getByRole('button', { name: 'Replace document' })).toBeEnabled()
  f.allowPosts()
  await user.click(screen.getByRole('button', { name: command === 'complete' ? 'Mark as complete' : 'Replace document' }))
  await waitFor(() => expect(f.posts()).toBe(1))
})

it('keeps correction task/document actions available during unrelated index revalidation', async () => {
  const f = fixture('corrections'); const user = userEvent.setup()
  const complete = await screen.findByRole('button', { name: 'Mark as complete' })
  let resolve!: (response: Response) => void
  f.defer('index', new Promise(done => { resolve = done }))
  let refresh!: Promise<void>
  await act(async () => { refresh = queryClient.refetchQueries({ queryKey: keys.index }) })
  expect(complete).toBeEnabled()
  await user.upload(screen.getByLabelText('Choose replacement file'), new File(['fictional'], 'new.pdf', { type: 'application/pdf' }))
  expect(screen.getByRole('button', { name: 'Replace document' })).toBeEnabled()
  f.allowPosts(); await user.click(complete)
  expect(f.posts()).toBe(1)
  f.release(); await act(async () => { resolve(json(f.data.index)); await refresh })
})

it('keeps resubmission available and executable after checklist failure, then honors current index action', async () => {
  const f = fixture('corrections', true); const user = userEvent.setup()
  await screen.findByRole('button', { name: 'Submit updates' })
  f.failed.add('checklist'); await f.refresh('checklist')
  expect(screen.getByRole('button', { name: 'Submit updates' })).toBeEnabled()
  f.allowPosts(); await user.click(screen.getByRole('button', { name: 'Submit updates' }))
  await waitFor(() => expect(f.posts()).toBe(1))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Submit updates' })).toBeEnabled())
  f.data.index[0]!.requiredAction = 'NONE'; await f.refresh('index')
  expect(screen.queryByRole('button', { name: 'Submit updates' })).not.toBeInTheDocument()
})

it.each(['index', 'tasks', 'checklist'] as const)('keeps cancellation and its open dialog executable after unrelated %s failure', async read => {
  const f = fixture('corrections'); const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Cancel application' }))
  f.failed.add(read); await f.refresh(read)
  const dialog = screen.getByRole('dialog', { name: 'Cancel this application?' })
  const cancel = within(dialog).getByRole('button', { name: 'Cancel application' })
  expect(cancel).toBeEnabled()
  f.allowPosts(); await user.click(cancel)
  await waitFor(() => expect(f.posts()).toBe(1))
})

it('blocks an already-open cancellation dialog after failed detail refresh', async () => {
  const f = fixture('corrections'); const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Cancel application' }))
  const cancel = within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel application' })
  f.failed.add('detail'); await f.refresh('detail')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Cancel application' })).not.toBeInTheDocument()
  fireEvent.click(cancel); expect(f.posts()).toBe(0)
})

it.each([
  ['status', 'SUBMITTED'], ['productCode', 'COLLATERAL_LOAN'], ['originationChannel', 'STAFF_ASSISTED'],
] as const)('removes cancellation when current detail changes %s to %s', async (field, value) => {
  const f = fixture('corrections'); const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Cancel application' }))
  f.data.detail[field] = value; await f.refresh('detail')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Cancel application' })).not.toBeInTheDocument()
  expect(f.posts()).toBe(0)
})

it.each([
  ['complete', 'tasks'], ['complete', 'checklist'], ['upload', 'tasks'], ['upload', 'checklist'],
  ['resubmit', 'index'], ['resubmit', 'tasks'],
] as const)('guards correction %s against live %s revalidation before React rerenders', async (command, read) => {
  const f = fixture('corrections', command === 'resubmit'); const user = userEvent.setup()
  let button: HTMLElement
  if (command === 'upload') {
    await user.upload(await screen.findByLabelText('Choose replacement file'), new File(['fictional'], 'new.pdf', { type: 'application/pdf' }))
    button = screen.getByRole('button', { name: 'Replace document' })
  } else button = await screen.findByRole('button', { name: command === 'complete' ? 'Mark as complete' : 'Submit updates' })
  expect(button).toBeEnabled()
  let resolve!: (response: Response) => void
  f.defer(read, new Promise(done => { resolve = done }))
  let refresh!: Promise<void>
  await act(async () => {
    refresh = queryClient.refetchQueries({ queryKey: keys[read] })
    fireEvent.click(button)
    expect(f.posts()).toBe(0)
  })
  f.release(); await act(async () => { resolve(json(f.data[read])); await refresh })
  expect(f.posts()).toBe(0)
})
