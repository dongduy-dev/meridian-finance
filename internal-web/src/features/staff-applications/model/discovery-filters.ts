export const APPLICATION_NUMBER_MAX_LENGTH = 50
export const submissionDateMin = '0001-01-01'
export const submissionDateMax = '9999-12-31'

export function normalizeApplicationNumber(value: string): string {
  return value.trim().toUpperCase()
}

export function isValidApplicationNumber(value: string): boolean {
  const normalized = normalizeApplicationNumber(value)
  return normalized === '' || (value.length <= APPLICATION_NUMBER_MAX_LENGTH
    && /^(SA|UCL|CL)-[0-9]{8}-[0-9]{6,}$/.test(normalized))
}

export function isValidSubmissionDate(value: string): boolean {
  if (value === '') return true
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < submissionDateMin || value > submissionDateMax) return false
  const [year, month, day] = value.split('-').map(Number) as [number, number, number]
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  return month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1]!
}

export function parseApplicationPage(value: string | null): number | undefined {
  if (value === null) return 0
  if (!/^\d+$/.test(value)) return undefined
  const page = Number(value)
  return Number.isInteger(page) && page <= 2_147_483_647 ? page : undefined
}

export const discoveryParameterNames = [
  'applicationNumber', 'productCode', 'status', 'originationChannel', 'submittedFrom', 'submittedTo', 'page',
] as const
