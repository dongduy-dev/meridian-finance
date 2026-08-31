import { CheckCircle2, CircleDashed, Clock3, FileUp, RefreshCcw } from 'lucide-react'

import type { StatusPresentation } from '@/components/common/status-presentation'

const documentStatuses: Record<string, StatusPresentation> = {
  NOT_UPLOADED: { label: 'Upload required', tone: 'warning', icon: FileUp },
  AWAITING_REVIEW: { label: 'Awaiting review', tone: 'information', icon: Clock3 },
  ACCEPTED: { label: 'Accepted', tone: 'success', icon: CheckCircle2 },
  REPLACEMENT_REQUESTED: { label: 'Replacement requested', tone: 'warning', icon: RefreshCcw },
  WAIVED: { label: 'Requirement waived', tone: 'neutral', icon: CheckCircle2 },
}

const descriptions: Record<string, string> = {
  NOT_UPLOADED: 'This document still needs an upload.',
  AWAITING_REVIEW: 'An upload exists and is awaiting review. It is not missing.',
  ACCEPTED: 'The current document has been accepted.',
  REPLACEMENT_REQUESTED: 'A reviewer requested a replacement for the current document.',
  WAIVED: 'This requirement was waived. No upload is needed.',
}

export function documentStatusPresentation(value: string): StatusPresentation {
  return documentStatuses[value] ?? {
    label: 'Status unavailable', tone: 'neutral', icon: CircleDashed,
  }
}

export function documentStatusDescription(value: string) {
  return descriptions[value] ?? 'The current document status cannot be described safely.'
}

export function documentUploadAction(value: string): 'upload' | 'replace' | undefined {
  if (value === 'NOT_UPLOADED') return 'upload'
  if (value === 'REPLACEMENT_REQUESTED') return 'replace'
  return undefined
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`
}
