import { describe, expect, it } from 'vitest'
import { formatDateOnly, formatTimestamp, formatVnd } from './presentation'

describe('internal presentation formatting', () => {
  it('formats finite whole-VND values through the centralized VND policy', () => {
    expect(formatVnd(1_234_567)).toBe(new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      maximumFractionDigits: 0,
    }).format(1_234_567))
    expect(formatVnd(Number.NaN)).toBe('Amount unavailable')
  })

  it('formats a calendar date without converting it through the browser time zone', () => {
    expect(formatDateOnly('2026-01-01')).toBe('1 Jan 2026')
  })

  it.each(['2026-02-30', '2026-13-01', 'not-a-date', '', null])('returns a safe date-only fallback for %s', (value) => {
    expect(formatDateOnly(value)).toBe('Date unavailable')
  })

  it('formats an offset timestamp in the explicit Asia/Ho_Chi_Minh policy', () => {
    const formatted = formatTimestamp('2026-01-01T00:00:00Z')
    expect(formatted).toContain('01 Jan 2026')
    expect(formatted).toContain('07:00')
    expect(formatted).toMatch(/GMT\+7|ICT/)
  })

  it.each(['2026-01-01T00:00:00', 'not-a-date', '', null])('returns a safe timestamp fallback for %s', (value) => {
    expect(formatTimestamp(value)).toBe('Date unavailable')
  })
})
