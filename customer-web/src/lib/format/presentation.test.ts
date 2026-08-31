import { describe, expect, it } from 'vitest'

import { formatDateOnly } from './presentation'

describe('date-only presentation', () => {
  it('formats a backend calendar date without changing its day', () => {
    const result = formatDateOnly('2026-01-01')
    expect(result).not.toBe('Date unavailable')
    expect(result).toMatch(/2026/)
    expect(result).toMatch(/1/)
  })

  it.each(['2026-02-30', '2026-13-01', 'not-a-date', ''])('returns a safe fallback for %s', (value) => {
    expect(formatDateOnly(value)).toBe('Date unavailable')
  })
})
