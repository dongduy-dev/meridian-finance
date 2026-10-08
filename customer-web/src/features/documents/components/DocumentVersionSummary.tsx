import { FileText } from 'lucide-react'

import { formatTimestamp } from '@/lib/format/presentation'

import type { DocumentVersion } from '../document-api'
import { formatFileSize } from '../document-presentation'

export function DocumentVersionSummary({ version }: { version: DocumentVersion }) {
  return (
    <div className="min-w-0 space-y-4 border-y border-border py-6">
      <h4 className="flex items-start gap-2 text-base leading-6 font-semibold">
        <FileText aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        Uploaded file · Version {version.versionNumber}
      </h4>
      <dl className="grid min-w-0 gap-4">
        <VersionFact label="Filename" value={version.originalFilename} />
        <VersionFact label="File details" value={`${version.mimeType} · ${formatFileSize(version.byteSize)}`} />
        <VersionFact label="Uploaded" value={formatTimestamp(version.uploadedAt)} />
      </dl>
    </div>
  )
}

function VersionFact({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">{label}</dt><dd className="mt-2 text-base leading-6 [overflow-wrap:anywhere]">{value}</dd></div>
}
