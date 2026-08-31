import { queryClient } from '@/app/providers/query-client'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { ApiError } from '@/lib/api'
import { createAuthApiMock, createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

const applicationId = '10000000-0000-4000-8000-000000000001'
const secondApplicationId = '10000000-0000-4000-8000-000000000002'
const supportingTaskId = '20000000-0000-4000-8000-000000000001'
const replacementTaskId = '20000000-0000-4000-8000-000000000002'
const reviewTaskId = '20000000-0000-4000-8000-000000000003'
const correctionRequestId = '30000000-0000-4000-8000-000000000001'
const supportingItemId = '40000000-0000-4000-8000-000000000001'
const replacementItemId = '40000000-0000-4000-8000-000000000002'
const versionId = '50000000-0000-4000-8000-000000000001'

const summary = {
  loanApplicationId: applicationId,
  applicationNumber: 'UCL-20260831-000001',
  productCode: 'UNSECURED_CONSUMER_LOAN',
  productType: 'UNSECURED',
  requestedAmount: 8_000_000,
  requestedTermMonths: 6,
  status: 'RETURNED_FOR_REVISION',
  submittedAt: '2026-08-31T08:00:00',
  lifecycleActive: true,
  requiredAction: 'COMPLETE_CORRECTIONS',
}

const detail = {
  loanApplicationId: applicationId,
  applicationNumber: summary.applicationNumber,
  productCode: summary.productCode,
  productType: summary.productType,
  requestedAmount: summary.requestedAmount,
  requestedTermMonths: summary.requestedTermMonths,
  status: summary.status,
  submittedAt: summary.submittedAt,
}

const supportingTask = {
  correctionTaskId: supportingTaskId,
  correctionRequestId,
  status: 'OPEN',
  scope: 'SUPPORTING_DOCUMENT_UPLOAD',
  documentType: 'INCOME_PROOF',
  checklistItemId: supportingItemId,
  reasonCode: 'RECENT_PAYSLIP_REQUIRED',
  customerInstruction: 'Upload a recent payslip with the full filename kept readable on narrow screens.',
  createdAt: '2026-08-31T09:00:00',
  completedAt: null,
}

const replacementTask = {
  ...supportingTask,
  correctionTaskId: replacementTaskId,
  scope: 'DOCUMENT_REPLACEMENT',
  documentType: 'BANK_STATEMENT',
  checklistItemId: replacementItemId,
  reasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED',
  customerInstruction: 'Replace the bank statement with a readable current version.',
}

const reviewTask = {
  ...supportingTask,
  correctionTaskId: reviewTaskId,
  scope: 'DOCUMENT_REVIEW',
  checklistItemId: replacementItemId,
  reasonCode: 'FUTURE_REASON',
  customerInstruction: 'Unexpected review instruction.',
}

const currentVersion = {
  documentVersionId: versionId,
  checklistItemId: replacementItemId,
  versionNumber: 1,
  originalFilename: 'a-very-long-bank-statement-filename-for-responsive-correction-review.pdf',
  mimeType: 'application/pdf',
  byteSize: 4096,
  uploadedAt: '2026-08-31T08:30:00',
}

const checklist = {
  checklistId: '60000000-0000-4000-8000-000000000001',
  loanApplicationId: applicationId,
  stage: 'CORRECTION',
  uploadComplete: false,
  processingReady: false,
  items: [
    { checklistItemId: supportingItemId, documentType: 'INCOME_PROOF', requirementStatus: 'REQUIRED', customerStatus: 'NOT_UPLOADED', uploadComplete: false, processingReady: false, currentVersion: null },
    { checklistItemId: replacementItemId, documentType: 'BANK_STATEMENT', requirementStatus: 'REQUIRED', customerStatus: 'REPLACEMENT_REQUESTED', uploadComplete: true, processingReady: false, currentVersion },
  ],
}

interface FixtureState {
  applications: Array<Record<string, unknown>>
  detail: Record<string, unknown>
  tasks: Array<Record<string, unknown>>
  checklist: typeof checklist
  indexFailureOnce?: boolean
  detailNotFound?: boolean
  taskQueryFailure?: boolean
  proofMissing?: boolean
  completionUncertainOnce?: boolean
  resubmissionUncertainOnce?: boolean
  cancellationUncertainOnce?: boolean
  taskCompleted?: boolean
  resubmitted?: boolean
  cancelled?: boolean
  indexReads: number
  completionBodies: Array<Record<string, string>>
  resubmissionBodies: Array<Record<string, string>>
  cancellationBodies: Array<Record<string, string>>
  uploadPosts: number
}

function state(overrides: Partial<FixtureState> = {}): FixtureState {
  return {
    applications: [summary, { ...summary, loanApplicationId: secondApplicationId, applicationNumber: 'APP-FUTURE-STATE', status: 'FUTURE_APPLICATION_STATUS', requiredAction: 'FUTURE_ACTION' }],
    detail,
    tasks: [supportingTask, replacementTask, reviewTask],
    checklist,
    indexReads: 0,
    completionBodies: [],
    resubmissionBodies: [],
    cancellationBodies: [],
    uploadPosts: 0,
    ...overrides,
  }
}

function json(body: unknown, status = 200, requestId?: string) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...(requestId ? { 'X-Request-ID': requestId } : {}) },
  })
}

