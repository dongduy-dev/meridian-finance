import { useCallback, useEffect, useState } from 'react'

function parseRetryAfter(value?: string): number | undefined {
  if (!value || !/^[1-9]\d*$/.test(value)) return undefined
  const seconds = Number(value)
  return Number.isSafeInteger(seconds) ? seconds : undefined
}

export function useRateLimitRecovery() {
  const [retryAt, setRetryAt] = useState<number>()
  const [now, setNow] = useState(0)
  const remainingSeconds = retryAt ? Math.max(0, Math.ceil((retryAt - now) / 1_000)) : 0
  useEffect(() => {
    if (!retryAt || remainingSeconds === 0) return
    const interval = window.setInterval(() => setNow(Date.now()), 250)
    return () => window.clearInterval(interval)
  }, [remainingSeconds, retryAt])
  const start = useCallback((retryAfter?: string) => {
    const seconds = parseRetryAfter(retryAfter)
    const current = Date.now()
    setNow(current)
    setRetryAt(seconds ? current + seconds * 1_000 : undefined)
  }, [])
  return { isActive: remainingSeconds > 0, remainingSeconds, start }
}
