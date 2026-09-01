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

  it('formats an explicit UTC timestamp in the Asia/Ho_Chi_Minh policy', () => {
    const formatted = formatTimestamp('2026-01-01T00:00:00Z')
    expect(formatted).toContain('01 Jan 2026')
    expect(formatted).toContain('07:00')
    expect(formatted).toMatch(/GMT\+7|ICT/)
  })

  it('interprets an offset-free Meridian LocalDateTime as UTC', () => {
    expect(formatTimestamp('2026-01-01T00:00:00')).toBe(formatTimestamp('2026-01-01T00:00:00Z'))
  })

  it('continues to support timestamps with an explicit offset', () => {
    expect(formatTimestamp('2026-01-01T07:00:00+07:00')).toBe(formatTimestamp('2026-01-01T00:00:00Z'))
  })

  it.each(['2026-02-30T00:00:00', '2026-02-30T00:00:00Z', '2026-01-01T24:00:00', 'not-a-date', '', null])('returns a safe timestamp fallback for %s', (value) => {
    expect(formatTimestamp(value)).toBe('Date unavailable')
  })
})
