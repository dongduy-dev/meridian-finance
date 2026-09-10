import type { LoanAccount } from '../api/contracts'

export const serviceableAccountStatuses = new Set(['ACTIVE', 'OVERDUE'])
export const knownAccountStatuses = new Set(['ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED'])
export const knownInstallmentStatuses = new Set(['NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'])
export const knownAllocationComponents = new Set(['FEE', 'INTEREST', 'PRINCIPAL'])

export function accountStatusLabel(value: string): string {
  return knownAccountStatuses.has(value)
    ? value.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase())
    : 'Status unavailable'
}

export function hasCoherentLoanAccount(value: LoanAccount): boolean {
  const servicing = value.servicing
  return knownAccountStatuses.has(value.status)
    && value.finalRepaymentSchedule.scheduleType === 'FINAL'
    && value.finalRepaymentSchedule.items.length === value.approvedTermMonths
    && servicing.totalPaid === servicing.principalPaid + servicing.interestPaid + servicing.feePaid
    && servicing.totalOutstanding
      === servicing.principalOutstanding + servicing.interestOutstanding + servicing.feeOutstanding
    && servicing.totalPaid + servicing.totalOutstanding === value.totalRepayment
    && value.finalRepaymentSchedule.items.every((item) =>
      knownInstallmentStatuses.has(item.servicing.status)
      && item.servicing.totalPaid
        === item.servicing.principalPaid + item.servicing.interestPaid + item.servicing.feePaid
      && item.servicing.totalOutstanding
        === item.servicing.principalOutstanding
          + item.servicing.interestOutstanding
          + item.servicing.feeOutstanding)
}
