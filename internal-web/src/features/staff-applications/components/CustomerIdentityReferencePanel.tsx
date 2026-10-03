import { Eye, EyeOff, ShieldAlert } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/model/auth-context'
import { ApiError } from '@/lib/api'
import { revealCustomerIdentityReference } from '../api/staff-applications-api'

export const IDENTITY_REVEAL_BACKGROUND_TIMEOUT_MS = 60_000

export function CustomerIdentityReferencePanel({ loanApplicationId, mask, blocked, reconcile }: {
  loanApplicationId: string
  mask: string
  blocked: boolean
  reconcile: () => Promise<unknown>
}) {
  const { manager } = useAuth()
  const [reference, setReference] = useState<string>()
  const [inFlight, setInFlight] = useState(false)
  const [needsRevalidation, setNeedsRevalidation] = useState(false)
  const [feedback, setFeedback] = useState<string>()
  const generation = useRef(0)
  const pending = useRef(false)
  const clear = useCallback(() => {
    generation.current += 1
    setReference(undefined)
  }, [])

  useLayoutEffect(() => () => clear(), [clear])
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    const visibility = () => {
      if (timer) clearTimeout(timer)
      timer = document.visibilityState === 'hidden'
        ? setTimeout(clear, IDENTITY_REVEAL_BACKGROUND_TIMEOUT_MS) : undefined
    }
    visibility()
    document.addEventListener('visibilitychange', visibility)
    return () => {
      if (timer) clearTimeout(timer)
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [clear])

  const reveal = async () => {
    if (pending.current || blocked || needsRevalidation) return
    pending.current = true
    setInFlight(true)
    setFeedback(undefined)
    const requestGeneration = generation.current
    try {
      const result = await revealCustomerIdentityReference(manager, loanApplicationId)
      if (requestGeneration !== generation.current) return
      if (result.loanApplicationId !== loanApplicationId || !result.identityReference.endsWith(mask.slice(4))) {
        throw new Error('Protected reveal context is inconsistent.')
      }
      setReference(result.identityReference)
    } catch (error) {
      if (requestGeneration !== generation.current) return
      clear()
      if (error instanceof ApiError && error.status === 403) {
        setFeedback('Identity Reference access is denied. Your current session authority controls access.')
      } else if (error instanceof ApiError && error.status === 404) {
        setFeedback('The application is unavailable. Refresh the case before continuing.')
        setNeedsRevalidation(true)
        void reconcile().catch(() => undefined)
      } else if (error instanceof ApiError && error.status === 409
          && error.errorCode === 'CUSTOMER_IDENTITY_REFERENCE_UNAVAILABLE') {
        setFeedback('The stored Identity Reference is unavailable.')
      } else {
        setFeedback('The reveal result is unavailable. Refresh the case successfully before choosing Reveal again. The request was not repeated automatically.')
        setNeedsRevalidation(true)
      }
    } finally {
      pending.current = false
      setInFlight(false)
    }
  }

  return <div className="mt-5 space-y-3 border-t pt-5">
    <dl><dt className="text-sm text-muted-foreground">Identity Reference</dt><dd className="mt-1 font-mono font-semibold">{mask}</dd></dl>
    <p className="text-sm text-muted-foreground">Sensitive Customer identifier. Reveal only when needed for this case.</p>
    {reference ? <div className="rounded-md border-2 border-warning bg-warning/5 p-4 print:hidden" aria-live="polite">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Sensitive identity — temporary view</p>
      <p className="mt-2 break-all font-mono text-lg font-semibold">{reference}</p>
    </div> : null}
    {reference || inFlight ? <Button variant="outline" onClick={clear}><EyeOff />Hide Identity Reference</Button> : null}
    {!reference ? <Button variant="outline" disabled={blocked || inFlight || needsRevalidation} onClick={() => void reveal()}><Eye />{inFlight ? 'Revealing…' : 'Reveal Identity Reference'}</Button> : null}
    {feedback ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Identity Reference unavailable</AlertTitle><AlertDescription>{feedback}</AlertDescription></Alert> : null}
  </div>
}
