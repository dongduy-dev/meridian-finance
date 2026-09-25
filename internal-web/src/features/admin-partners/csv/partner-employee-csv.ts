import Papa from 'papaparse'
import { importRowInputSchema, type ImportPartnerEmployeesInput } from '../api/contracts'

export const partnerEmployeeCsvHeaders = [
  'employeeCode',
  'identityReference',
  'salaryAmount',
  'salaryAdvanceLimit',
  'employmentStatus',
  'active',
] as const

type PartnerEmployeeCsvHeader = typeof partnerEmployeeCsvHeaders[number]
type ImportRow = ImportPartnerEmployeesInput['rows'][number]

export type PartnerEmployeeCsvIssue = {
  dataRow?: number
  field?: PartnerEmployeeCsvHeader
  message: string
}

export type PartnerEmployeeCsvPreviewRow = Record<PartnerEmployeeCsvHeader, string> & {
  dataRow: number
  valid: boolean
}

export type PartnerEmployeeCsvResult = {
  dataRowCount: number
  validRowCount: number
  problemRowCount: number
  rows: ImportRow[]
  previewRows: PartnerEmployeeCsvPreviewRow[]
  issues: PartnerEmployeeCsvIssue[]
}

const expectedHeaders = new Set<string>(partnerEmployeeCsvHeaders)
const fieldProblemMessages: Record<PartnerEmployeeCsvHeader, string> = {
  employeeCode: 'Employee code is required and must not exceed 50 characters.',
  identityReference: 'Identity reference is required and must not exceed 100 characters.',
  salaryAmount: 'Salary amount must be a nonnegative number.',
  salaryAdvanceLimit: 'Salary Advance limit must be a nonnegative number.',
  employmentStatus: 'Employment status is not supported.',
  active: 'Active must be true or false.',
}

function headerIssue(message: string): PartnerEmployeeCsvIssue {
  return { message }
}

function rowIssue(
  dataRow: number,
  field: PartnerEmployeeCsvHeader,
  message: string,
): PartnerEmployeeCsvIssue {
  return { dataRow, field, message: `Data row ${dataRow} — ${message}` }
}

function normalizeHeaders(values: string[]) {
  return values.map((value, index) => {
    const withoutBom = index === 0 ? value.replace(/^\uFEFF/, '') : value
    return withoutBom.trim()
  })
}

function validateHeaders(headers: string[]) {
  const issues: PartnerEmployeeCsvIssue[] = []
  const counts = new Map<string, number>()
  headers.forEach((header) => counts.set(header, (counts.get(header) ?? 0) + 1))

  for (const expected of partnerEmployeeCsvHeaders) {
    const count = counts.get(expected) ?? 0
    if (count === 0) issues.push(headerIssue(`Missing required header: ${expected}.`))
    if (count > 1) issues.push(headerIssue(`Duplicate header: ${expected}.`))
  }

  for (const [header, count] of counts) {
    if (!expectedHeaders.has(header)) {
      const label = header || '(empty)'
      issues.push(headerIssue(`Unexpected header: ${label}.`))
      if (count > 1) issues.push(headerIssue(`Duplicate header: ${label}.`))
    }
  }

  return issues
}

function previewValue(row: string[], headerIndexes: Map<PartnerEmployeeCsvHeader, number>, header: PartnerEmployeeCsvHeader) {
  return (row[headerIndexes.get(header) ?? -1] ?? '').trim()
}

