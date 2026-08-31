import { AlertCircle, Upload } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { ApiError } from '@/lib/api'

import type { CustomerDocumentChecklistItem } from '../document-api'
import { useUploadDocumentMutation } from '../document-queries'
import { formatFileSize } from '../document-presentation'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const acceptedTypes = new Set(['application/pdf', 'image/jpeg', 'image/png'])

function newOperationId() {
  return crypto.randomUUID()
}

const uploadMessages: Record<string, string> = {
  DOCUMENT_CHECKLIST_NOT_FOUND: 'This document checklist is no longer available.',
  DOCUMENT_ACCESS_DENIED: 'This document is not available for upload.',
  DOCUMENT_UPLOAD_DENIED: 'This document cannot be uploaded in its current state.',
  INVALID_DOCUMENT_UPLOAD: 'Meridian rejected the file content, type, size, or format.',
  STALE_DOCUMENT_VERSION: 'The document changed since this page loaded. Meridian refreshed the checklist; review the current version before retrying.',
  IDEMPOTENCY_KEY_REUSED: 'This upload identity no longer matches the selected file. Select the file again to start a new upload.',
  SYSTEM_STATE_CONFLICT: 'The current document state could not be reconciled safely.',
  DOCUMENT_STORAGE_UNAVAILABLE: 'Document storage is temporarily unavailable. You can retry this same file.',
  VALIDATION_FAILED: 'Meridian could not validate this upload.',
}

export function DocumentUpload({
  loanApplicationId,
  item,
  action,
  onVersionConflict,
}: {
  loanApplicationId: string
  item: CustomerDocumentChecklistItem
  action: 'upload' | 'replace'
  onVersionConflict: () => Promise<unknown>
}) {
  const mutation = useUploadDocumentMutation()
  const [file, setFile] = useState<File>()
  const [localError, setLocalError] = useState<string>()
  const [serverError, setServerError] = useState<Error>()
  const [succeeded, setSucceeded] = useState(false)
  const [operationId, setOperationId] = useState(newOperationId)
  const baseline = action === 'replace' ? item.currentVersion?.documentVersionId : undefined
  const previousBaseline = useRef(baseline)

  useEffect(() => {
    if (previousBaseline.current !== baseline) {
      previousBaseline.current = baseline
      setOperationId(newOperationId())
      setServerError((current: Error | undefined) => (
        current instanceof ApiError && current.errorCode === 'STALE_DOCUMENT_VERSION'
          ? current
          : undefined
      ))
      setSucceeded(false)
    }
  }, [baseline])

  const chooseFile = (selected: File | undefined) => {
    setFile(selected)
    setServerError(undefined)
    setSucceeded(false)
    setOperationId(newOperationId())
    if (!selected) {
      setLocalError(undefined)
    } else if (selected.size > MAX_FILE_SIZE) {
      setLocalError('Choose a file no larger than 10 MiB.')
    } else if (selected.type && !acceptedTypes.has(selected.type)) {
      setLocalError('Choose a PDF, JPEG, or PNG file. Meridian will verify the file content after upload.')
    } else {
      setLocalError(undefined)
    }
  }

  const submit = async () => {
    if (!file || localError || mutation.isPending) return
    setServerError(undefined)
    try {
      await mutation.upload({
        loanApplicationId,
        checklistItemId: item.checklistItemId,
        uploadRequestId: operationId,
        expectedCurrentVersionId: baseline,
        file,
      })
      setFile(undefined)
      setOperationId(newOperationId())
      setSucceeded(true)
    } catch (error) {
      setServerError(error instanceof Error ? error : new Error('The upload could not be completed.'))
      if (error instanceof ApiError && error.errorCode === 'STALE_DOCUMENT_VERSION') {
        await onVersionConflict()
      }
    }
  }

  if (action === 'replace' && !baseline) {
    return (
      <Alert variant="warning">
        <AlertCircle aria-hidden="true" />
        <AlertTitle>Replacement is temporarily unavailable</AlertTitle>
        <AlertDescription>The current document version is missing, so Customer Web cannot establish a safe replacement baseline.</AlertDescription>
      </Alert>
    )
  }

  const knownMessage = serverError instanceof ApiError ? uploadMessages[serverError.errorCode] : undefined
  return (
    <div className="space-y-4 rounded-md border border-border bg-background p-4">
      <div className="space-y-2">
        <label htmlFor={`file-${item.checklistItemId}`} className="block text-sm font-semibold">
          {action === 'replace' ? 'Choose replacement file' : 'Choose file'}
        </label>
        <input
          id={`file-${item.checklistItemId}`}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          className="block min-h-11 w-full min-w-0 cursor-pointer rounded-md border border-input bg-card p-2 text-sm file:mr-3 file:rounded-sm file:border-0 file:bg-muted file:px-3 file:py-2 file:font-semibold"
          aria-describedby={`file-${item.checklistItemId}-help${localError ? ` file-${item.checklistItemId}-error` : ''}`}
          onChange={(event) => chooseFile(event.target.files?.[0])}
        />
        <p id={`file-${item.checklistItemId}-help`} className="text-xs leading-5 text-muted-foreground">
          PDF, JPEG, or PNG; maximum 10 MiB. Meridian verifies actual file content and MIME type.
        </p>
        {file ? <p className="break-all text-sm">Selected: {file.name} ({formatFileSize(file.size)})</p> : null}
        {localError ? <p id={`file-${item.checklistItemId}-error`} className="text-sm text-danger">{localError}</p> : null}
      </div>
      {serverError ? (
        <Alert variant="destructive" aria-live="polite">
          <AlertCircle aria-hidden="true" />
          <AlertTitle>{action === 'replace' ? 'Replacement failed' : 'Upload failed'}</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>{knownMessage ?? (serverError instanceof ApiError ? serverError.message : 'The upload could not be completed. Check your connection and retry the same file if appropriate.')}</p>
            {serverError instanceof ApiError && serverError.requestId ? <p className="break-all text-xs">Support reference: {serverError.requestId}</p> : null}
          </AlertDescription>
        </Alert>
      ) : null}
      {succeeded ? (
        <Alert variant="success" aria-live="polite">
          <Upload aria-hidden="true" />
          <AlertTitle>{action === 'replace' ? 'Replacement uploaded' : 'Document uploaded'}</AlertTitle>
          <AlertDescription>Meridian recorded the file and refreshed the current checklist state.</AlertDescription>
        </Alert>
      ) : null}
      <Button type="button" disabled={!file || Boolean(localError) || mutation.isPending} onClick={() => void submit()}>
        {mutation.isPending ? <Spinner /> : <Upload aria-hidden="true" />}
        {mutation.isPending ? 'Uploading…' : action === 'replace' ? 'Replace document' : 'Upload document'}
      </Button>
      <span className="sr-only" aria-live="polite">{mutation.isPending ? 'Document upload in progress.' : ''}</span>
    </div>
  )
}