function error(path: string, errorCode: string, status = 409) {
  return json({ timestamp: '2026-08-31T10:00:00Z', status, errorCode, message: `Safe ${errorCode} response.`, path }, status, 'support-reference-cp8')
}

function fixtureFetch(fixture: FixtureState) {
  return async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (url.endsWith(`/loan-applications/${applicationId}/corrections/tasks/${supportingTaskId}/complete`) && method === 'POST') {
      fixture.completionBodies.push(JSON.parse(String(init?.body)))
      if (fixture.completionUncertainOnce && fixture.completionBodies.length === 1) throw new TypeError('uncertain completion result')
      if (fixture.proofMissing) return error(url, 'CORRECTION_TASK_PROOF_MISSING')
      fixture.taskCompleted = true
      return json({ ...supportingTask, status: 'COMPLETED', completedAt: '2026-08-31T10:00:00' })
    }
    if (url.endsWith(`/loan-applications/${applicationId}/corrections/resubmit`) && method === 'POST') {
      fixture.resubmissionBodies.push(JSON.parse(String(init?.body)))
      if (fixture.resubmissionUncertainOnce && fixture.resubmissionBodies.length === 1) throw new TypeError('uncertain resubmission result')
      fixture.resubmitted = true
      return json({ correctionRequestId, loanApplicationId: applicationId, loanApplicationStatus: 'SUBMITTED', resubmissionRequestId: fixture.resubmissionBodies.at(-1)?.resubmissionRequestId, resubmittedAt: '2026-08-31T10:05:00' })
    }
    if (url.endsWith(`/loan-applications/${applicationId}/cancel`) && method === 'POST') {
      fixture.cancellationBodies.push(JSON.parse(String(init?.body)))
      if (fixture.cancellationUncertainOnce && fixture.cancellationBodies.length === 1) throw new TypeError('uncertain cancellation result')
      fixture.cancelled = true
      return json({ loanApplicationId: applicationId, resultingStatus: 'CANCELLED', cancelledAt: '2026-08-31T10:10:00', idempotentReplay: fixture.cancellationBodies.length > 1 })
    }
    if (url.includes('/documents/') && url.endsWith('/versions') && method === 'POST') {
      fixture.uploadPosts += 1
      return json({ ...currentVersion, documentVersionId: '70000000-0000-4000-8000-000000000001', checklistItemId: supportingItemId, originalFilename: 'uploaded.pdf', uploadedAt: '2026-08-31T10:00:00' }, 201)
    }
    if (url.endsWith(`/loan-applications/${applicationId}/corrections/tasks`) && method === 'GET') {
      if (fixture.taskQueryFailure) return error(url, 'QUERY_UNAVAILABLE', 400)
      return json(fixture.tasks.map((task) => task.correctionTaskId === supportingTaskId && fixture.taskCompleted ? { ...task, status: 'COMPLETED', completedAt: '2026-08-31T10:00:00' } : task))
    }
    if (url.endsWith(`/loan-applications/${applicationId}/documents`) && method === 'GET') return json(fixture.checklist)
    if (url.endsWith(`/loan-applications/${applicationId}`) && method === 'GET') {
      if (fixture.detailNotFound) return error(url, 'LOAN_APPLICATION_NOT_FOUND', 404)
      if (fixture.cancelled) return json({ ...fixture.detail, status: 'CANCELLED' })
      if (fixture.resubmitted) return json({ ...fixture.detail, status: 'SUBMITTED' })
      return json(fixture.detail)
    }
    if (url.endsWith('/loan-applications') && method === 'GET') {
      fixture.indexReads += 1
      if (fixture.indexFailureOnce && fixture.indexReads === 1) return error(url, 'QUERY_UNAVAILABLE', 400)
      return json(fixture.applications.map((application) => application.loanApplicationId === applicationId && (fixture.resubmitted || fixture.cancelled) ? { ...application, status: fixture.cancelled ? 'CANCELLED' : 'SUBMITTED', requiredAction: 'NONE', lifecycleActive: !fixture.cancelled } : application))
    }
    throw new Error(`Unexpected request: ${method} ${url}`)
  }
}

function renderRoute(path: string, fixture: FixtureState) {
  const fetchMock = vi.fn(fixtureFetch(fixture))
  vi.stubGlobal('fetch', fetchMock)
  const router = createTestRouter([path])
  render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return { fetchMock, router }
}

