import {
  AlertCircle,
  Ban,
  CheckCircle2,
  CircleAlert,
  CircleHelp,
  Clock3,
  RefreshCw,
  ShieldAlert,
} from 'lucide-react'

import type { StatusPresentation, StatusTone } from '@/components/common/status-presentation'

export interface BlockerPresentation {
  title: string
  description: string
  tone: StatusTone
  action?: { label: string; to: string }
}

const blockerPresentations: Record<string, BlockerPresentation> = {
  CUSTOMER_NOT_ACTIVE: {
    title: 'Customer account is not active',
    description: 'Salary Advance is unavailable while the Customer account is inactive. Contact Meridian support if this is unexpected.',
    tone: 'danger',
  },
  PROFILE_INCOMPLETE: {
    title: 'Complete your profile',
    description: 'Meridian requires a complete Customer profile before a Salary Advance application can be submitted.',
    tone: 'warning',
    action: { label: 'Complete profile', to: '/account/profile' },
  },
  PRIMARY_BANK_ACCOUNT_REQUIRED: {
    title: 'Add a primary bank account',
    description: 'An active primary bank account is required before submitting a Salary Advance application.',
    tone: 'warning',
    action: { label: 'Manage bank accounts', to: '/account/bank-accounts' },
  },
  PRODUCT_NOT_AVAILABLE: {
    title: 'Salary Advance is not available',
    description: 'The Salary Advance product is not currently available for new applications.',
    tone: 'neutral',
  },
  EMPLOYEE_NOT_VERIFIED: {
    title: 'Verify your employment',
    description: 'Complete the secure Partner employment check below. Meridian will refresh readiness after the attempt.',
    tone: 'warning',
  },
  SALARY_ADVANCE_ELIGIBILITY_DATA_STALE: {
    title: 'Refresh your employment verification',
    description: 'Current Partner evidence is required. Re-verify below against the latest available Partner information.',
    tone: 'warning',
  },
  SALARY_ADVANCE_LIMIT_UNAVAILABLE: {
    title: 'Salary Advance limit is unavailable',
    description: 'Meridian cannot currently confirm a usable Salary Advance limit.',
    tone: 'warning',
  },
  INSUFFICIENT_AVAILABLE_LIMIT: {
    title: 'Available limit is insufficient',
    description: 'The currently returned available amount is below the minimum needed for a new Salary Advance application.',
    tone: 'warning',
  },
  BLOCKING_APPLICATION_EXISTS: {
    title: 'Another Salary Advance application is active',
    description: 'A new application cannot be started while Meridian reports another blocking Salary Advance application.',
    tone: 'information',
  },
  OUTSTANDING_LOAN_ACCOUNT_EXISTS: {
    title: 'A Salary Advance balance remains outstanding',
    description: 'A prior Salary Advance must be fully repaid before another application can be submitted.',
    tone: 'information',
  },
  SYSTEM_STATE_CONFLICT: {
    title: 'Readiness is temporarily unavailable',
    description: 'Meridian found inconsistent lending evidence. Refresh the page, and contact support if the issue continues.',
    tone: 'danger',
  },
}

const employeeStatusPresentations: Record<string, StatusPresentation> = {
  VERIFIED: { label: 'Verification on file', tone: 'success', icon: CheckCircle2 },
  NOT_VERIFIED: { label: 'Verification required', tone: 'warning', icon: CircleAlert },
}

const partnerStatusPresentations: Record<string, StatusPresentation> = {
  ELIGIBLE: { label: 'Partner evidence eligible', tone: 'success', icon: CheckCircle2 },
  NOT_VERIFIED: { label: 'Partner evidence not verified', tone: 'warning', icon: CircleAlert },
  PARTNER_INACTIVE: { label: 'Partner is inactive', tone: 'danger', icon: Ban },
  EMPLOYEE_INACTIVE: { label: 'Employment is inactive', tone: 'danger', icon: Ban },
  EVIDENCE_STALE: { label: 'Partner evidence needs refresh', tone: 'warning', icon: RefreshCw },
}

