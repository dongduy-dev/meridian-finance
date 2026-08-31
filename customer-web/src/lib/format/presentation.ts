const vndFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

const timestampFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateOnlyFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeZone: 'UTC',
})

const percentFormatter = new Intl.NumberFormat(undefined, {
  style: 'percent',
  minimumFractionDigits: 0,
  maximumFractionDigits: 3,
})

export function formatMoney(value: number) {
  return Number.isFinite(value) ? vndFormatter.format(value) : 'Amount unavailable'
}

export function formatPercentage(value: number) {
  return Number.isFinite(value) ? percentFormatter.format(value) : 'Rate unavailable'
}

export function formatTimestamp(value: string) {
  const normalized = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`
  const timestamp = new Date(normalized)
  return Number.isNaN(timestamp.getTime()) ? 'Date unavailable' : timestampFormatter.format(timestamp)
}

export function formatDateOnly(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return 'Date unavailable'

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  if (
    date.getUTCFullYear() !== year
    || date.getUTCMonth() !== month - 1
    || date.getUTCDate() !== day
  ) {
    return 'Date unavailable'
  }
  return dateOnlyFormatter.format(date)
}

export function formatTerms(terms: number[]) {
  if (terms.length === 0) return 'Terms unavailable'
  return terms.map((term) => `${term} ${term === 1 ? 'month' : 'months'}`).join(', ')
}
