import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers/AppProviders'
import { queryClient } from '@/app/providers/query-client'
import { applicationKeys } from '@/features/applications/application-queries'
import { formatMoney } from '@/lib/format/presentation'
import { createTestAuthManager } from '@/test/auth'

import { createTestRouter } from './router'

const id = '10000000-0000-4000-8000-000000000001'
const detail = {
  loanApplicationId: id, applicationNumber: 'APP-20261005-000001',
  productCode: 'UNSECURED_CONSUMER_LOAN', productType: 'UNSECURED',
  originationChannel: 'CUSTOMER_DIGITAL', requestedAmount: 25_000_000,
  requestedTermMonths: 12, status: 'SUBMITTED', submittedAt: '2026-10-05T08:00:00',
}
const collateral = {
  collateralType: 'MOTORBIKE', description: '<strong>Submitted motorbike</strong>',
  estimatedValue: 35_000_000, ownershipStatus: 'Customer owned', conditionNote: 'Normal used condition',
}
const checklist = {
  checklistId: '20000000-0000-4000-8000-000000000001', loanApplicationId: id,
  stage: 'SUBMISSION', uploadComplete: false, processingReady: false,
  items: [{ checklistItemId: '30000000-0000-4000-8000-000000000001', documentType: 'COLLATERAL_OWNERSHIP_EVIDENCE',
    requirementStatus: 'REQUIRED', customerStatus: 'NOT_UPLOADED', uploadComplete: false,
    processingReady: false, currentVersion: null }],
}

