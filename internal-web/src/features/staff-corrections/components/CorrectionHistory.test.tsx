import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { historicalCorrectionSchema } from '../api/contracts'
import { CorrectionHistory } from './CorrectionHistory'

const app = '11111111-1111-4111-8111-111111111111'
const id = '22222222-2222-4222-8222-222222222222'
const baseline = '33333333-3333-4333-8333-333333333333'
const item = '44444444-4444-4444-8444-444444444444'
const staff = { actorType: 'STAFF', staffActor: { userId: '00000000-0000-0000-0000-000000000302', displayName: 'Fictional recorder', email: 'recorder@meridian.local' } }
const self = { actorType: 'CUSTOMER_SELF_SERVICE', staffActor: null }
const recorded = { correctionRequestId: id, status: 'RESUBMITTED', reasonCode: 'DOCUMENT_REPLACEMENT_REQUIRED', sourceAction: 'REQUEST_REPLACEMENT', sourceReviewCycleId: null,
  createdBy: staff, createdAt: '2026-10-01T10:00:00', readyAt: '2026-10-02T10:00:00', resubmittedAt: '2026-10-02T11:00:00', cancelledAt: null,
  resubmittedBy: self, resultingApplicationStatus: 'SUBMITTED', tasks: [{ taskId: item, sequence: 1, responsibleParty: 'CUSTOMER', scope: 'DOCUMENT_REPLACEMENT', documentType: 'BANK_STATEMENT',
    checklistItemId: item, baselineDocumentVersionId: baseline, customerInstruction: 'Provide all pages.', staffInstruction: null, createdAt: '2026-10-01T10:00:00', status: 'COMPLETED', completedBy: staff, completedAt: '2026-10-02T10:00:00' }] }

describe('read-only correction provenance', () => {
  it('preserves ordered requests, Customer responsibility and Staff recorder, with only an exact baseline link', () => {
    const cancelled = { ...recorded, correctionRequestId: baseline, status: 'CANCELLED', resubmittedAt: null, resubmittedBy: null, cancelledAt: '2026-10-02T12:00:00', resultingApplicationStatus: null, tasks: [] }
    render(<MemoryRouter><CorrectionHistory loanApplicationId={app} history={[historicalCorrectionSchema.parse(recorded), historicalCorrectionSchema.parse(cancelled)]} latestId={baseline} canReadDocuments /></MemoryRouter>)
    expect(screen.getByText('Correction 1')).toBeVisible()
    expect(screen.getByText('Correction 2 · Latest')).toBeVisible()
    expect(screen.getByText('Customer task recorded by')).toBeVisible()
    expect(screen.getByText('Customer — self-service')).toBeVisible()
    expect(screen.getByText('Cancelled at')).toBeVisible()
    expect(screen.getByText(/exact completion-proof version was not retained/)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Open baseline document' })).toHaveAttribute('href', `/staff/applications/${app}/documents?checklistItemId=${item}&documentVersionId=${baseline}`)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByText(staff.staffActor.userId)).not.toBeInTheDocument()
  })

  it('keeps unknown actors neutral and hides baseline content navigation without the Document capability', () => {
    render(<MemoryRouter><CorrectionHistory loanApplicationId={app} history={[historicalCorrectionSchema.parse({ ...recorded, resubmittedBy: { actorType: 'FUTURE_ACTOR', staffActor: null } })]} canReadDocuments={false} /></MemoryRouter>)
    expect(screen.getByText('Actor unavailable')).toBeVisible()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })
})
