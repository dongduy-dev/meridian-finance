import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from '@/lib/query/query-client'
import { productAssessmentFixture } from '@/test/staff-verification-fixture'
import { staffVerificationKeys } from '../api/queries'
import { ProductAssessmentEvidence, ProductAssessmentPanel } from './ProductAssessmentPanel'

const auth = vi.hoisted(() => ({
  manager: { protectedRequest: vi.fn() },
  state: { status: 'authenticated', actor: { permissions: ['approval:decide'] } },
}))
vi.mock('@/features/auth/model/auth-context', () => ({ useAuth: () => auth }))

describe('Product assessment evidence', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.state.actor.permissions = ['approval:decide']
    auth.manager.protectedRequest.mockResolvedValue(productAssessmentFixture())
  })

  it('shows provenance without foregrounding correction identifiers or verification controls', () => {
    render(<ProductAssessmentEvidence data={productAssessmentFixture()} />)
    expect(screen.getByText('Synthetic product assessment evidence.')).toBeVisible()
    expect(screen.getByText('Assessment Officer')).toBeVisible()
    expect(screen.getByText('assessment@meridian.test')).toBeVisible()
    expect(screen.getByText(/Re-verification after correction/)).toBeVisible()
    expect(screen.queryByText('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows pending evidence and unknown outcomes safely', () => {
    const fixture = productAssessmentFixture()
    const cycle = { ...fixture.productVerification.currentCycle, productVerificationResult: 'FUTURE_RESULT',
      sourceCorrectionRequestId: null, reviewedAt: null, reviewedBy: null, assessmentNote: null }
    render(<ProductAssessmentEvidence data={{ ...fixture, productVerification: {
      ...fixture.productVerification, currentCycle: cycle, history: [cycle],
    } }} />)
    expect(screen.getByText('Verification result unavailable')).toBeVisible()
    expect(screen.getByText('Assessment has not been completed.')).toBeVisible()
    expect(screen.getByText('Not yet reviewed')).toBeVisible()
    expect(screen.queryByText('Assessment Officer')).not.toBeInTheDocument()
  })

  it('preserves the recorded assessment when reviewer identity is unavailable', () => {
    const fixture = productAssessmentFixture()
    const cycle = { ...fixture.productVerification.currentCycle, reviewedBy: null }
    render(<ProductAssessmentEvidence data={{ ...fixture, productVerification: {
      ...fixture.productVerification, currentCycle: cycle, history: [cycle],
    } }} />)
    expect(screen.getByText('Staff member unavailable')).toBeVisible()
    expect(screen.getByText('Synthetic product assessment evidence.')).toBeVisible()
  })

  it('links a pending reviewer to the assessment workspace while keeping the shared panel read-only', async () => {
    const fixture = productAssessmentFixture()
    const cycle = { ...fixture.productVerification.currentCycle, productVerificationResult: 'PENDING_MANUAL_REVIEW',
      reviewedAt: null, reviewedBy: null, assessmentNote: null }
    auth.state.actor.permissions = ['loan:review']
    auth.manager.protectedRequest.mockResolvedValue({ ...fixture, productVerification: {
      ...fixture.productVerification, currentCycle: cycle, history: [cycle],
    } })
    render(<QueryClientProvider client={createQueryClient()}><MemoryRouter>
      <ProductAssessmentPanel loanApplicationId={fixture.loanApplicationId} />
    </MemoryRouter></QueryClientProvider>)
    expect(await screen.findByRole('link', { name: 'Open product assessment workspace' })).toHaveAttribute('href',
      `/staff/applications/${fixture.loanApplicationId}/verification`)
    expect(screen.queryByLabelText('Assessment note')).not.toBeInTheDocument()
    expect(screen.getByText('Assessment has not been completed.')).toBeVisible()
  })

  it('allows an Approver read, hides failed-refresh evidence, and removes inactive sensitive queries', async () => {
    const queryClient = createQueryClient()
    const localWrite = vi.spyOn(Storage.prototype, 'setItem')
    const fixture = productAssessmentFixture()
    const view = render(<QueryClientProvider client={queryClient}><MemoryRouter>
      <ProductAssessmentPanel loanApplicationId={fixture.loanApplicationId} />
    </MemoryRouter></QueryClientProvider>)
    expect(await screen.findByText('Synthetic product assessment evidence.')).toBeVisible()
    expect(auth.manager.protectedRequest).toHaveBeenCalledWith(`/staff/loan-applications/${fixture.loanApplicationId}/verification`)
    expect(screen.queryByRole('link', { name: 'Open product assessment workspace' })).not.toBeInTheDocument()
    expect(localWrite).not.toHaveBeenCalled()
    auth.manager.protectedRequest.mockRejectedValue(new Error('read unavailable'))
    await act(async () => { await queryClient.refetchQueries({ queryKey: staffVerificationKeys.case(fixture.loanApplicationId) }) })
    await waitFor(() => expect(screen.queryByText('Synthetic product assessment evidence.')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
    view.unmount()
    await waitFor(() => expect(queryClient.getQueryData(staffVerificationKeys.case(fixture.loanApplicationId))).toBeUndefined())
    localWrite.mockRestore()
  })

  it.each(['loan:read', 'approval:recommend', 'document:review', 'loan:review:all', 'admin:config'])(
    'does not run a restricted read for %s alone', (permission) => {
      auth.state.actor.permissions = [permission]
      render(<QueryClientProvider client={createQueryClient()}><MemoryRouter>
        <ProductAssessmentPanel loanApplicationId={productAssessmentFixture().loanApplicationId} />
      </MemoryRouter></QueryClientProvider>)
      expect(auth.manager.protectedRequest).not.toHaveBeenCalled()
      expect(screen.queryByText('Product assessment / verification')).not.toBeInTheDocument()
    },
  )
})
