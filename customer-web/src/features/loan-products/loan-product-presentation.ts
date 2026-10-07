import { CircleAlert, CircleCheck, Clock3 } from 'lucide-react'

import type { StatusPresentation } from '@/components/common/status-presentation'

export const productSlugToCode = {
  'salary-advance': 'SALARY_ADVANCE',
  'unsecured-consumer-loan': 'UNSECURED_CONSUMER_LOAN',
  'collateral-loan': 'COLLATERAL_LOAN',
} as const

const productCodeToSlug: Record<string, keyof typeof productSlugToCode> = Object.fromEntries(
  Object.entries(productSlugToCode).map(([slug, code]) => [code, slug]),
) as Record<string, keyof typeof productSlugToCode>

const productNames: Record<string, string> = {
  SALARY_ADVANCE: 'Salary Advance',
  UNSECURED_CONSUMER_LOAN: 'Unsecured Consumer Loan',
  COLLATERAL_LOAN: 'Collateral Loan',
}

const interestMethodLabels: Record<string, string> = {
  FLAT_ORIGINAL_PRINCIPAL: 'Flat interest on the amount borrowed',
}

const repaymentMethodLabels: Record<string, string> = {
  ON_SALARY_DATE: 'On salary date',
  MONTHLY_INSTALLMENT: 'Monthly installments',
}

const documentTypeLabels: Record<string, string> = {
  BANK_STATEMENT: 'Bank statement',
  EMPLOYMENT_PROOF: 'Employment proof',
  INCOME_PROOF: 'Income proof',
  COLLATERAL_OWNERSHIP_EVIDENCE: 'Proof of collateral ownership',
  RECENT_PAYSLIP: 'Recent payslip',
}

const requirementPresentations: Record<string, StatusPresentation> = {
  REQUIRED: { label: 'Required', tone: 'warning', icon: CircleAlert },
  OPTIONAL: { label: 'Optional', tone: 'information', icon: CircleCheck },
}

// Translate only verified API notes; the returned policy remains authoritative.
const eligibilityNotes: Record<string, string> = {
  'A complete Customer profile and eligible primary bank account are required.': 'Complete your profile and choose an eligible primary bank account before applying.',
  'Verified current Partner employment and available Salary Advance limit are required.': 'Your employment with a participating employer must be verified and up to date, and you need an available Salary Advance limit.',
  'A blocking application or positive outstanding Salary Advance debt prevents submission.': 'You cannot apply while another Salary Advance application is in progress or a previous Salary Advance has an unpaid balance.',
  'A blocking application or positive outstanding Unsecured Consumer Loan debt prevents submission.': 'You cannot apply while another Unsecured Consumer Loan application is in progress or a previous Unsecured Consumer Loan has an unpaid balance.',
  'Income and employment evidence is assessed through manual verification.': 'Meridian reviews your income and employment documents.',
  'One submitted collateral asset and ownership evidence are assessed manually.': 'Meridian reviews the details and proof of ownership for the one asset you submit as collateral.',
  'Estimated collateral value does not produce an automated loan-to-value decision.': 'Your estimated collateral value does not automatically determine how much you can borrow.',
}

export function eligibilityNoteLabel(note: string) {
  return Object.hasOwn(eligibilityNotes, note) ? eligibilityNotes[note]! : note
}

export function productSlug(productCode: string) {
  return productCodeToSlug[productCode]
}

export function productNameForCode(productCode: string) {
  return productNames[productCode] ?? 'Product unavailable'
}

export function interestMethodLabel(value: string) {
  return interestMethodLabels[value] ?? 'Status unavailable'
}

export function repaymentMethodLabel(value: string) {
  return repaymentMethodLabels[value] ?? 'Status unavailable'
}

export function documentTypeLabel(value: string) {
  return documentTypeLabels[value] ?? 'Status unavailable'
}

export function evidenceRequirementPresentation(value: string): StatusPresentation {
  return requirementPresentations[value] ?? {
    label: 'Status unavailable',
    tone: 'neutral',
    icon: Clock3,
  }
}
