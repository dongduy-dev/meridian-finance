const VND_LOCALE = 'vi-VN'
const INTERNAL_LOCALE = 'en-GB'
const INTERNAL_TIME_ZONE = 'Asia/Ho_Chi_Minh'

const vndFormatter = new Intl.NumberFormat(VND_LOCALE, {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

const dateOnlyFormatter = new Intl.DateTimeFormat(INTERNAL_LOCALE, {
  dateStyle: 'medium',
  timeZone: 'UTC',
})

const timestampFormatter = new Intl.DateTimeFormat(INTERNAL_LOCALE, {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: INTERNAL_TIME_ZONE,
  timeZoneName: 'short',
})

export function formatVnd(value: number): string {
  return Number.isFinite(value) ? vndFormatter.format(value) : 'Amount unavailable'
}

export function formatDateOnly(value?: string | null): string {
  const match = value ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(value) : null
  if (!match) return 'Date unavailable'

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    return 'Date unavailable'
  }
  return dateOnlyFormatter.format(date)
}

export function formatTimestamp(value?: string | null): string {
  if (!value || !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) return 'Date unavailable'
  const timestamp = new Date(value)
  return Number.isNaN(timestamp.getTime()) ? 'Date unavailable' : timestampFormatter.format(timestamp)
}
