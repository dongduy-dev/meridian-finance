import { Eye, EyeOff } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import type { ApiBinaryResponse } from '@/lib/api'
import { getDocumentContent } from '../api/staff-documents-api'

type Props = {
  manager: AuthSessionManager
  loanApplicationId: string
  checklistItemId: string
  documentVersionId: string
  filename: string
}
export function DocumentContentViewer(props: Props) {
  const [content, setContent] = useState<(ApiBinaryResponse & { url: string }) | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()

  const close = () => setContent((current) => {
    if (current) URL.revokeObjectURL(current.url)
    return null
  })

  useEffect(() => () => {
    setContent((current) => {
      if (current) URL.revokeObjectURL(current.url)
      return null
    })
  }, [props.documentVersionId])

  const view = async () => {
    setLoading(true); setError(undefined)
    try {
      const result = await getDocumentContent(
        props.manager, props.loanApplicationId, props.checklistItemId, props.documentVersionId,
      )
      if (!['application/pdf', 'image/jpeg', 'image/png'].includes(result.contentType)) {
        setError('This document type cannot be displayed safely.')
        return
      }
      close()
      setContent({ ...result, url: URL.createObjectURL(result.blob) })
    } catch {
      setError('Document content could not be loaded. Refresh the evidence and try again.')
    } finally { setLoading(false) }
  }

  return <div className="space-y-3 rounded-lg border bg-muted/25 p-4">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h3 className="font-semibold">Sensitive document viewer</h3><p className="text-sm text-muted-foreground">Content stays in memory and is fetched only on request.</p></div>{content ? <Button variant="outline" onClick={close}><EyeOff /> Close viewer</Button> : <Button onClick={() => void view()} disabled={loading}>{loading ? <Spinner /> : <Eye />} View document</Button>}</div>
    {error ? <Alert variant="warning"><EyeOff /><AlertTitle>Viewer unavailable</AlertTitle><AlertDescription>{error}</AlertDescription></Alert> : null}
    {content ? <div className="overflow-hidden rounded-md border bg-background" aria-label={`Document viewer for ${props.filename}`}>
      {content.contentType === 'application/pdf'
        ? <object data={content.url} type="application/pdf" className="h-[65vh] min-h-96 w-full"><p className="p-4">The browser cannot display this PDF.</p></object>
        : <img src={content.url} alt={`Document evidence: ${props.filename}`} className="mx-auto max-h-[65vh] object-contain" />}
    </div> : null}
  </div>
}
