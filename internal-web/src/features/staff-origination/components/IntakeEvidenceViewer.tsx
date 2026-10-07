import { useState } from 'react'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { DocumentContentViewer } from '@/features/staff-documents/components/DocumentContentViewer'
import { formatTimestamp } from '@/lib/format/presentation'
import type { IntakeEvidence } from '../api/contracts'

export function IntakeEvidenceViewer({ manager, caseId, evidence, label }: {
  manager: AuthSessionManager; caseId: string; evidence: IntakeEvidence; label: string
}) {
  const [selectedId, setSelectedId] = useState(evidence.currentVersionId)
  const selected = evidence.versions.find(version => version.intakeDocumentVersionId === selectedId)
  if (evidence.assistedOriginationCaseId !== caseId || !selected) {
    return <p role="alert">Evidence version metadata is unavailable. Refresh the evidence before viewing.</p>
  }
  const historical = selected.intakeDocumentVersionId !== evidence.currentVersionId
  return <div className="space-y-3">
    {evidence.versions.length > 1 ? <label className="grid gap-1 text-sm font-medium">{label} version
      <select className="min-h-11 w-full rounded-md border bg-background px-3 sm:max-w-md" value={selectedId ?? ''} onChange={event => setSelectedId(event.target.value)}>
        {evidence.versions.map(version => <option key={version.intakeDocumentVersionId} value={version.intakeDocumentVersionId}>
          Version {version.versionNumber} · {version.intakeDocumentVersionId === evidence.currentVersionId ? 'Current' : 'Historical'}
        </option>)}
      </select>
    </label> : null}
    <p className="text-sm font-semibold">Version {selected.versionNumber} · {historical ? 'Historical' : 'Current'}</p>
    <p className="break-words text-sm text-muted-foreground">{selected.originalFilename} · {selected.detectedMimeType} · {selected.byteSize.toLocaleString()} bytes · Uploaded {formatTimestamp(selected.uploadedAt)}</p>
    {historical ? <p className="rounded-md border border-warning/40 bg-warning/10 p-3 text-sm">Historical evidence is read-only. OCR and replacement continue to use the current version.</p> : null}
    <DocumentContentViewer manager={manager} intakeCaseId={caseId} intakeEvidenceType={evidence.evidenceType}
      documentVersionId={selected.intakeDocumentVersionId} filename={selected.originalFilename} />
  </div>
}
