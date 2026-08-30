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
  FLAT_ORIGINAL_PRINCIPAL: 'Flat interest on original principal',
}

const repaymentMethodLabels: Record<string, string> = {
  ON_SALARY_DATE: 'On salary date',
  MONTHLY_INSTALLMENT: 'Monthly installment',
}

const documentTypeLabels: Record<string, string> = {
  BANK_STATEMENT: 'Bank statement',
  EMPLOYMENT_PROOF: 'Employment proof',
  INCOME_PROOF: 'Income proof',
  COLLATERAL_OWNERSHIP_EVIDENCE: 'Collateral ownership evidence',
  RECENT_PAYSLIP: 'Recent payslip',
}

const requirementPresentations: Record<string, StatusPresentation> = {
  REQUIRED: { label: 'Required', tone: 'warning', icon: CircleAlert },
  OPTIONAL: { label: 'Optional', tone: 'information', icon: CircleCheck },
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
