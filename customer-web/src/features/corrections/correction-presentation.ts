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
    description: 'Upload the requested supporting evidence, then explicitly complete this task.',
    status: { label: 'Upload required', tone: 'warning', icon: FileUp },
    documentAction: 'upload',
    customerCompletable: true,
  },
  DOCUMENT_REPLACEMENT: {
    label: 'Document replacement required',
    description: 'Replace the current document version, then explicitly complete this task.',
    status: { label: 'Replacement required', tone: 'warning', icon: RefreshCcw },
    documentAction: 'replace',
    customerCompletable: true,
  },
  DOCUMENT_REVIEW: {
    label: 'Document review unavailable',
    description: 'This task type is not Customer-executable. Customer Web will not expose a Staff review action.',
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
  CORRECTION_TASK_PROOF_MISSING: 'Meridian has not yet accepted the required evidence as proof for this task. Review the current document state before trying again.',
  CORRECTION_TASKS_INCOMPLETE: 'Every required Customer task must be completed before resubmission.',
  CORRECTION_RESUBMISSION_DENIED: 'Customer resubmission is not available for the current correction state.',
  CORRECTION_ALREADY_RESUBMITTED: 'This correction was already resubmitted. Meridian refreshed the authoritative application state.',
  CORRECTION_REQUEST_CONFLICT: 'The correction request changed and is no longer actionable in its previous state.',
  CORRECTION_TASK_ALREADY_COMPLETED: 'This task was already completed. Meridian refreshed the task state.',
  IDEMPOTENCY_KEY_REUSED: 'This operation identity no longer matches the requested action. Review the refreshed state before trying again.',
  LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED: 'Cancellation is no longer available for this application state.',
  LOAN_APPLICATION_NOT_FOUND: 'This application is unavailable.',
  SYSTEM_STATE_CONFLICT: 'The current application state could not be reconciled safely.',
}

export function correctionScopePresentation(value: string): CorrectionScopePresentation {
  return scopes[value] ?? {
    label: 'Task type unavailable',
    description: 'Customer Web cannot describe or execute this task type safely.',
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
