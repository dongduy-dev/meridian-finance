import {
  STAFF_OPERATIONAL_PERMISSIONS,
  hasAnyPermission,
  type StaffActor,
  type StaffOperationalPermission,
} from '@/features/auth/model/access-control'

export type StaffRouteDefinition = {
  path: `/staff${string}`
  label: string
  documentTitle: string
  requiredPermissions: readonly StaffOperationalPermission[]
  requiredRoles?: readonly string[]
}

export const STAFF_HOME_ROUTE = {
  path: '/staff',
  label: 'Internal operations',
  documentTitle: 'Internal operations',
  requiredPermissions: STAFF_OPERATIONAL_PERMISSIONS,
} as const satisfies StaffRouteDefinition

export const STAFF_APPLICATIONS_ROUTE = {
  path: '/staff/applications',
  label: 'Applications',
  documentTitle: 'Applications',
  requiredPermissions: ['loan:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_ORIGINATION_ROUTE = {
  path: '/staff/origination',
  label: 'Assisted origination',
  documentTitle: 'Assisted origination',
  requiredPermissions: ['loan:originate:staff'],
} as const satisfies StaffRouteDefinition

export const STAFF_CUSTOMER_ACCESS_ROUTE = {
  path: '/staff/customer-access',
  label: 'Customer digital access',
  documentTitle: 'Customer digital access',
  requiredPermissions: ['customer:intake:manage'],
} as const satisfies StaffRouteDefinition

export const STAFF_ORIGINATION_CASE_ROUTE = {
  path: '/staff/origination/:assistedOriginationCaseId',
  label: 'Assisted origination case',
  documentTitle: 'Assisted origination case',
  requiredPermissions: ['loan:originate:staff'],
} as const satisfies StaffRouteDefinition

export const STAFF_APPLICATION_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId',
  label: 'Application case',
  documentTitle: 'Application case',
  requiredPermissions: ['loan:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_OFFER_RESPONSE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/offer-response',
  label: 'Customer offer response',
  documentTitle: 'Customer offer response',
  requiredPermissions: ['loan:offer:respond:staff'],
  requiredRoles: ['LOAN_OFFICER'],
} as const satisfies StaffRouteDefinition

export const STAFF_DOCUMENT_QUEUE_ROUTE = {
  path: '/staff/work/documents',
  label: 'Document review',
  documentTitle: 'Document review',
  requiredPermissions: ['document:review'],
} as const satisfies StaffRouteDefinition

export const STAFF_CORRECTION_QUEUE_ROUTE = {
  path: '/staff/work/corrections',
  label: 'Corrections',
  documentTitle: 'Staff corrections',
  requiredPermissions: ['loan:correction:staff'],
} as const satisfies StaffRouteDefinition

export const STAFF_DOCUMENT_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/documents',
  label: 'Application documents',
  documentTitle: 'Application documents',
  requiredPermissions: ['document:review'],
} as const satisfies StaffRouteDefinition

export const STAFF_CORRECTION_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/corrections',
  label: 'Application corrections',
  documentTitle: 'Application corrections',
  requiredPermissions: ['loan:correction:staff'],
} as const satisfies StaffRouteDefinition

export const STAFF_VERIFICATION_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/verification',
  label: 'Product assessment',
  documentTitle: 'Product assessment',
  requiredPermissions: ['loan:review'],
} as const satisfies StaffRouteDefinition

export const STAFF_REVIEW_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/review',
  label: 'Loan Officer review',
  documentTitle: 'Loan Officer review',
  requiredPermissions: ['loan:review'],
} as const satisfies StaffRouteDefinition

export const STAFF_APPROVAL_QUEUE_ROUTE = {
  path: '/staff/work/approvals',
  label: 'Approval decisions',
  documentTitle: 'Approval decisions',
  requiredPermissions: ['approval:decide'],
} as const satisfies StaffRouteDefinition

export const STAFF_DECISION_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/decision',
  label: 'Independent decision',
  documentTitle: 'Independent decision',
  requiredPermissions: ['approval:decide'],
} as const satisfies StaffRouteDefinition