const limitStatusPresentations: Record<string, StatusPresentation> = {
  ACTIVE: { label: 'Active limit', tone: 'success', icon: CheckCircle2 },
  SUSPENDED: { label: 'Limit suspended', tone: 'warning', icon: ShieldAlert },
  DISABLED: { label: 'Limit disabled', tone: 'danger', icon: Ban },
  STALE: { label: 'Limit needs refresh', tone: 'warning', icon: RefreshCw },
  NOT_INITIALIZED: { label: 'Advisory current limit', tone: 'information', icon: Clock3 },
  UNAVAILABLE: { label: 'Limit unavailable', tone: 'neutral', icon: CircleHelp },
}

export interface VerificationOutcomePresentation extends StatusPresentation {
  description: string
}

const verificationOutcomePresentations: Record<string, VerificationOutcomePresentation> = {
  MATCHED_ACTIVE: {
    label: 'Employment match recorded',
    tone: 'success',
    icon: CheckCircle2,
    description: 'Meridian refreshed Salary Advance readiness. The refreshed readiness result remains authoritative for applying.',
  },
  MATCHED_INACTIVE: {
    label: 'Employment is not active',
    tone: 'warning',
    icon: CircleAlert,
    description: 'The verification did not establish active employment for Salary Advance eligibility.',
  },
  NOT_FOUND: {
    label: 'Employment could not be verified',
    tone: 'warning',
    icon: CircleAlert,
    description: 'Meridian could not verify employment from the submitted Partner and employee code.',
  },
  MULTIPLE_MATCHES: {
    label: 'Employment needs review',
    tone: 'warning',
    icon: Clock3,
    description: 'The verification could not establish one eligible employment record.',
  },
  PENDING_MANUAL_REVIEW: {
    label: 'Manual review required',
    tone: 'information',
    icon: Clock3,
    description: 'Meridian has recorded the attempt for manual review. Readiness will remain blocked unless a later authoritative result allows application.',
  },
  MANUAL_REVIEW_APPROVED: {
    label: 'Manual review approved',
    tone: 'success',
    icon: CheckCircle2,
    description: 'Meridian refreshed Salary Advance readiness. The refreshed readiness result remains authoritative for applying.',
  },
  MANUAL_REVIEW_REJECTED: {
    label: 'Manual review did not verify employment',
    tone: 'danger',
    icon: AlertCircle,
    description: 'The manual review did not establish eligible employment for Salary Advance.',
  },
}

export function blockerPresentation(code: string): BlockerPresentation {
  return blockerPresentations[code] ?? {
    title: 'Readiness unavailable',
    description: 'Meridian returned a readiness condition that Customer Web cannot safely interpret yet.',
    tone: 'neutral',
  }
}

export function employeeStatusPresentation(value: string): StatusPresentation {
  return employeeStatusPresentations[value] ?? {
    label: 'Verification status unavailable',
    tone: 'neutral',
    icon: CircleHelp,
  }
}

export function partnerStatusPresentation(value: string): StatusPresentation {
  return partnerStatusPresentations[value] ?? {
    label: 'Partner status unavailable',
    tone: 'neutral',
    icon: CircleHelp,
  }
}

export function limitStatusPresentation(value: string): StatusPresentation {
  return limitStatusPresentations[value] ?? {
    label: 'Limit status unavailable',
    tone: 'neutral',
    icon: CircleHelp,
  }
}

export function verificationOutcomePresentation(
  outcome: string,
  manualReviewRequired: boolean,
): VerificationOutcomePresentation {
  if (manualReviewRequired) {
    return verificationOutcomePresentations.PENDING_MANUAL_REVIEW!
  }
  return verificationOutcomePresentations[outcome] ?? {
    label: 'Verification result unavailable',
    tone: 'neutral',
    icon: CircleHelp,
    description: 'Meridian returned a verification result that Customer Web cannot safely interpret yet. Use the refreshed readiness result for current application availability.',
  }
}
