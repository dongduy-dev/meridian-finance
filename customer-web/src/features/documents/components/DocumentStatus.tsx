import { StatusBadge } from '@/components/common/StatusBadge'

import { documentStatusDescription, documentStatusPresentation } from '../document-presentation'

export function DocumentStatus({ status }: { status: string }) {
  return (
    <div className="space-y-2">
      <StatusBadge presentation={documentStatusPresentation(status)} />
      <p className="text-sm leading-6 text-muted-foreground">{documentStatusDescription(status)}</p>
    </div>
  )
}
