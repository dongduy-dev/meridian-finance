import { AlertCircle, Download } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/auth-context'
import { ApiError } from '@/lib/api'
import { customerErrorMessage } from '@/lib/errors/customer-error-message'

import { createDocumentApi, type DocumentVersion } from '../document-api'
import { DocumentVersionSummary } from './DocumentVersionSummary'

export function CurrentDocumentSummary({ loanApplicationId, checklistItemId, version }: {
  loanApplicationId: string
  checklistItemId: string
  version: DocumentVersion
}) {
  return (
    <div className="min-w-0 space-y-4">
      <DocumentVersionSummary version={version} />
      <DocumentDownload key={`${loanApplicationId}/${checklistItemId}/${version.documentVersionId}`}
        loanApplicationId={loanApplicationId} checklistItemId={checklistItemId} version={version} />
    </div>
  )
}

function DocumentDownload({ loanApplicationId, checklistItemId, version }: {
  loanApplicationId: string
  checklistItemId: string
  version: DocumentVersion
}) {
  const { manager } = useAuth()
  const api = useMemo(() => createDocumentApi(manager), [manager])
  const generation = useRef(0)
  const inFlight = useRef(false)
  const downloadUrls = useRef(new Map<string, ReturnType<typeof setTimeout>>())
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>()

  useEffect(() => {
    const urls = downloadUrls.current
    const mountedGeneration = generation.current
    return () => {
      generation.current = mountedGeneration + 1
      for (const [url, timer] of urls) {
        clearTimeout(timer)
        URL.revokeObjectURL(url)
      }
      urls.clear()
    }
  }, [])

  const download = async () => {
    if (inFlight.current) return
    const requestGeneration = generation.current
    inFlight.current = true
    setPending(true)
    setError(undefined)
    try {
      const blob = await api.getDocumentContent(loanApplicationId, checklistItemId, version.documentVersionId)
      if (requestGeneration !== generation.current) return
      const url = URL.createObjectURL(blob)
      // Give the browser time to consume the URL; unmount also releases surviving URLs.
      downloadUrls.current.set(url, setTimeout(() => {
        URL.revokeObjectURL(url)
        downloadUrls.current.delete(url)
      }, 1000))
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = version.originalFilename
      anchor.click()
    } catch (failure) {
      if (requestGeneration === generation.current) setError(failure)
    } finally {
      if (requestGeneration === generation.current) {
        inFlight.current = false
        setPending(false)
      }
    }
  }

  return (
    <div className="min-w-0 space-y-4">
      <Button variant="secondary" className="w-full sm:w-auto" type="button" disabled={pending} onClick={() => void download()}>
        {pending ? <Spinner /> : <Download aria-hidden="true" />}
        {pending ? 'Downloading…' : 'Download document'}
      </Button>
      <span className="sr-only" role="status">{pending ? 'Document download in progress.' : ''}</span>
      {error ? (
        <Alert variant="destructive" aria-live="polite">
          <AlertCircle aria-hidden="true" />
          <AlertTitle>Document could not be downloaded</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>{customerErrorMessage(error, "We couldn't download this document. Refresh the page and try again.")}</p>
            {error instanceof ApiError && error.requestId ? <p className="break-all text-xs">Support reference: {error.requestId}</p> : null}
          </AlertDescription>
        </Alert>
      ) : null}
    </div>
  )
}
