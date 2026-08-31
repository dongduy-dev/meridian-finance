import { useCallback, useRef } from 'react'

export function useOperationIdentity() {
  const current = useRef<string | undefined>(undefined)

  const begin = useCallback(() => {
    current.current ??= crypto.randomUUID()
    return current.current
  }, [])

  const reset = useCallback(() => {
    current.current = undefined
  }, [])

  return { begin, reset }
}
