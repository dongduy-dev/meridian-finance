import { describe, expect, it } from 'vitest'
import { validateCorrectionTasks } from './CorrectionPlanFields'

const options = [{
  documentType: 'INCOME_PROOF',
  checklistItemId: '55555555-5555-4555-8555-555555555555',
  currentDocumentVersionId: '66666666-6666-4666-8666-666666666666',
  allowedScopes: ['DOCUMENT_REPLACEMENT', 'DOCUMENT_REVIEW'],
}]

describe('structured correction validation', () => {
  it('requires both ownership sides for an Approver mixed correction', () => {
    expect(validateCorrectionTasks([{ optionIndex: 0, scope: 'DOCUMENT_REPLACEMENT', responsibleParty: 'CUSTOMER', instruction: 'Replace it.' }], options, 'MIXED'))
      .toMatch(/separate Customer-owned and Staff-owned tasks/i)
  })

  it('rejects APPLICATION_TERMS and non-authoritative targets by construction', () => {
    expect(validateCorrectionTasks([{ optionIndex: 4, scope: 'DOCUMENT_REPLACEMENT', responsibleParty: 'CUSTOMER', instruction: 'Replace it.' }], options, 'CUSTOMER'))
      .toMatch(/authoritative option/i)
  })
})
