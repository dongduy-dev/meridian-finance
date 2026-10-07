const states = new Map([
  ['PENDING', 'Field extraction queued'],
  ['PROCESSING', 'Extracting document fields'],
  ['COMPLETED', 'Field extraction completed'],
  ['FAILED', 'Field extraction unavailable'],
])

export function ocrStateLabel(value: string): string {
  return states.get(value) ?? 'Extraction status unavailable'
}

const failures = new Map([
  ['SOURCE_NOT_FOUND', 'The document is unavailable for field extraction.'],
  ['SOURCE_INTEGRITY_MISMATCH', 'The document could not be validated for field extraction.'],
  ['UNSUPPORTED_SOURCE', 'This document format cannot be used for field extraction.'],
  ['PROVIDER_UNAVAILABLE', 'The field extraction service is unavailable.'],
  ['PROVIDER_REJECTED', 'The field extraction service could not accept this document.'],
  ['PROVIDER_ERROR', 'The field extraction service could not complete this document.'],
  ['CONFIGURATION_ERROR', 'Field extraction is unavailable. Contact a Meridian administrator.'],
])

export function ocrFailureLabel(value: string | null): string {
  return failures.get(value ?? '') ?? 'Field extraction could not be completed.'
}
