import { describe, expect, it } from 'vitest'
import { employeeStatusLabel, importRejectionLabel } from './presentation'

describe('employee import presentation', () => {
  it('labels employment without implying a lending decision', () => {
    expect(employeeStatusLabel('ACTIVE')).toBe('Active')
    expect(employeeStatusLabel('TERMINATED')).toBe('Terminated')
    expect(employeeStatusLabel('FUTURE_STATUS')).toBe('Employment status unavailable')
  })
  it('explains row problems without displaying free-form server messages', () => {
    expect(importRejectionLabel('DUPLICATE_EMPLOYEE_CODE')).toMatch(/appears more than once/)
    expect(importRejectionLabel('PRIVATE_ERROR')).toMatch(/Review this row/)
  })
})
