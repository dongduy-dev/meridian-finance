const VND_LOCALE = 'vi-VN'
const INTERNAL_LOCALE = 'en-GB'
const INTERNAL_TIME_ZONE = 'Asia/Ho_Chi_Minh'
const OFFSET_SUFFIX = /(?:Z|[+-]\d{2}:?\d{2})$/i
const API_TIMESTAMP = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:?\d{2})?$/i

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

function hasValidTimestampFields(value: string): boolean {
  const parts = API_TIMESTAMP.exec(value)
  if (!parts) return false

  const timestamp = new Date(0)
  timestamp.setUTCFullYear(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]))
  timestamp.setUTCHours(Number(parts[4]), Number(parts[5]), Number(parts[6] ?? 0), 0)
  return timestamp.getUTCFullYear() === Number(parts[1])
    && timestamp.getUTCMonth() === Number(parts[2]) - 1
    && timestamp.getUTCDate() === Number(parts[3])
    && timestamp.getUTCHours() === Number(parts[4])
    && timestamp.getUTCMinutes() === Number(parts[5])
    && timestamp.getUTCSeconds() === Number(parts[6] ?? 0)
}

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
  if (!value || !hasValidTimestampFields(value)) return 'Date unavailable'
  const hasExplicitOffset = OFFSET_SUFFIX.test(value)
  const normalized = hasExplicitOffset ? value : `${value}Z`
  const timestamp = new Date(normalized)
  return Number.isNaN(timestamp.getTime()) ? 'Date unavailable' : timestampFormatter.format(timestamp)
}