export function parsePartnerEmployeeCsv(csv: string): PartnerEmployeeCsvResult {
  const parsed = Papa.parse<string[]>(csv, { skipEmptyLines: 'greedy' })
  const parsedRows = parsed.data
  const rawHeaders = parsedRows[0]
  const dataRows = rawHeaders ? parsedRows.slice(1) : []
  const issues: PartnerEmployeeCsvIssue[] = []

  if (!rawHeaders) {
    return {
      dataRowCount: 0,
      validRowCount: 0,
      problemRowCount: 0,
      rows: [],
      previewRows: [],
      issues: [headerIssue('CSV header row is required.')],
    }
  }

  const headers = normalizeHeaders(rawHeaders)
  const headerIssues = validateHeaders(headers)
  issues.push(...headerIssues)

  const parserProblemRows = new Set<number>()
  let globalParserProblem = false
  for (const error of parsed.errors) {
    const parsedRow = error.row
    if (parsedRow === undefined || parsedRow < 1) {
      globalParserProblem = true
      issues.push(headerIssue('CSV content could not be parsed safely.'))
    } else {
      parserProblemRows.add(parsedRow)
      issues.push({ dataRow: parsedRow, message: `Data row ${parsedRow} — CSV content could not be parsed safely.` })
    }
  }

  if (dataRows.length === 0) issues.push(headerIssue('At least one CSV data row is required.'))

  if (headerIssues.length > 0) {
    return {
      dataRowCount: dataRows.length,
      validRowCount: 0,
      problemRowCount: dataRows.length,
      rows: [],
      previewRows: [],
      issues,
    }
  }

  const headerIndexes = new Map<PartnerEmployeeCsvHeader, number>()
  headers.forEach((header, index) => headerIndexes.set(header as PartnerEmployeeCsvHeader, index))
  const rows: ImportRow[] = []
  const previewRows: PartnerEmployeeCsvPreviewRow[] = []
  const problemRows = new Set<number>()

  dataRows.forEach((rawRow, index) => {
    const dataRow = index + 1
    const rowIssues: PartnerEmployeeCsvIssue[] = []
    const employeeCode = previewValue(rawRow, headerIndexes, 'employeeCode')
    const identityReference = previewValue(rawRow, headerIndexes, 'identityReference')
    const salaryAmountValue = previewValue(rawRow, headerIndexes, 'salaryAmount')
    const salaryAdvanceLimitValue = previewValue(rawRow, headerIndexes, 'salaryAdvanceLimit')
    const employmentStatus = previewValue(rawRow, headerIndexes, 'employmentStatus').toUpperCase()
    const activeValue = previewValue(rawRow, headerIndexes, 'active').toLowerCase()
    const salaryAmount = salaryAmountValue === '' ? Number.NaN : Number(salaryAmountValue)
    const salaryAdvanceLimit = salaryAdvanceLimitValue === '' ? Number.NaN : Number(salaryAdvanceLimitValue)
    const mapped = importRowInputSchema.safeParse({
      employeeCode,
      identityReference,
      salaryAmount,
      salaryAdvanceLimit,
      employmentStatus,
      active: activeValue === 'true' ? true : activeValue === 'false' ? false : activeValue,
    })

    if (rawRow.length !== headers.length) {
      rowIssues.push({ dataRow, message: `Data row ${dataRow} — Expected ${headers.length} columns but found ${rawRow.length}.` })
    }
    if (!mapped.success) {
      const invalidFields = new Set(mapped.error.issues.map((issue) => issue.path[0]))
      for (const field of partnerEmployeeCsvHeaders) {
        if (invalidFields.has(field)) rowIssues.push(rowIssue(dataRow, field, fieldProblemMessages[field]))
      }
    }
    if (globalParserProblem || parserProblemRows.has(dataRow)) problemRows.add(dataRow)
    if (rowIssues.length > 0) problemRows.add(dataRow)
    issues.push(...rowIssues)

    const valid = !globalParserProblem && !parserProblemRows.has(dataRow) && rowIssues.length === 0
    previewRows.push({
      dataRow,
      employeeCode,
      identityReference,
      salaryAmount: salaryAmountValue,
      salaryAdvanceLimit: salaryAdvanceLimitValue,
      employmentStatus,
      active: activeValue,
      valid,
    })

    if (valid && mapped.success) rows.push(mapped.data)
  })

  return {
    dataRowCount: dataRows.length,
    validRowCount: rows.length,
    problemRowCount: problemRows.size,
    rows,
    previewRows,
    issues,
  }
}
