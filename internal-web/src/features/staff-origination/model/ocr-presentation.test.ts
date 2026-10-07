import { describe, expect, it } from 'vitest'
import { ocrFailureLabel, ocrStateLabel } from './ocr-presentation'

describe('extraction presentation', () => {
  it('distinguishes queued and running extraction', () => {
    expect(ocrStateLabel('PENDING')).toBe('Field extraction queued')
    expect(ocrStateLabel('PROCESSING')).toBe('Extracting document fields')
  })
  it('keeps unfamiliar states and failure categories neutral', () => {
    expect(ocrStateLabel('FUTURE_STATE')).toBe('Extraction status unavailable')
    expect(ocrFailureLabel('PRIVATE_PROVIDER_CODE')).toBe('Field extraction could not be completed.')
    expect(ocrFailureLabel(null)).toBe('Field extraction could not be completed.')
    expect(ocrFailureLabel('SOURCE_INTEGRITY_MISMATCH')).toMatch(/document could not be validated/)
  })
})
