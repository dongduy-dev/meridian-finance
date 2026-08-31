import { queryClient } from '@/app/providers/query-client'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { AuthSessionManager } from '@/features/auth/auth-session'
import { ApiError } from '@/lib/api'
import { createAuthApiMock, createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

const applicationId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const itemId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
const currentVersionId = 'cccccccc-cccc-cccc-cccc-cccccccccccc'

const uclProduct = {
  productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'UNSECURED', name: 'Unsecured Consumer Loan', description: 'Evidence-based lending.', active: true,
  minAmount: 2_000_000, maxAmount: 50_000_000,
  policy: { allowedTermsMonths: [3, 6], pricing: { flatMonthlyInterestRate: 0.018, feeAmount: 0 }, interestCalculationMethod: 'FLAT_ORIGINAL_PRINCIPAL', repaymentMethod: 'MONTHLY_INSTALLMENT', offerValidityDays: 7, submissionEvidenceRequirements: [{ documentType: 'INCOME_PROOF', requirementStatus: 'REQUIRED' }, { documentType: 'FUTURE_DOCUMENT', requirementStatus: 'FUTURE_REQUIREMENT' }], eligibilityNotes: ['Evidence is manually reviewed.'] },
}
const collateralProduct = {
  ...uclProduct, productCode: 'COLLATERAL_LOAN', productType: 'SECURED', name: 'Collateral Loan', minAmount: 5_000_000, maxAmount: 100_000_000,
  policy: { ...uclProduct.policy, allowedTermsMonths: [6, 12], submissionEvidenceRequirements: [{ documentType: 'COLLATERAL_OWNERSHIP_EVIDENCE', requirementStatus: 'REQUIRED' }] },
}
const application = {
  loanApplicationId: applicationId, applicationNumber: 'APP-20260831-000001', productType: 'UNSECURED', productCode: 'UNSECURED_CONSUMER_LOAN', status: 'FUTURE_STATUS', requestedAmount: 5_000_000, requestedTermMonths: 6, productVerificationResult: 'FUTURE_RESULT', submittedAt: '2026-08-31T09:00:00',
}
const emptyChecklist = { checklistId: 'dddddddd-dddd-dddd-dddd-dddddddddddd', loanApplicationId: applicationId, stage: 'SUBMISSION', uploadComplete: true, processingReady: true, items: [] }

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function baseFetch(input: RequestInfo | URL, init?: RequestInit) {
  const url = String(input)
  if (url.endsWith('/loan-products/UNSECURED_CONSUMER_LOAN')) return Promise.resolve(response(uclProduct))
  if (url.endsWith('/loan-products/COLLATERAL_LOAN')) return Promise.resolve(response(collateralProduct))
  if (url.endsWith('/loan-applications/unsecured-consumer-loan')) return Promise.resolve(response(application, 201))
  if (url.endsWith('/loan-applications/collateral-loan')) return Promise.resolve(response({ ...application, productCode: 'COLLATERAL_LOAN', productType: 'SECURED', collateralType: 'MOTORBIKE', evidenceRequirements: [] }, 201))
  if (url.endsWith(`/loan-applications/${applicationId}/documents`)) return Promise.resolve(response(emptyChecklist))
  throw new Error(`Unexpected request: ${url}; ${init?.method ?? 'GET'}`)
}

function renderRoute(path: string, fetchImplementation: typeof fetch = baseFetch) {
  const fetchMock = vi.fn(fetchImplementation)
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

describe('FE-CP7 UCL origination', () => {
  it('adds a real Apply action and uses only returned policy terms and evidence', async () => {
    renderRoute('/products/unsecured-consumer-loan')
    expect(await screen.findByRole('link', { name: 'Apply now' })).toHaveAttribute('href', '/products/unsecured-consumer-loan/apply')
    expect(screen.getByText('Income proof')).toBeVisible()
    expect(screen.getAllByText('Status unavailable').length).toBeGreaterThan(0)
  })

  it('preserves Request/Review values, sends the exact body, and navigates to server-backed Documents', async () => {
    const user = userEvent.setup()
    let submittedBody: unknown
    const { router } = renderRoute('/products/unsecured-consumer-loan/apply', async (input, init) => {
      if (String(input).endsWith('/loan-applications/unsecured-consumer-loan')) submittedBody = JSON.parse(String(init?.body))
      return baseFetch(input, init)
    })
    expect(await screen.findByRole('heading', { name: 'Choose your request' })).toHaveFocus()
    const terms = screen.getByRole('combobox', { name: /Requested term/ })
    expect(within(terms).getAllByRole('option').map((option) => option.textContent)).toEqual(['Select a term', '3 months', '6 months'])
    await user.type(screen.getByRole('textbox', { name: /Requested amount/ }), '5000000')
    await user.selectOptions(terms, '6')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    expect(await screen.findByRole('heading', { name: 'Review your application' })).toBeVisible()
    expect(router.state.location.search).toBe('?step=review')
    expect(router.state.location.search).not.toContain('5000000')
    await user.click(screen.getByRole('button', { name: 'Submit application' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/applications/${applicationId}/documents`))
    expect(submittedBody).toEqual({ requestedAmount: 5_000_000, requestedTermMonths: 6 })
    expect(await screen.findByText('Application submitted')).toBeVisible()
    expect(screen.getByText('Status unavailable')).toBeVisible()
    expect(await screen.findByText('No documents are currently required')).toBeVisible()
  })

  it('warns before leaving unsaved form input', async () => {
    const user = userEvent.setup()
    renderRoute('/products/unsecured-consumer-loan/apply')
    await user.type(await screen.findByRole('textbox', { name: /Requested amount/ }), '5000000')
    await user.click(screen.getByRole('link', { name: 'Back to product' }))
    expect(await screen.findByRole('dialog', { name: 'Leave this application?' })).toBeVisible()
  })

  it('focuses the first invalid UCL control after validation', async () => {
    const user = userEvent.setup()
    renderRoute('/products/unsecured-consumer-loan/apply')
    await user.click(await screen.findByRole('button', { name: 'Review request' }))
    await waitFor(() => expect(screen.getByRole('textbox', { name: /Requested amount/ })).toHaveFocus())
  })
})

describe('FE-CP7 route protection', () => {
  it.each([
    '/products/unsecured-consumer-loan/apply',
    '/products/collateral-loan/apply',
    `/applications/${applicationId}/documents`,
  ])('keeps %s behind Customer authentication', async (path) => {
    const api = createAuthApiMock()
    vi.mocked(api.refresh).mockRejectedValue(new ApiError({ status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid refresh token.' }))
    const router = createTestRouter([path])
    render(<AppProviders router={router} authManager={new AuthSessionManager(api, vi.fn())} />)
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.state).toEqual({ from: path })
  })
})

describe('FE-CP7 Collateral origination', () => {
  it('focuses the first invalid Collateral control and connects its error description', async () => {
    const user = userEvent.setup()
    renderRoute('/products/collateral-loan/apply')
    await user.click(await screen.findByRole('button', { name: 'Review request' }))
    const amount = screen.getByRole('textbox', { name: /Requested amount/ })
    await waitFor(() => expect(amount).toHaveFocus())
    expect(amount).toHaveAttribute('aria-describedby', 'requestedAmount-description requestedAmount-error')
  })

  it('offers all five exact types, keeps ownership free text, validates, reviews, and submits nested collateral', async () => {
    const user = userEvent.setup()
    let submittedBody: unknown
    const { router } = renderRoute('/products/collateral-loan/apply', async (input, init) => {
      if (String(input).endsWith('/loan-applications/collateral-loan')) submittedBody = JSON.parse(String(init?.body))
      return baseFetch(input, init)
    })
    await screen.findByRole('heading', { name: 'Describe your request' })
    expect(within(screen.getByRole('combobox', { name: /Collateral type/ })).getAllByRole('option')).toHaveLength(6)
    expect(screen.getByRole('textbox', { name: /Ownership status/ })).toBeVisible()
    await user.type(screen.getByRole('textbox', { name: /Requested amount/ }), '25000000')
    await user.selectOptions(screen.getByRole('combobox', { name: /Requested term/ }), '12')
    await user.selectOptions(screen.getByRole('combobox', { name: /Collateral type/ }), 'MOTORBIKE')
    await user.type(screen.getByRole('textbox', { name: /^Description/ }), '2024 motorbike')
    await user.type(screen.getByRole('textbox', { name: /Estimated value/ }), '35000000')
    await user.type(screen.getByRole('textbox', { name: /Ownership status/ }), 'Owned by Customer')
    await user.type(screen.getByRole('textbox', { name: /Condition note/ }), 'Normal used condition')
    await user.click(screen.getByRole('button', { name: 'Review request' }))
    expect(await screen.findByText('Owned by Customer')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Submit application' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/applications/${applicationId}/documents`))
    expect(submittedBody).toEqual({ requestedAmount: 25_000_000, requestedTermMonths: 12, collateral: { type: 'MOTORBIKE', description: '2024 motorbike', estimatedValue: 35_000_000, ownershipStatus: 'Owned by Customer', conditionNote: 'Normal used condition' } })
    expect(submittedBody).not.toHaveProperty('collateralType')
  })
})

describe('FE-CP7 document workspace', () => {
  const version = { documentVersionId: currentVersionId, checklistItemId: itemId, versionNumber: 1, originalFilename: 'a-very-long-customer-filename-that-must-wrap-safely.pdf', mimeType: 'application/pdf', byteSize: 2048, uploadedAt: '2026-08-31T09:00:00' }
  const statuses = ['NOT_UPLOADED', 'AWAITING_REVIEW', 'ACCEPTED', 'REPLACEMENT_REQUESTED', 'WAIVED', 'FUTURE_STATUS']
  const checklist = { ...emptyChecklist, uploadComplete: false, processingReady: false, items: [...statuses.map((status, index) => ({ checklistItemId: `${index + 1}0000000-0000-0000-0000-000000000000`, documentType: index ? 'BANK_STATEMENT' : 'INCOME_PROOF', requirementStatus: 'REQUIRED', customerStatus: status, uploadComplete: status !== 'NOT_UPLOADED', processingReady: status === 'ACCEPTED' || status === 'WAIVED', currentVersion: status === 'NOT_UPLOADED' || status === 'FUTURE_STATUS' ? null : { ...version, checklistItemId: `${index + 1}0000000-0000-0000-0000-000000000000` } })), { checklistItemId: '70000000-0000-0000-0000-000000000000', documentType: 'EMPLOYMENT_PROOF', requirementStatus: 'REQUIRED', customerStatus: 'REPLACEMENT_REQUESTED', uploadComplete: true, processingReady: false, currentVersion: null }] }

  it('reconnects by direct route, renders returned readiness/status facts, and exposes only allowed actions', async () => {
    renderRoute(`/applications/${applicationId}/documents`, (input, init) => String(input).endsWith(`/loan-applications/${applicationId}/documents`) ? Promise.resolve(response(checklist)) : baseFetch(input, init))
    const heading = await screen.findByRole('heading', { name: 'Documents' })
    await waitFor(() => expect(heading).toHaveFocus())
    expect(screen.queryByRole('navigation', { name: 'Customer navigation' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Step 1 of 1/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Return to Dashboard' })).toHaveAttribute('href', '/')
    expect(screen.getByText('Uploads: Not complete')).toBeVisible()
    expect(screen.getByText('Processing: Not complete')).toBeVisible()
    expect(screen.getByText('An upload exists and is awaiting review. It is not missing.')).toBeVisible()
    expect(screen.getByText('The current document has been accepted.')).toBeVisible()
    expect(screen.getByText('This requirement was waived. No upload is needed.')).toBeVisible()
    expect(screen.getByText('The current document status cannot be described safely.')).toBeVisible()
    expect(screen.getByText('Replacement is temporarily unavailable')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Upload document' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Replace document' })).toBeDisabled()
    expect(screen.getAllByLabelText(/Choose (replacement )?file/)).toHaveLength(2)
  })

  it('keeps the focused evidence presentation while the checklist loads or fails', async () => {
    let resolveChecklist!: (value: Response) => void
    const checklistResponse = new Promise<Response>((resolve) => {
      resolveChecklist = resolve
    })
    renderRoute(`/applications/${applicationId}/documents`, (input, init) => String(input).endsWith(`/loan-applications/${applicationId}/documents`) ? checklistResponse : baseFetch(input, init))

    const heading = await screen.findByRole('heading', { name: 'Documents' })
    expect(await screen.findByLabelText('Loading document checklist')).toBeVisible()
    expect(screen.queryByText(/Step 1 of 1/i)).not.toBeInTheDocument()
    resolveChecklist(response({ timestamp: '2026-08-31T09:00:00Z', status: 400, errorCode: 'QUERY_UNAVAILABLE', message: 'Checklist unavailable.', path: `/api/v1/loan-applications/${applicationId}/documents` }, 400))

    expect(await screen.findByText('Document checklist could not be loaded')).toBeVisible()
    expect(heading).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('uploads explicitly with a stable logical request ID after an uncertain failure', async () => {
    const user = userEvent.setup()
    let posts = 0
    const bodies: FormData[] = []
    const firstOnly = { ...checklist, items: [checklist.items[0]] }
    renderRoute(`/applications/${applicationId}/documents`, async (input, init) => {
      const url = String(input)
      if (url.endsWith(`/loan-applications/${applicationId}/documents`) && init?.method !== 'POST') return response(firstOnly)
      if (url.includes('/versions')) {
        posts += 1
        bodies.push(init?.body as FormData)
        if (posts === 1) throw new TypeError('uncertain network result')
        return response(version)
      }
      return baseFetch(input, init)
    })
    const file = new File(['%PDF'], 'income.pdf', { type: 'application/pdf' })
    await user.upload(await screen.findByLabelText('Choose file'), file)
    await user.click(screen.getByRole('button', { name: 'Upload document' }))
    expect(await screen.findByText(/could not be completed/)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Upload document' }))
    await waitFor(() => expect(posts).toBe(2))
    expect(bodies[0]?.get('uploadRequestId')).toBe(bodies[1]?.get('uploadRequestId'))
    expect(bodies[0]?.has('expectedCurrentVersionId')).toBe(false)
  })

  it('rejects an obviously oversized file locally without claiming server validation', async () => {
    const user = userEvent.setup()
    const firstOnly = { ...checklist, items: [checklist.items[0]] }
    const { fetchMock } = renderRoute(`/applications/${applicationId}/documents`, (input, init) => String(input).endsWith(`/loan-applications/${applicationId}/documents`) ? Promise.resolve(response(firstOnly)) : baseFetch(input, init))
    await user.upload(await screen.findByLabelText('Choose file'), new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.pdf', { type: 'application/pdf' }))
    expect(await screen.findByText('Choose a file no larger than 10 MiB.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Upload document' })).toBeDisabled()
    expect(fetchMock.mock.calls.filter(([input]) => String(input).includes('/versions'))).toHaveLength(0)
  })

  it('sends the loaded replacement baseline, refetches a stale conflict, and never overwrites automatically', async () => {
    const user = userEvent.setup()
    let reads = 0
    let replacementBody: FormData | undefined
    const replacementItem = { ...checklist.items[3], currentVersion: version }
    const replacementChecklist = { ...checklist, items: [replacementItem] }
    renderRoute(`/applications/${applicationId}/documents`, async (input, init) => {
      const url = String(input)
      if (url.endsWith(`/loan-applications/${applicationId}/documents`) && init?.method !== 'POST') {
        reads += 1
        return response(reads === 1 ? replacementChecklist : { ...replacementChecklist, items: [{ ...replacementItem, currentVersion: { ...version, documentVersionId: 'ffffffff-ffff-ffff-ffff-ffffffffffff', versionNumber: 2 } }] })
      }
      if (url.includes('/versions')) {
        replacementBody = init?.body as FormData
        return response({ timestamp: '2026-08-31T09:00:00Z', status: 409, errorCode: 'STALE_DOCUMENT_VERSION', message: 'Document version is stale.', path: url }, 409)
      }
      return baseFetch(input, init)
    })
    const file = new File(['%PDF replacement'], 'replacement.pdf', { type: 'application/pdf' })
    await user.upload(await screen.findByLabelText('Choose replacement file'), file)
    await user.click(screen.getByRole('button', { name: 'Replace document' }))
    expect(await screen.findByText(/document changed since this page loaded/i)).toBeVisible()
    expect(replacementBody?.get('expectedCurrentVersionId')).toBe(currentVersionId)
    expect(reads).toBeGreaterThanOrEqual(2)
    expect(screen.getByText(/Selected: replacement.pdf/)).toBeVisible()
  })
})