it('does not repeat a cached no-action claim when application detail refresh fails', async () => {
  let failed = false
  fixture({ detailResponse: async () => failed ? json({ status: 403, errorCode: 'FORBIDDEN', message: 'Internal failure' }, 403) : json(detail) })
  expect(await screen.findByText('No action needed')).toBeVisible()
  failed = true
  await act(async () => { await queryClient.refetchQueries({ queryKey: applicationKeys.detail(id) }) })
  expect(await screen.findByText('Application details could not be loaded')).toBeVisible()
  expect(screen.queryByText('No action needed')).not.toBeInTheDocument()
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function fixture(options: {
  productCode?: string; channel?: string; status?: string; requiredAction?: string
  collateralResponse?: () => Promise<Response>; detailResponse?: () => Promise<Response>
  path?: string; documentStatus?: string
} = {}) {
  const application = { ...detail, productCode: options.productCode ?? detail.productCode,
    originationChannel: options.channel ?? 'CUSTOMER_DIGITAL', status: options.status ?? 'SUBMITTED' }
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith(`/loan-applications/${id}/collateral`)) return options.collateralResponse?.() ?? json(collateral)
    if (url.endsWith(`/loan-applications/${id}/documents`)) return json({ ...checklist, items: checklist.items.map((item) => ({ ...item, customerStatus: options.documentStatus ?? item.customerStatus })) })
    if (url.endsWith(`/loan-applications/${id}`)) return options.detailResponse?.() ?? json(application)
    if (url.endsWith('/loan-applications')) return json([{ ...application, lifecycleActive: true, requiredAction: options.requiredAction ?? 'NONE' }])
    throw new Error(`Unexpected request: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  const router = createTestRouter([options.path ?? `/applications/${id}`])
  render(<AppProviders router={router} authManager={createTestAuthManager()} />)
  return { fetchMock, router }
}

afterEach(() => {
  queryClient.clear()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

it.each(['SUBMITTED', 'UNDER_REVIEW', 'APPROVAL_PENDING', 'CUSTOMER_ACCEPTANCE_PENDING', 'CONTRACT_PENDING', 'REJECTED', 'DISBURSED'])('UCL records remain discoverable with no action in %s', async (status) => {
  const { fetchMock } = fixture({ status })
  expect(await screen.findByText('No action needed')).toBeVisible()
  const records = await screen.findByText('Application records')
  expect(records).toBeVisible()
  expect(screen.getByRole('link', { name: 'View documents' })).toHaveAttribute('href', `/applications/${id}/documents`)
  expect(screen.queryByText('Upload documents')).not.toBeInTheDocument()
  expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/collateral'))).toBe(false)
})

it('UCL records link opens existing Documents and deterministic Application back navigation', async () => {
  const user = userEvent.setup()
  const { router } = fixture()
  await user.click(await screen.findByRole('link', { name: 'View documents' }))
  expect(await screen.findByRole('heading', { name: 'Documents' })).toBeVisible()
  expect(router.state.location.pathname).toBe(`/applications/${id}/documents`)
  expect(await screen.findByText('Documents provided: Not complete')).toBeVisible()
  const back = screen.getByRole('link', { name: 'Back to application' })
  expect(back).toHaveAttribute('href', `/applications/${id}`)
  await user.click(back)
  expect(await screen.findByText('No action needed')).toBeVisible()
  expect(router.state.location.pathname).toBe(`/applications/${id}`)
})

it.each(['SUBMITTED', 'UNDER_REVIEW', 'APPROVAL_PENDING', 'CUSTOMER_ACCEPTANCE_PENDING', 'CONTRACT_PENDING', 'REJECTED', 'DISBURSED'])('Collateral renders only immutable submitted facts and records in %s', async (status) => {
  const { fetchMock } = fixture({ productCode: 'COLLATERAL_LOAN', status })
  expect(await screen.findByText(collateral.description)).toBeVisible()
  expect(screen.getByText('Motorbike')).toBeVisible()
  expect(screen.getByText(formatMoney(collateral.estimatedValue), { normalizer: (value) => value })).toHaveClass('tabular-nums')
  expect(screen.getByText(collateral.ownershipStatus)).toBeVisible()
  expect(screen.getByText(collateral.conditionNote)).toBeVisible()
  expect(screen.getByRole('link', { name: 'View documents' })).toHaveAttribute('href', `/applications/${id}/documents`)
  expect(await screen.findByText('No action needed')).toBeVisible()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument()
  expect(screen.queryByText(/LTV|loan.to.value|valuation result|approved collateral/i)).not.toBeInTheDocument()
  expect(screen.getByText(collateral.description).querySelector('strong')).toBeNull()
  const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/collateral'))
  expect(String(call?.[0])).toMatch(new RegExp(`/loan-applications/${id}/collateral$`))
  expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/staff/'))).toBe(false)
})

it('future Collateral types render neutrally with the rest of the facts intact', async () => {
  fixture({ productCode: 'COLLATERAL_LOAN', collateralResponse: async () => json({ ...collateral, collateralType: 'FUTURE_TYPE' }) })
  expect(await screen.findByText('Type unavailable')).toBeVisible()
  expect(screen.getByText(collateral.description)).toBeVisible()
  expect(screen.getByRole('heading', { name: detail.applicationNumber, level: 2 })).toBeVisible()
})

it('Collateral loading and error stay local and retry restores facts', async () => {
  const user = userEvent.setup()
  let resolve!: (response: Response) => void
  const pending = new Promise<Response>((done) => { resolve = done })
  let calls = 0
  fixture({ productCode: 'COLLATERAL_LOAN', collateralResponse: () => ++calls === 1 ? pending : Promise.resolve(json(collateral)) })
  expect(await screen.findByLabelText('Loading collateral details')).toBeVisible()
  expect(screen.getByRole('heading', { name: detail.applicationNumber, level: 2 })).toBeVisible()
  expect(screen.getByRole('link', { name: 'View documents' })).toBeVisible()
  resolve(json({ timestamp: '2026-10-05T08:00:00Z', status: 409, errorCode: 'SYSTEM_STATE_CONFLICT', message: 'Authoritative collateral facts are inconsistent.', path: `/api/v1/loan-applications/${id}/collateral` }, 409))
  expect(await screen.findByText('Collateral details could not be loaded')).toBeVisible()
  expect(screen.getByRole('heading', { name: detail.applicationNumber, level: 2 })).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByText(collateral.description)).toBeVisible()
  expect(calls).toBe(2)
})

it('Staff-assisted Collateral reads facts and documents while suppressing advertised digital actions', async () => {
  const user = userEvent.setup()
  fixture({ productCode: 'COLLATERAL_LOAN', channel: 'STAFF_ASSISTED', requiredAction: 'UPLOAD_DOCUMENTS' })
  expect(await screen.findByText(collateral.description)).toBeVisible()
  expect(screen.getByText('Application handled with Meridian staff')).toBeVisible()
  expect(screen.queryByText('No action needed')).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Upload documents' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('link', { name: 'View documents' }))
  expect(await screen.findByText('Documents provided: Not complete')).toBeVisible()
  expect(screen.getByText(/Documents for this application are handled with Meridian staff/)).toBeVisible()
  expect(screen.queryByLabelText(/Choose.*file/)).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Upload document' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Replace document' })).not.toBeInTheDocument()
})

it.each(['NOT_UPLOADED', 'REPLACEMENT_REQUESTED'])('direct Staff-assisted Documents %s remains read-only with Application back link', async (documentStatus) => {
  fixture({ channel: 'STAFF_ASSISTED', path: `/applications/${id}/documents`, documentStatus })
  expect(await screen.findByText('Documents provided: Not complete')).toBeVisible()
  expect(screen.getByRole('link', { name: 'Back to application' })).toHaveAttribute('href', `/applications/${id}`)
  expect(screen.queryByLabelText(/Choose.*file/)).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Upload document|Replace document/ })).not.toBeInTheDocument()
})

it('Salary Advance retains its existing summary and action state without the new records or Collateral query', async () => {
  const { fetchMock } = fixture({ productCode: 'SALARY_ADVANCE' })
  expect(await screen.findByText('No action needed')).toBeVisible()
  expect(screen.getByRole('heading', { name: detail.applicationNumber, level: 2 })).toBeVisible()
  expect(screen.queryByText('Application records')).not.toBeInTheDocument()
  expect(screen.queryByText('Collateral details')).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'View documents' })).not.toBeInTheDocument()
  expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/collateral'))).toBe(false)
})

it('Collateral is not queried from the index while authoritative detail is unavailable', async () => {
  let resolve!: (response: Response) => void
  const pending = new Promise<Response>((done) => { resolve = done })
  const { fetchMock } = fixture({ productCode: 'COLLATERAL_LOAN', detailResponse: () => pending })
  expect(await screen.findByLabelText('Loading application details')).toBeVisible()
  await waitFor(() => expect(fetchMock.mock.calls.length).toBe(2))
  expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/collateral'))).toBe(false)
  resolve(json({ timestamp: '2026-10-05T08:00:00Z', status: 404, errorCode: 'LOAN_APPLICATION_NOT_FOUND', message: 'Loan Application was not found.', path: `/api/v1/loan-applications/${id}` }, 404))
  const heading = await screen.findByRole('heading', { name: 'Application unavailable', level: 1 })
  expect(heading).toBeVisible()
  expect(within(document.body).queryByText('Collateral details')).not.toBeInTheDocument()
  expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/collateral'))).toBe(false)
})
