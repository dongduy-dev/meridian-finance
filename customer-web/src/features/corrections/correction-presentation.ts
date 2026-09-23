import { CheckCircle2, CircleDashed, FileUp, RefreshCcw, ShieldAlert } from 'lucide-react'

import { unavailableStatus, type StatusPresentation } from '@/components/common/status-presentation'
import { ApiError } from '@/lib/api'

export interface CorrectionScopePresentation {
  label: string
  description: string
  status: StatusPresentation
  documentAction?: 'upload' | 'replace'
  customerCompletable: boolean
}

const scopes: Record<string, CorrectionScopePresentation> = {
  SUPPORTING_DOCUMENT_UPLOAD: {
    label: 'Supporting document required',
    description: 'Upload the requested supporting document, then mark this change as complete.',
    status: { label: 'Upload required', tone: 'warning', icon: FileUp },
    documentAction: 'upload',
    customerCompletable: true,
  },
  DOCUMENT_REPLACEMENT: {
    label: 'Document replacement required',
    description: 'Upload a replacement document, then mark this change as complete.',
    status: { label: 'Replacement required', tone: 'warning', icon: RefreshCcw },
    documentAction: 'replace',
    customerCompletable: true,
  },
  DOCUMENT_REVIEW: {
    label: 'Document review in progress',
    description: 'This review is completed by Meridian. There is no action for you right now.',
    status: { label: 'Action unavailable', tone: 'neutral', icon: ShieldAlert },
    customerCompletable: false,
  },
}

const reasons: Record<string, string> = {
  SUPPORTING_DOCUMENT_REQUIRED: 'Supporting document required',
  RECENT_PAYSLIP_REQUIRED: 'Recent payslip required',
  DOCUMENT_REPLACEMENT_REQUIRED: 'Document replacement required',
  DOCUMENT_REVIEW_REQUIRED: 'Document review required',
}

const taskStatuses: Record<string, StatusPresentation> = {
  OPEN: { label: 'Open', tone: 'warning', icon: CircleDashed },
  COMPLETED: { label: 'Completed', tone: 'success', icon: CheckCircle2 },
}

const correctionMessages: Record<string, string> = {
  CORRECTION_TASK_PROOF_MISSING: 'The required document is not ready for this change. Review its current status before trying again.',
  CORRECTION_TASKS_INCOMPLETE: 'Complete every requested change before submitting your updates.',
  CORRECTION_RESUBMISSION_DENIED: 'Submitting updates is not available for this application right now.',
  CORRECTION_ALREADY_RESUBMITTED: 'These changes were already submitted. The application status has been refreshed.',
  CORRECTION_REQUEST_CONFLICT: 'The requested changes have changed. Review the latest information before continuing.',
  CORRECTION_TASK_ALREADY_COMPLETED: 'This change was already completed. Its status has been refreshed.',
  IDEMPOTENCY_KEY_REUSED: 'This action could not be safely repeated. Review the latest status before trying again.',
  LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED: 'Cancellation is no longer available for this application.',
  LOAN_APPLICATION_NOT_FOUND: 'This application is unavailable.',
  SYSTEM_STATE_CONFLICT: 'The application changed before this action was completed. Review the latest status and try again if needed.',
}

export function correctionScopePresentation(value: string): CorrectionScopePresentation {
  return scopes[value] ?? {
    label: 'Task type unavailable',
    description: "We can't show or complete this type of task right now.",
    status: unavailableStatus,
    customerCompletable: false,
  }
}

export function correctionReasonLabel(value: string) {
  return reasons[value] ?? 'Reason unavailable'
}

export function correctionTaskStatusPresentation(value: string) {
  return taskStatuses[value] ?? unavailableStatus
}

export function correctionErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback
  return correctionMessages[error.errorCode] ?? error.message
}
