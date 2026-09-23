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
    title: 'Your account is not active',
    description: 'Salary Advance is unavailable while your account is inactive. Contact Meridian support if this is unexpected.',
    tone: 'danger',
  },
  PROFILE_INCOMPLETE: {
    title: 'Complete your profile',
    description: 'Complete your profile before submitting a Salary Advance application.',
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
    description: 'Select your employer and enter your employee code below.',
    tone: 'warning',
  },
  SALARY_ADVANCE_ELIGIBILITY_DATA_STALE: {
    title: 'Refresh employment verification',
    description: 'Your employment verification needs to be refreshed before submission.',
    tone: 'warning',
  },
  SALARY_ADVANCE_LIMIT_UNAVAILABLE: {
    title: 'Salary Advance limit is unavailable',
    description: 'Meridian cannot currently confirm a usable Salary Advance limit.',
    tone: 'warning',
  },
  INSUFFICIENT_AVAILABLE_LIMIT: {
    title: 'Available limit is insufficient',
    description: 'Your available amount is below the minimum for a new Salary Advance application.',
    tone: 'warning',
  },
  BLOCKING_APPLICATION_EXISTS: {
    title: 'Salary Advance application in progress',
    description: 'You already have a Salary Advance application in progress. You can start another after it is no longer active.',
    tone: 'information',
  },
  OUTSTANDING_LOAN_ACCOUNT_EXISTS: {
    title: 'A Salary Advance balance remains outstanding',
    description: 'A prior Salary Advance must be fully repaid before another application can be submitted.',
    tone: 'information',
  },
  SYSTEM_STATE_CONFLICT: {
    title: 'Salary Advance information is unavailable',
    description: "We couldn't confirm the latest Salary Advance information. Review the latest status and try again if appropriate.",
    tone: 'danger',
  },
}

const employeeStatusPresentations: Record<string, StatusPresentation> = {
  VERIFIED: { label: 'Employment verified', tone: 'success', icon: CheckCircle2 },
  NOT_VERIFIED: { label: 'Employment verification required', tone: 'warning', icon: CircleAlert },
}

const partnerStatusPresentations: Record<string, StatusPresentation> = {
  ELIGIBLE: { label: 'Employment verified', tone: 'success', icon: CheckCircle2 },
  NOT_VERIFIED: { label: 'Employment not verified', tone: 'warning', icon: CircleAlert },
  PARTNER_INACTIVE: { label: 'Employer is inactive', tone: 'danger', icon: Ban },
  EMPLOYEE_INACTIVE: { label: 'Employment is inactive', tone: 'danger', icon: Ban },
  EVIDENCE_STALE: { label: 'Employment information needs updating', tone: 'warning', icon: RefreshCw },
}

const limitStatusPresentations: Record<string, StatusPresentation> = {
  ACTIVE: { label: 'Active limit', tone: 'success', icon: CheckCircle2 },
  SUSPENDED: { label: 'Limit suspended', tone: 'warning', icon: ShieldAlert },
  DISABLED: { label: 'Limit disabled', tone: 'danger', icon: Ban },
  STALE: { label: 'Limit needs refresh', tone: 'warning', icon: RefreshCw },
  NOT_INITIALIZED: { label: 'Current estimate', tone: 'information', icon: Clock3 },
  UNAVAILABLE: { label: 'Limit unavailable', tone: 'neutral', icon: CircleHelp },
}

export interface VerificationOutcomePresentation extends StatusPresentation {
  description: string
}

const verificationOutcomePresentations: Record<string, VerificationOutcomePresentation> = {
  MATCHED_ACTIVE: {
    label: 'Employment verified',
    tone: 'success',
    icon: CheckCircle2,
    description: 'Your employment was verified. We updated your application availability.',
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
    description: 'We could not verify employment using the selected employer and employee code.',
  },
  MULTIPLE_MATCHES: {
    label: 'Employment needs review',
    tone: 'warning',
    icon: Clock3,
    description: 'The verification could not establish one eligible employment record.',
  },
  PENDING_MANUAL_REVIEW: {
    label: "We're reviewing your employment details",
    tone: 'information',
    icon: Clock3,
    description: "We'll update your application availability when the review is complete.",
  },
  MANUAL_REVIEW_APPROVED: {
    label: 'Employment verified',
    tone: 'success',
    icon: CheckCircle2,
    description: 'Your employment was verified. We updated your application availability.',
  },
  MANUAL_REVIEW_REJECTED: {
    label: 'Employment could not be verified',
    tone: 'danger',
    icon: AlertCircle,
    description: 'We could not verify eligible employment for Salary Advance.',
  },
}

export function blockerPresentation(code: string): BlockerPresentation {
  return blockerPresentations[code] ?? {
    title: 'Application status unavailable',
    description: "We can't show whether you can apply right now. Refresh the page or try again later.",
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
    label: 'Employment status unavailable',
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
    description: "We can't show the verification result right now. Check your application availability before continuing.",
  }
}