export const STAFF_CONTRACT_QUEUE_ROUTE = {
  path: '/staff/work/contracts',
  label: 'Contracts',
  documentTitle: 'Contract and readiness queue',
  requiredPermissions: ['loan:contract:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_CONTRACT_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/contract',
  label: 'Contract and readiness',
  documentTitle: 'Contract and readiness',
  requiredPermissions: ['loan:contract:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_DISBURSEMENT_QUEUE_ROUTE = {
  path: '/staff/work/disbursements',
  label: 'Disbursements',
  documentTitle: 'Ready-disbursement queue',
  requiredPermissions: ['loan:disburse'],
} as const satisfies StaffRouteDefinition

export const STAFF_DISBURSEMENT_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/disbursement',
  label: 'Disbursement and activation',
  documentTitle: 'Disbursement and activation',
  requiredPermissions: ['loan:disburse'],
} as const satisfies StaffRouteDefinition

export const STAFF_SERVICING_QUEUE_ROUTE = {
  path: '/staff/work/servicing',
  label: 'Account servicing',
  documentTitle: 'Loan account servicing queue',
  requiredPermissions: ['loan:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_LOAN_ACCOUNT_ROUTE = {
  path: '/staff/applications/:loanApplicationId/loan-account',
  label: 'Loan account servicing',
  documentTitle: 'Loan account servicing',
  requiredPermissions: ['loan:read'],
} as const satisfies StaffRouteDefinition

export const STAFF_REPAYMENT_ENTRY_ROUTE = {
  path: '/staff/applications/:loanApplicationId/repayments/new',
  label: 'Record repayment',
  documentTitle: 'Record repayment',
  requiredPermissions: ['repayment:update'],
} as const satisfies StaffRouteDefinition

export const STAFF_SETTLEMENT_QUEUE_ROUTE = {
  path: '/staff/work/settlements',
  label: 'Settlements',
  documentTitle: 'Settlement work queue',
  requiredPermissions: ['loan:settlement:approve'],
  requiredRoles: ['APPROVER'],
} as const satisfies StaffRouteDefinition

export const STAFF_SETTLEMENT_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/settlement',
  label: 'Administrative settlement',
  documentTitle: 'Administrative Full-Balance Settlement',
  requiredPermissions: ['loan:settlement:approve'],
  requiredRoles: ['APPROVER'],
} as const satisfies StaffRouteDefinition

export const STAFF_CLOSURE_QUEUE_ROUTE = {
  path: '/staff/work/closures',
  label: 'Closures',
  documentTitle: 'Closure work queue',
  requiredPermissions: ['loan:account:close'],
  requiredRoles: ['ACCOUNTING_OFFICER'],
} as const satisfies StaffRouteDefinition

export const STAFF_CLOSURE_CASE_ROUTE = {
  path: '/staff/applications/:loanApplicationId/closure',
  label: 'Administrative closure',
  documentTitle: 'Administrative closure',
  requiredPermissions: ['loan:account:close'],
  requiredRoles: ['ACCOUNTING_OFFICER'],
} as const satisfies StaffRouteDefinition

export const STAFF_ROUTES = [
  STAFF_HOME_ROUTE,
  STAFF_ORIGINATION_ROUTE,
  STAFF_CUSTOMER_ACCESS_ROUTE,
  STAFF_APPLICATIONS_ROUTE,
  STAFF_DOCUMENT_QUEUE_ROUTE,
  STAFF_CORRECTION_QUEUE_ROUTE,
  STAFF_APPROVAL_QUEUE_ROUTE,
  STAFF_CONTRACT_QUEUE_ROUTE,
  STAFF_DISBURSEMENT_QUEUE_ROUTE,
  STAFF_SERVICING_QUEUE_ROUTE,
  STAFF_SETTLEMENT_QUEUE_ROUTE,
  STAFF_CLOSURE_QUEUE_ROUTE,
] as const satisfies readonly StaffRouteDefinition[]

export const STAFF_EXECUTABLE_ROUTES = [
  STAFF_HOME_ROUTE,
  STAFF_APPLICATIONS_ROUTE,
  STAFF_ORIGINATION_ROUTE,
  STAFF_CUSTOMER_ACCESS_ROUTE,
  STAFF_ORIGINATION_CASE_ROUTE,
  STAFF_APPLICATION_CASE_ROUTE,
  STAFF_OFFER_RESPONSE_ROUTE,
  STAFF_DOCUMENT_QUEUE_ROUTE,
  STAFF_CORRECTION_QUEUE_ROUTE,
  STAFF_DOCUMENT_CASE_ROUTE,
  STAFF_CORRECTION_CASE_ROUTE,
  STAFF_VERIFICATION_CASE_ROUTE,
  STAFF_REVIEW_CASE_ROUTE,
  STAFF_APPROVAL_QUEUE_ROUTE,
  STAFF_DECISION_CASE_ROUTE,
  STAFF_CONTRACT_QUEUE_ROUTE,
  STAFF_CONTRACT_CASE_ROUTE,
  STAFF_DISBURSEMENT_QUEUE_ROUTE,
  STAFF_DISBURSEMENT_CASE_ROUTE,
  STAFF_SERVICING_QUEUE_ROUTE,
  STAFF_LOAN_ACCOUNT_ROUTE,
  STAFF_REPAYMENT_ENTRY_ROUTE,
  STAFF_SETTLEMENT_QUEUE_ROUTE,
  STAFF_SETTLEMENT_CASE_ROUTE,
  STAFF_CLOSURE_QUEUE_ROUTE,
  STAFF_CLOSURE_CASE_ROUTE,
] as const satisfies readonly StaffRouteDefinition[]

export function canAccessStaffRoute(actor: StaffActor, route: StaffRouteDefinition): boolean {
  return hasAnyPermission(actor, route.requiredPermissions)
    && (!route.requiredRoles || route.requiredRoles.some((role) => actor.roles.includes(role)))
}

export function permittedStaffRoutes(actor: StaffActor): readonly StaffRouteDefinition[] {
  return STAFF_ROUTES.filter((route) => canAccessStaffRoute(actor, route))
}
