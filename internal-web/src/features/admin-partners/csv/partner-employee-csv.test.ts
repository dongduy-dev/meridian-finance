import { describe, expect, it } from 'vitest'
import { parsePartnerEmployeeCsv } from './partner-employee-csv'

const headers = 'employeeCode,identityReference,salaryAmount,salaryAdvanceLimit,employmentStatus,active'
const validRow = 'EMP-001,012345678901,12000000,4000000,ACTIVE,true'

describe('Partner Employee CSV parsing', () => {
  it('maps a normal CSV into the existing import row shape', () => {
    const result = parsePartnerEmployeeCsv(`${headers}\n${validRow}`)

    expect(result.issues).toEqual([])
    expect(result.rows).toEqual([{
      employeeCode: 'EMP-001',
      identityReference: '012345678901',
      salaryAmount: 12_000_000,
      salaryAdvanceLimit: 4_000_000,
      employmentStatus: 'ACTIVE',
      active: true,
    }])
    expect(result).toMatchObject({ dataRowCount: 1, validRowCount: 1, problemRowCount: 0 })
  })

  it('maps columns by header name when their order differs', () => {
    const result = parsePartnerEmployeeCsv([
      'active,employmentStatus,salaryAdvanceLimit,employeeCode,salaryAmount,identityReference',
      'FALSE, inactive ,5000000, EMP-002 ,15000000, 012345678902 ',
    ].join('\n'))

    expect(result.rows).toEqual([{
      employeeCode: 'EMP-002',
      identityReference: '012345678902',
      salaryAmount: 15_000_000,
      salaryAdvanceLimit: 5_000_000,
      employmentStatus: 'INACTIVE',
      active: false,
    }])
  })

  it('tolerates a UTF-8 BOM and surrounding header whitespace', () => {
    const result = parsePartnerEmployeeCsv(`\uFEFF employeeCode , identityReference , salaryAmount , salaryAdvanceLimit , employmentStatus , active \n${validRow}`)

    expect(result.issues).toEqual([])
    expect(result.rows).toHaveLength(1)
  })

  it('uses normal CSV quoting instead of splitting on commas', () => {
    const result = parsePartnerEmployeeCsv(`${headers}\n"EMP,001","ID,001",12000000,4000000,"active","true"`)

    expect(result.issues).toEqual([])
    expect(result.rows[0]).toMatchObject({ employeeCode: 'EMP,001', identityReference: 'ID,001', employmentStatus: 'ACTIVE' })
  })

  it.each([
    ['LF', '\n'],
    ['CRLF', '\r\n'],
  ])('accepts %s line endings', (_name, newline) => {
    const result = parsePartnerEmployeeCsv(`${headers}${newline}${validRow}${newline}`)

    expect(result.rows).toHaveLength(1)
    expect(result.issues).toEqual([])
  })

  it('ignores empty trailing lines', () => {
    const result = parsePartnerEmployeeCsv(`${headers}\n${validRow}\n\n   \n`)

    expect(result.dataRowCount).toBe(1)
    expect(result.rows).toHaveLength(1)
  })

  it('reports missing required headers clearly', () => {
    const result = parsePartnerEmployeeCsv('employeeCode,identityReference,salaryAmount,salaryAdvanceLimit,active\nEMP-001,ID-001,1,1,true')

    expect(result.issues.map((issue) => issue.message)).toContain('Missing required header: employmentStatus.')
    expect(result.rows).toEqual([])
  })

  it('rejects duplicate headers', () => {
    const result = parsePartnerEmployeeCsv(`${headers},active\n${validRow},false`)

    expect(result.issues.map((issue) => issue.message)).toContain('Duplicate header: active.')
    expect(result.rows).toEqual([])
  })

  it('rejects unexpected headers instead of discarding them', () => {
    const result = parsePartnerEmployeeCsv(`${headers},notes\n${validRow},private`)

    expect(result.issues.map((issue) => issue.message)).toContain('Unexpected header: notes.')
    expect(result.rows).toEqual([])
  })

  it('reports invalid numeric fields without including sensitive row values', () => {
    const result = parsePartnerEmployeeCsv(`${headers}\nEMP-001,ID-SECRET,not-money,4000000,ACTIVE,true`)

    expect(result.problemRowCount).toBe(1)
    expect(result.rows).toEqual([])
    expect(result.issues.map((issue) => issue.message)).toContain('Data row 1 — Salary amount must be a nonnegative number.')
    expect(result.issues.map((issue) => issue.message).join(' ')).not.toContain('ID-SECRET')
  })

  it('reports invalid active values', () => {
    const result = parsePartnerEmployeeCsv(`${headers}\nEMP-001,ID-001,12000000,4000000,ACTIVE,yes`)

    expect(result.issues.map((issue) => issue.message)).toContain('Data row 1 — Active must be true or false.')
    expect(result.rows).toEqual([])
  })

  it('reports unsupported employment statuses', () => {
    const result = parsePartnerEmployeeCsv(`${headers}\nEMP-001,ID-001,12000000,4000000,ON_LEAVE,true`)

    expect(result.issues.map((issue) => issue.message)).toContain('Data row 1 — Employment status is not supported.')
    expect(result.rows).toEqual([])
  })
})
