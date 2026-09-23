import {
  Ban,
  CheckCircle2,
  CircleAlert,
  CircleDashed,
  Clock3,
  FileCheck2,
  FileUp,
  PenLine,
  ShieldCheck,
  ThumbsDown,
} from 'lucide-react'

import { unavailableStatus, type StatusPresentation } from '@/components/common/status-presentation'

const applicationStatuses: Record<string, StatusPresentation> = {
  DRAFT: { label: 'Draft', tone: 'neutral', icon: PenLine },
  SUBMITTED: { label: 'Submitted', tone: 'information', icon: FileCheck2 },
  VERIFICATION_PENDING: { label: 'Verification pending', tone: 'information', icon: Clock3 },
  VERIFICATION_FAILED: { label: 'Verification failed', tone: 'danger', icon: CircleAlert },
  DOCUMENTS_PENDING: { label: 'Documents pending', tone: 'warning', icon: FileUp },
  UNDER_REVIEW: { label: 'Under review', tone: 'information', icon: ShieldCheck },
  RETURNED_FOR_REVISION: { label: 'Changes requested', tone: 'warning', icon: PenLine },
  RETURNED_TO_REVIEW: { label: 'Back under review', tone: 'information', icon: Clock3 },
  APPROVAL_PENDING: { label: 'Approval pending', tone: 'information', icon: Clock3 },
  APPROVED: { label: 'Approved', tone: 'success', icon: CheckCircle2 },
  REJECTED: { label: 'Not approved', tone: 'danger', icon: ThumbsDown },
  CUSTOMER_ACCEPTANCE_PENDING: { label: 'Offer ready to review', tone: 'warning', icon: CircleAlert },
  CUSTOMER_DECLINED: { label: 'Offer declined', tone: 'neutral', icon: Ban },
  CONTRACT_PENDING: { label: 'Contract pending', tone: 'warning', icon: FileCheck2 },
  DISBURSEMENT_PENDING: { label: 'Disbursement pending', tone: 'information', icon: Clock3 },
  DISBURSED: { label: 'Disbursed', tone: 'success', icon: CheckCircle2 },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', icon: Ban },
  EXPIRED: { label: 'Expired', tone: 'neutral', icon: Clock3 },
}

export interface RequiredActionPresentation {
  title: string
  description: string
  status: StatusPresentation
}

const requiredActions: Record<string, RequiredActionPresentation> = {
  UPLOAD_DOCUMENTS: {
    title: 'Documents are required',
    description: 'Upload the required documents so your application can continue.',
    status: { label: 'Documents needed', tone: 'warning', icon: FileUp },
  },
  COMPLETE_CORRECTIONS: {
    title: 'Requested changes need attention',
    description: 'Review and complete the requested changes for this application.',
    status: { label: 'Changes needed', tone: 'warning', icon: PenLine },
  },
  REVIEW_APPROVED_OFFER: {
    title: 'An approved offer is ready',
    description: 'Review the approved terms and accept or decline the offer.',
    status: { label: 'Offer review needed', tone: 'warning', icon: CircleAlert },
  },
  ACKNOWLEDGE_CONTRACT: {
    title: 'Review your contract',
    description: 'Review the current contract version and confirm that you have read it.',
    status: { label: 'Acknowledgment needed', tone: 'warning', icon: FileCheck2 },
  },
}

export function applicationStatusPresentation(value: string) {
  return applicationStatuses[value] ?? unavailableStatus
}

export function requiredActionPresentation(value: string): RequiredActionPresentation | undefined {
  if (value === 'NONE') return undefined
  return requiredActions[value] ?? {
    title: 'Action details unavailable',
    description: "We can't show what you need to do right now. Refresh the page or try again later.",
    status: { ...unavailableStatus, icon: CircleDashed },
  }
}
