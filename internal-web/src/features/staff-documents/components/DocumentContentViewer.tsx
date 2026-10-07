import { identityContent } from '@/features/customer-identity/api'
import { getIntakeEvidenceContent } from '@/features/staff-origination/api/staff-origination-api'
import { Download, Eye, EyeOff } from 'lucide-react'
import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { ApiBinaryResponse } from '@/lib/api'
import { getAssistedActionEvidenceContent, getDocumentContent } from '../api/staff-documents-api'

type Props = {
  manager: AuthSessionManager
  loanApplicationId?: string
  documentVersionId?: string
  filename: string
  buttonLabel?: string
} & (
  | { checklistItemId: string; evidenceType?: never; identityVerificationId?: never; intakeCaseId?: never; intakeEvidenceType?: never }
  | { evidenceType: string; checklistItemId?: never; identityVerificationId?: never; intakeCaseId?: never; intakeEvidenceType?: never }
  | { identityVerificationId: string; checklistItemId?: never; evidenceType?: never; intakeCaseId?: never; intakeEvidenceType?: never }
  | { intakeCaseId: string; intakeEvidenceType: string; checklistItemId?: never; evidenceType?: never; identityVerificationId?: never }
)

function downloadFilename(disposition: string | undefined, fallback: string, contentType: string): string {
  const safe = (value: string) => value.trim().length > 0 && value.length <= 255
    && !/[\\/\p{Cc}\p{Cf}]/u.test(value) && !value.includes('..')
  const extended = disposition?.match(/(?:^|;)\s*filename\*=UTF-8''([^;]+)/i)?.[1]
  if (extended) {
    try {
      const decoded = decodeURIComponent(extended.trim())
      if (safe(decoded)) return decoded
    } catch { /* Use safe response metadata when the header is malformed. */ }
  }
  const plain = disposition?.match(/(?:^|;)\s*filename="([^"\r\n]*)"/i)?.[1]
  if (plain && safe(plain) && !plain.startsWith('=?')) return plain
  return safe(fallback) ? fallback : `document.${contentType === 'application/pdf' ? 'pdf' : contentType === 'image/png' ? 'png' : 'jpg'}`
}

export function DocumentContentViewer(props: Props) {
  const session = useSyncExternalStore(props.manager.subscribe, props.manager.getSnapshot, props.manager.getSnapshot)
  const authority = session.status === 'authenticated'
    ? `${session.actor.userId}:${[...session.actor.permissions].sort().join(',')}:${[...session.actor.roles].sort().join(',')}`
    : session.status
  if (session.status !== 'authenticated') return null
  const has = (permission: string) => session.actor.permissions.includes(permission)
  const allowed = props.intakeCaseId ? has('document:upload:intake') && has('loan:originate:staff')
    : props.identityVerificationId ? has('customer:identity:verify')
      : props.evidenceType ? has('document:review')
        || (props.evidenceType === 'CUSTOMER_OFFER_RESPONSE' && has('loan:offer:respond:staff') && session.actor.roles.includes('LOAN_OFFICER'))
        || (props.evidenceType === 'CUSTOMER_CONTRACT_ACKNOWLEDGMENT' && has('loan:contract:read') && session.actor.roles.includes('ACCOUNTING_OFFICER'))
        || (props.evidenceType === 'CUSTOMER_CANCELLATION_REQUEST' && has('loan:correction:staff') && session.actor.roles.includes('LOAN_OFFICER'))
        : has('document:review') || has('approval:decide')
  if (!allowed) return null
  return <ContentViewer key={`${authority}:${props.loanApplicationId}:${props.intakeCaseId}:${props.intakeEvidenceType ?? props.identityVerificationId ?? props.checklistItemId ?? props.evidenceType}:${props.documentVersionId}`} {...props} />
}

function ContentViewer(props: Props) {
  const [content, setContent] = useState<(ApiBinaryResponse & { url: string }) | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()
  const generation = useRef(0)
  const objectUrl = useRef<string | null>(null)

  const close = () => {
    generation.current += 1
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current)
    objectUrl.current = null
    setContent(null)
    setLoading(false)
    setError(undefined)
  }

  useEffect(() => () => {
    generation.current += 1
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current)
    objectUrl.current = null
  }, [])

  const view = async () => {
    setLoading(true); setError(undefined)
    const requestGeneration = generation.current
    try {
      const result = props.intakeCaseId
        ? await getIntakeEvidenceContent(props.manager, props.intakeCaseId, props.intakeEvidenceType, props.documentVersionId!)
        : props.identityVerificationId ? await identityContent(props.manager, props.identityVerificationId) : props.evidenceType
        ? await getAssistedActionEvidenceContent(props.manager, props.loanApplicationId!, props.evidenceType, props.documentVersionId!)
        : await getDocumentContent(props.manager, props.loanApplicationId!, props.checklistItemId!, props.documentVersionId!)
      if (generation.current !== requestGeneration) return
      if (!['application/pdf', 'image/jpeg', 'image/png'].includes(result.contentType)) {
        setError('This document type cannot be displayed safely.')
        return
      }
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current)
      objectUrl.current = URL.createObjectURL(result.blob)
      setContent({ ...result, url: objectUrl.current })
    } catch {
      if (generation.current === requestGeneration) setError('Document content could not be loaded. Refresh the evidence and try again.')
    } finally { if (generation.current === requestGeneration) setLoading(false) }
  }

  return <div className="space-y-3 rounded-lg border bg-muted/25 p-4">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h3 className="font-semibold">Document viewer</h3><p className="text-sm text-muted-foreground">Contains Customer information. Use only for this case.</p></div>
      <div className="flex flex-wrap gap-2">
        {content ? <Button asChild variant="outline"><a href={content.url} download={downloadFilename(content.contentDisposition, props.filename, content.contentType)}><Download /> Download document</a></Button> : null}
        {content || loading ? <Button variant="outline" onClick={close}><EyeOff /> Close viewer</Button>
          : <Button onClick={() => void view()}><Eye /> {props.buttonLabel ?? 'View document'}</Button>}
        {loading ? <Spinner /> : null}
      </div>
    </div>
    {error ? <Alert variant="warning"><EyeOff /><AlertTitle>Viewer unavailable</AlertTitle><AlertDescription>{error}</AlertDescription></Alert> : null}
    {content ? <div className="overflow-hidden rounded-md border bg-background" aria-label={`Document viewer for ${props.filename}`}>
      {content.contentType === 'application/pdf'
        ? <object data={content.url} type="application/pdf" className="h-[65vh] min-h-96 w-full"><p className="p-4">The browser cannot display this PDF.</p></object>
        : <img src={content.url} alt={`Document evidence: ${props.filename}`} className="mx-auto max-h-[65vh] object-contain" />}
    </div> : null}
  </div>
}
