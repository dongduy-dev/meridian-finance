import { identityContent } from '@/features/customer-identity/api'
import { Eye, EyeOff } from 'lucide-react'
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
} & ({ checklistItemId: string; evidenceType?: never; identityVerificationId?: never } | { evidenceType: string; checklistItemId?: never; identityVerificationId?: never } | { identityVerificationId: string; checklistItemId?: never; evidenceType?: never })
export function DocumentContentViewer(props: Props) {
  const session = useSyncExternalStore(props.manager.subscribe, props.manager.getSnapshot, props.manager.getSnapshot)
  const authority = session.status === 'authenticated'
    ? `${session.actor.userId}:${[...session.actor.permissions].sort().join(',')}:${[...session.actor.roles].sort().join(',')}`
    : session.status
  if (session.status !== 'authenticated') return null
  return <ContentViewer key={`${authority}:${props.loanApplicationId}:${props.identityVerificationId ?? props.checklistItemId ?? props.evidenceType}:${props.documentVersionId}`} {...props} />
}

function ContentViewer(props: Props) {
  const [content, setContent] = useState<(ApiBinaryResponse & { url: string }) | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()
  const generation = useRef(0)
  const objectUrl = useRef<string | null>(null)

  const close = () => setContent((current) => {
    if (current) URL.revokeObjectURL(current.url)
    objectUrl.current = null
    return null
  })

  useEffect(() => () => {
    generation.current += 1
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current)
  }, [])

  const view = async () => {
    setLoading(true); setError(undefined)
    const requestGeneration = generation.current
    try {
      const result = props.identityVerificationId ? await identityContent(props.manager, props.identityVerificationId) : props.evidenceType
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
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h3 className="font-semibold">Document viewer</h3><p className="text-sm text-muted-foreground">Contains Customer information. Use only for this case.</p></div>{content ? <Button variant="outline" onClick={close}><EyeOff /> Close viewer</Button> : <Button onClick={() => void view()} disabled={loading}>{loading ? <Spinner /> : <Eye />} {props.buttonLabel ?? 'View document'}</Button>}</div>
    {error ? <Alert variant="warning"><EyeOff /><AlertTitle>Viewer unavailable</AlertTitle><AlertDescription>{error}</AlertDescription></Alert> : null}
    {content ? <div className="overflow-hidden rounded-md border bg-background" aria-label={`Document viewer for ${props.filename}`}>
      {content.contentType === 'application/pdf'
        ? <object data={content.url} type="application/pdf" className="h-[65vh] min-h-96 w-full"><p className="p-4">The browser cannot display this PDF.</p></object>
        : <img src={content.url} alt={`Document evidence: ${props.filename}`} className="mx-auto max-h-[65vh] object-contain" />}
    </div> : null}
  </div>
}
