import { useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatTimestamp } from '@/lib/format/presentation'
import type { AssistedActionEvidence } from '../api/contracts'
import { SignedEvidenceViewer } from './SignedEvidenceViewer'

const labels: Record<string, string> = {
  CUSTOMER_OFFER_RESPONSE: 'Customer offer response',
  CUSTOMER_CONTRACT_ACKNOWLEDGMENT: 'Customer contract acknowledgment',
  CUSTOMER_CANCELLATION_REQUEST: 'Customer cancellation request',
}

export function SignedCustomerActionEvidence({ loanApplicationId, evidence }: { loanApplicationId: string; evidence: AssistedActionEvidence[] }) {
  return <Card><CardHeader><CardTitle>Signed Customer action evidence</CardTitle><p className="text-sm text-muted-foreground">Read-only signed forms, separate from the underwriting checklist.</p></CardHeader>
    <CardContent className="space-y-4">{evidence.length === 0 ? <p className="text-sm text-muted-foreground">No signed Customer action evidence exists.</p> : evidence.map((item) =>
      <EvidenceItem key={item.documentId} loanApplicationId={loanApplicationId} item={item} />)}</CardContent>
  </Card>
}

function EvidenceItem({ loanApplicationId, item }: { loanApplicationId: string; item: AssistedActionEvidence }) {
  const [selectedId, setSelectedId] = useState<string>()
  const selected = item.versionHistory.find((version) => version.documentVersionId === selectedId) ?? item.currentVersion
  if (!selected) return null
  return <section className="space-y-3 rounded-md border p-4">
    <h3 className="font-semibold">{labels[item.evidenceType] ?? 'Evidence type unavailable'}</h3>
    {item.declaredOfferDecision ? <p className="text-sm">Customer decision: {item.declaredOfferDecision === 'ACCEPT' ? 'Accepted' : item.declaredOfferDecision === 'DECLINE' ? 'Declined' : 'Decision unavailable'}</p> : null}
    {item.contractVersion ? <p className="text-sm">Contract version {item.contractVersion}</p> : null}
    <label className="grid gap-2 text-sm">Signed evidence version<select className="min-h-11 max-w-full rounded-md border bg-background px-3" value={selected.documentVersionId} onChange={(event) => setSelectedId(event.target.value)}>
      {item.versionHistory.map((version) => <option key={version.documentVersionId} value={version.documentVersionId}>Version {version.versionNumber}{version.documentVersionId === item.currentVersion?.documentVersionId ? ' · Current' : ' · Historical'} · {version.originalFilename}</option>)}
    </select></label>
    <p className="break-words text-sm">{selected.originalFilename} · {selected.detectedMimeType} · {selected.byteSize.toLocaleString()} bytes · Uploaded {formatTimestamp(selected.uploadedAt)}</p>
    {selected.documentVersionId !== item.currentVersion?.documentVersionId ? <p className="text-sm text-muted-foreground">Historical signed evidence selected</p> : null}
    <details className="text-xs text-muted-foreground"><summary>Technical references</summary><p className="break-all">Target: {item.approvedOfferId ?? item.loanContractId ?? item.correctionRequestId}</p><p className="break-all">Version: {selected.documentVersionId}</p></details>
    {labels[item.evidenceType] ? <SignedEvidenceViewer loanApplicationId={loanApplicationId} evidenceType={item.evidenceType} documentVersionId={selected.documentVersionId} /> : null}
  </section>
}
