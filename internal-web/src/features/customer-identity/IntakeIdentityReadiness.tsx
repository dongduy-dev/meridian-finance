import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { submitIntakeIdentity } from './api'

export function IntakeIdentityReadiness({ caseId, status, versionId, open }: { caseId: string; status?: string; versionId?: string; open: boolean }) {
  const { manager, state } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(false)
  const allowed = state.status === 'authenticated' && hasPermission(state.actor, 'customer:identity:verify')
  const submit = async () => {
    if (!versionId || busy) return
    setBusy(true); setError(false)
    try { const v = await submitIntakeIdentity(manager, caseId, versionId); navigate(`/staff/customer-identity-verifications/${v.verificationId}`) }
    catch { setError(true) } finally { setBusy(false) }
  }
  return <article className="space-y-3 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Customer identity readiness</h2>
    <p>{status === 'VERIFIED' ? 'Customer identity is verified. A new identity file is not required for this application.' : 'Customer identity verification must complete before creating a UCL or Collateral Loan application.'}</p>
    {allowed && status !== 'VERIFIED' ? <><Button disabled={!open || !versionId || busy || error} onClick={() => void submit()}>Review current Customer identity evidence</Button><Button variant="link" asChild><Link to="/staff/customer-identity-verifications">Open identity verification queue</Link></Button></> : null}
    {allowed && !versionId && status !== 'VERIFIED' ? <p>Upload Customer identity / CCCD intake evidence first.</p> : null}
    {error ? <p role="alert">Verification submission was not confirmed. Check the pending queue before trying again with this exact evidence.</p> : null}
  </article>
}
