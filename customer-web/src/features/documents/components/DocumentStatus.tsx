import { StatusBadge } from '@/components/common/StatusBadge'

import { documentStatusDescription, documentStatusPresentation } from '../document-presentation'

export function DocumentStatus({ status, staffAssisted = false }: { status: string; staffAssisted?: boolean }) {
  const presentation = documentStatusPresentation(status)
  const staffLabel = status === 'NOT_UPLOADED' ? 'Document required'
    : status === 'REPLACEMENT_REQUESTED' ? 'Replacement required' : presentation.label
  const description = staffAssisted && status === 'NOT_UPLOADED'
    ? 'This document is still required. Meridian Staff will coordinate the upload with you.'
    : staffAssisted && status === 'REPLACEMENT_REQUESTED'
      ? 'A replacement is required. Meridian Staff will coordinate it with you.'
      : documentStatusDescription(status)
  return (
    <div className="space-y-2">
      <StatusBadge presentation={staffAssisted ? { ...presentation, label: staffLabel } : presentation} />
      <p className="text-sm leading-6 text-muted-foreground">{description}</p>
    </div>
  )
}
