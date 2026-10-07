const employmentLabels = new Map([
  ['ACTIVE', 'Active'], ['INACTIVE', 'Inactive'],
  ['TERMINATED', 'Terminated'], ['SUSPENDED', 'Suspended'],
])

export function employeeStatusLabel(value: string): string {
  return employmentLabels.get(value) ?? 'Employment status unavailable'
}

const rejectionLabels = new Map([
  ['ROW_REQUIRED', 'Employee details are required.'],
  ['INVALID_EMPLOYEE_CODE', 'Employee code is required and must not exceed 50 characters.'],
  ['DUPLICATE_EMPLOYEE_CODE', 'Employee code appears more than once in this import.'],
  ['INVALID_IDENTITY_REFERENCE', 'Identity reference is required and must not exceed 100 characters.'],
  ['INVALID_SALARY_AMOUNT', 'Salary must be a nonnegative amount.'],
  ['INVALID_SALARY_ADVANCE_LIMIT', 'Salary Advance limit must be a nonnegative amount.'],
  ['INVALID_EMPLOYMENT_STATUS', 'Employment status is not supported.'],
  ['INVALID_ACTIVE_FLAG', 'Specify whether the employee record is active.'],
])

export function importRejectionLabel(code: string): string {
  return rejectionLabels.get(code) ?? 'Employee details were not accepted. Review this row before importing the complete roster again.'
}