afterEach(() => {
  queryClient.clear()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('FE-CP8 application tracking', () => {
  it('renders the backend-ordered application index with safe status and real detail routes', async () => {
    renderRoute('/applications', state())
    expect(await screen.findByRole('heading', { name: 'Applications' })).toHaveFocus()
    const links = await screen.findAllByRole('link', { name: 'View application' })
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      `/applications/${applicationId}`,
      `/applications/${secondApplicationId}`,
    ])
    expect(screen.getByText('Status unavailable')).toBeVisible()
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
    expect(screen.queryByText(/timeline/i)).not.toBeInTheDocument()
  })

  it('handles application index empty and error/retry states without invented controls', async () => {
    const user = userEvent.setup()
    const empty = state({ applications: [] })
    const first = renderRoute('/applications', empty)
    expect(await screen.findByText('No applications yet')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Explore products' })).toHaveAttribute('href', '/products')
    first.router.dispose()
    queryClient.clear()
    cleanup()

    const failed = state({ indexFailureOnce: true })
    renderRoute('/applications', failed)
    expect(await screen.findByText('Applications could not be loaded')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText(summary.applicationNumber)).toBeVisible()
  })

  it('loads safe application detail, uses only the indexed action, and fabricates no timeline', async () => {
    let resolveDetail!: (value: Response) => void
    const pendingDetail = new Promise<Response>((resolve) => { resolveDetail = resolve })
    const fixture = state()
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith(`/loan-applications/${applicationId}`)) return pendingDetail
      return fixtureFetch(fixture)(input, init)
    })
    vi.stubGlobal('fetch', fetchMock)
    const router = createTestRouter([`/applications/${applicationId}`])
    render(<AppProviders router={router} authManager={createTestAuthManager()} />)

    expect(await screen.findByLabelText('Loading application details')).toBeVisible()
    resolveDetail(json(detail))
    expect(await screen.findByRole('heading', { name: summary.applicationNumber, level: 1 })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Complete corrections' })).toHaveAttribute('href', `/applications/${applicationId}/corrections`)
    expect(screen.queryByRole('heading', { name: /timeline/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/offer terms|contract details|staff notes/i)).not.toBeInTheDocument()
  })

  it('uses a generic concealed unavailable state for application 404', async () => {
    renderRoute(`/applications/${applicationId}`, state({ detailNotFound: true }))
    expect(await screen.findByRole('heading', { name: 'Application unavailable', level: 1 })).toBeVisible()
    expect(screen.getByText('This application could not be found or is not available to this Customer.')).toBeVisible()
    expect(screen.queryByText('Safe LOAN_APPLICATION_NOT_FOUND response.')).not.toBeInTheDocument()
  })
})

