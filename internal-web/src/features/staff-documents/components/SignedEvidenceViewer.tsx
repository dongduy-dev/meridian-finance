import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { DocumentContentViewer } from './DocumentContentViewer'

export function SignedEvidenceViewer({ loanApplicationId, evidenceType, documentVersionId, label = 'View signed evidence' }: {
  loanApplicationId: string; evidenceType: string; documentVersionId: string; label?: string
}) {
  const { manager, state } = useAuth()
  if (state.status !== 'authenticated') return null
  const actor = state.actor
  const allowed = hasPermission(actor, 'document:review')
    || (evidenceType === 'CUSTOMER_OFFER_RESPONSE' && hasPermission(actor, 'loan:offer:respond:staff') && actor.roles.includes('LOAN_OFFICER'))
    || (evidenceType === 'CUSTOMER_CONTRACT_ACKNOWLEDGMENT' && hasPermission(actor, 'loan:contract:read') && actor.roles.includes('ACCOUNTING_OFFICER'))
    || (evidenceType === 'CUSTOMER_CANCELLATION_REQUEST' && hasPermission(actor, 'loan:correction:staff') && actor.roles.includes('LOAN_OFFICER'))
  return allowed ? <DocumentContentViewer manager={manager} loanApplicationId={loanApplicationId}
    evidenceType={evidenceType} documentVersionId={documentVersionId} filename="Signed Customer action evidence" buttonLabel={label} /> : null
}
