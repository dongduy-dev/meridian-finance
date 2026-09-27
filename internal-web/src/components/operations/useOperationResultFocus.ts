import { useCallback, useEffect, useRef } from 'react'

import type { OperationStatus } from './OperationStatusPanel'

const settledStatuses = new Set<OperationStatus>(['RESOLVED', 'BLOCKED', 'RESULT_UNKNOWN'])

export function useOperationResultFocus(status: OperationStatus, headingId: string) {
  const focusAfterSubmission = useRef(false)

  const requestResultFocus = useCallback(() => {
    focusAfterSubmission.current = true
  }, [])

  useEffect(() => {
    if (!focusAfterSubmission.current || !settledStatuses.has(status)) return
    focusAfterSubmission.current = false
    document.getElementById(headingId)?.focus()
  }, [headingId, status])

  return requestResultFocus
}