describe('FE-CP8 Customer corrections', () => {
  it('composes upload/replacement from the existing document flow and renders Staff/unknown work read-only', async () => {
    const user = userEvent.setup()
    const fixture = state()
    renderRoute(`/applications/${applicationId}/corrections`, fixture)

    expect(await screen.findByRole('heading', { name: 'Complete corrections' })).toHaveFocus()
    expect(await screen.findByText(supportingTask.customerInstruction)).toBeVisible()
    expect(screen.getByText(replacementTask.customerInstruction)).toBeVisible()
    expect(screen.getByText('Reason unavailable')).toBeVisible()
    expect(screen.getByText('Customer action unavailable')).toBeVisible()
    expect(screen.getByLabelText('Choose file')).toBeVisible()
    expect(screen.getByLabelText('Choose replacement file')).toBeVisible()
    expect(screen.getAllByRole('button', { name: 'Complete task' })).toHaveLength(2)

    await user.upload(screen.getByLabelText('Choose file'), new File(['%PDF'], 'uploaded.pdf', { type: 'application/pdf' }))
    await user.click(screen.getByRole('button', { name: 'Upload document' }))
    expect(await screen.findByText('Document uploaded')).toBeVisible()
    expect(fixture.uploadPosts).toBe(1)
    expect(fixture.completionBodies).toHaveLength(0)
    expect(screen.getAllByText('Open').length).toBeGreaterThan(0)
  })

  it('keeps proof-missing tasks open and refreshes authoritative state', async () => {
    const user = userEvent.setup()
    const fixture = state({ proofMissing: true, tasks: [supportingTask] })
    renderRoute(`/applications/${applicationId}/corrections`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Complete task' }))
    expect(await screen.findByText(/not yet accepted the required evidence as proof/i)).toBeVisible()
    expect(screen.getByText('Open')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Complete task' })).toBeVisible()
  })

  it('reuses one completion identity after an uncertain result and completes only from returned task state', async () => {
    const user = userEvent.setup()
    const fixture = state({ completionUncertainOnce: true, tasks: [supportingTask] })
    renderRoute(`/applications/${applicationId}/corrections`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Complete task' }))
    expect(await screen.findByText(/could not be completed/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Complete task' }))
    expect(await screen.findByText('Meridian reports this Customer task as completed.')).toBeVisible()
    expect(fixture.completionBodies).toHaveLength(2)
    expect(fixture.completionBodies[0]?.completionRequestId).toBe(fixture.completionBodies[1]?.completionRequestId)
  })

  it('offers resubmission only when all returned tasks are completed and the backend action still requires corrections', async () => {
    const user = userEvent.setup()
    const completedTask = { ...supportingTask, status: 'COMPLETED', completedAt: '2026-08-31T10:00:00' }
    const fixture = state({ tasks: [completedTask], resubmissionUncertainOnce: true })
    const { router } = renderRoute(`/applications/${applicationId}/corrections`, fixture)
    const resubmit = await screen.findByRole('button', { name: 'Resubmit corrections' })
    await user.click(resubmit)
    expect(await screen.findByText(/could not be completed/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Resubmit corrections' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/applications/${applicationId}`))
    expect(await screen.findByText('Corrections resubmitted')).toBeVisible()
    expect(screen.getByText(/resulting application status: SUBMITTED/i)).toBeVisible()
    expect(fixture.resubmissionBodies).toHaveLength(2)
    expect(fixture.resubmissionBodies[0]?.resubmissionRequestId).toBe(fixture.resubmissionBodies[1]?.resubmissionRequestId)
  })

  it('shows a calm waiting state and no resubmit when Customer tasks are complete but requiredAction is NONE', async () => {
    const completedTask = { ...supportingTask, status: 'COMPLETED', completedAt: '2026-08-31T10:00:00' }
    renderRoute(`/applications/${applicationId}/corrections`, state({ applications: [{ ...summary, requiredAction: 'NONE' }], tasks: [completedTask] }))
    expect(await screen.findByText('Your Customer tasks are complete')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Resubmit corrections' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Staff work is pending/i)).not.toBeInTheDocument()
  })

  it('keeps correction query errors visible with an explicit retry', async () => {
    renderRoute(`/applications/${applicationId}/corrections`, state({ taskQueryFailure: true }))
    expect(await screen.findByText('Correction tasks could not be loaded')).toBeVisible()
    expect(screen.getAllByRole('button', { name: 'Try again' }).length).toBeGreaterThan(0)
  })
})

describe('FE-CP8 narrow cancellation', () => {
  it.each([
    ['SALARY_ADVANCE', 'RETURNED_FOR_REVISION', true],
    ['UNSECURED_CONSUMER_LOAN', 'RETURNED_FOR_REVISION', true],
    ['COLLATERAL_LOAN', 'RETURNED_FOR_REVISION', false],
    ['UNSECURED_CONSUMER_LOAN', 'UNDER_REVIEW', false],
  ])('uses product %s and status %s without widening cancellation', async (productCode, status, visible) => {
    renderRoute(`/applications/${applicationId}/corrections`, state({ detail: { ...detail, productCode, status } }))
    await screen.findByText(supportingTask.customerInstruction)
    expect(Boolean(screen.queryByRole('button', { name: 'Cancel application' }))).toBe(visible)
  })

  it('confirms cancellation, reuses its identity after uncertainty, and navigates to returned CANCELLED state', async () => {
    const user = userEvent.setup()
    const fixture = state({ tasks: [supportingTask], cancellationUncertainOnce: true })
    const { router } = renderRoute(`/applications/${applicationId}/corrections`, fixture)
    await user.click(await screen.findByRole('button', { name: 'Cancel application' }))
    const dialog = await screen.findByRole('dialog', { name: 'Cancel this application?' })
    await user.click(within(dialog).getByRole('button', { name: 'Cancel application' }))
    expect(await within(dialog).findByText(/could not be completed/i)).toBeVisible()
    await user.click(within(dialog).getByRole('button', { name: 'Cancel application' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/applications/${applicationId}`))
    expect(await screen.findByText('Application cancelled')).toBeVisible()
    expect(screen.getByText(/resulting application status: CANCELLED/i)).toBeVisible()
    expect(fixture.cancellationBodies).toHaveLength(2)
    expect(fixture.cancellationBodies[0]?.requestId).toBe(fixture.cancellationBodies[1]?.requestId)
  })
})

describe('FE-CP8 route protection', () => {
  it.each([
    '/applications',
    `/applications/${applicationId}`,
    `/applications/${applicationId}/corrections`,
  ])('keeps %s behind Customer authentication', async (path) => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh).mockRejectedValue(new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid refresh token.' }))
    const router = createTestRouter([path])
    render(<AppProviders router={router} authManager={new AuthSessionManager(api, vi.fn())} />)
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
  })
})
