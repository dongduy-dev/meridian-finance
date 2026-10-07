import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'
import type { PartnerEligibilityReview, PartnerEligibilityReviewDecision } from '../api/contracts'
import { decidePartnerEligibilityReview } from '../api/partner-admin-api'
import { partnerEligibilityReviewQuery, partnerEligibilityReviewsQuery } from '../api/queries'
import { PartnerQueryErrorPanel } from '../components/PartnerQueryErrorPanel'

const rejectionReasons = [
  'NO_ELIGIBLE_CURRENT_EMPLOYEE',
  'IDENTITY_EVIDENCE_MISMATCH',
  'INSUFFICIENT_SOURCE_EVIDENCE',
] as const

const triggerLabels: Record<string, string> = {
  MATCHED_ACTIVE: 'Active employee matched',
  MATCHED_INACTIVE: 'Inactive employee matched',
  NOT_FOUND: 'No matching employee found',
  MULTIPLE_MATCHES: 'Multiple employees matched',
  PENDING_MANUAL_REVIEW: 'Manual review needed',
  MANUAL_REVIEW_APPROVED: 'Employment confirmed by review',
  MANUAL_REVIEW_REJECTED: 'Employment not confirmed by review',
}
const reviewStatusLabels: Record<string, string> = {
  PENDING: 'Pending review', APPROVED: 'Approved', REJECTED: 'Rejected', SUPERSEDED: 'Superseded employment attempt',
}
const unavailableReasonLabels: Record<string, string> = {
  REVIEW_RESOLVED: 'This review is complete',
  PRIOR_EFFECTIVE_MONTH: 'The effective month is no longer current',
  PARTNER_COMPANY_INACTIVE: 'The Partner Company is not active',
  SOURCE_BATCH_REPLACED: 'A newer employee snapshot replaced this review’s source. Ask the Customer to verify employment again against the current snapshot. This historical review cannot be retargeted.',
  CUSTOMER_IDENTITY_EVIDENCE_UNAVAILABLE: 'Current Customer identity evidence is unavailable',
}
const decisionLabels: Record<string, string> = {
  MANUAL_REVIEW_APPROVED: 'Employment confirmed', MANUAL_REVIEW_REJECTED: 'Employment not confirmed',
}
const reasonLabels: Record<string, string> = {
  CURRENT_EMPLOYEE_CONFIRMED: 'Current employee confirmed',
  NO_ELIGIBLE_CURRENT_EMPLOYEE: 'No eligible current employee',
  IDENTITY_EVIDENCE_MISMATCH: 'Identity evidence does not match',
  INSUFFICIENT_SOURCE_EVIDENCE: 'Employment evidence is insufficient',
}
const employmentLabels: Record<string, string> = {
  ACTIVE: 'Active', INACTIVE: 'Inactive', TERMINATED: 'Terminated', SUSPENDED: 'Suspended',
}

export const triggerLabel = (value: string) => triggerLabels[value] ?? 'Verification result unavailable'
export const reviewStatusLabel = (value: string) => reviewStatusLabels[value] ?? 'Review status unavailable'
export const unavailableReasonLabel = (value: string | null) =>
  value ? unavailableReasonLabels[value] ?? 'Review availability cannot be determined' : 'Review availability cannot be determined'
export const decisionLabel = (value: string) => decisionLabels[value] ?? 'Decision unavailable'
export const reasonLabel = (value: string) => reasonLabels[value] ?? 'Reason unavailable'
export const employmentLabel = (value: string) => employmentLabels[value] ?? 'Employment status unavailable'

type DecisionConfirmation = {
  decision: PartnerEligibilityReviewDecision
  employeeCode?: string
  reviewUpdatedAt: string
}

function refreshedDecisionMessage(
  review: PartnerEligibilityReview | undefined,
  attempted: PartnerEligibilityReviewDecision,
): string {
  const matchingApproval = attempted.outcome === 'APPROVE'
    && review?.status === 'APPROVED'
    && review.decisionOutcome === 'MANUAL_REVIEW_APPROVED'
    && review.decisionReason === 'CURRENT_EMPLOYEE_CONFIRMED'
    && review.selectedEmployee?.partnerEmployeeId === attempted.partnerEmployeeId
  const matchingRejection = attempted.outcome === 'REJECT'
    && review?.status === 'REJECTED'
    && review.decisionOutcome === 'MANUAL_REVIEW_REJECTED'
    && review.decisionReason === attempted.reasonCode
    && review.selectedEmployee === null

  if (matchingApproval || matchingRejection) {
    return 'Your decision was confirmed after Meridian refreshed the review.'
  }
  if (review?.status === 'PENDING') {
    return 'The decision was not confirmed. The review is still pending. Review the current evidence before submitting a decision again.'
  }
  return 'The review was completed with a different outcome. Your attempted decision was not confirmed.'
}

export function PartnerEligibilityReviewPage() {
  const { manager, state } = useAuth()
  const enabled = state.status === 'authenticated'
  const canManage = enabled && hasPermission(state.actor, 'partner:manage')
  const queue = useQuery(partnerEligibilityReviewsQuery(manager, 0, 20, enabled))
  const [requestedReviewId, setRequestedReviewId] = useState('')
  const selectedReviewId = requestedReviewId || queue.data?.items[0]?.reviewId || ''
  const detail = useQuery(partnerEligibilityReviewQuery(manager, selectedReviewId, enabled && selectedReviewId !== ''))
  const [selectedEmployeeId, setSelectedEmployeeId] = useState('')
  const [rejectionReason, setRejectionReason] = useState<(typeof rejectionReasons)[number]>('NO_ELIGIBLE_CURRENT_EMPLOYEE')
  const [busy, setBusy] = useState(false)
  const [commandError, setCommandError] = useState<Error>()
  const [commandMessage, setCommandMessage] = useState<string>()
  const [confirmation, setConfirmation] = useState<DecisionConfirmation>()

  const reconcile = async () => {
    const [detailResult, queueResult] = await Promise.all([detail.refetch(), queue.refetch()])
    if (detailResult.isError) throw detailResult.error
    if (queueResult.isError) throw queueResult.error
    return detailResult.data
  }

  const decide = async (decision: PartnerEligibilityReviewDecision) => {
    if (!selectedReviewId) return
    setBusy(true)
    setCommandError(undefined)
    setCommandMessage(undefined)
    try {
      await decidePartnerEligibilityReview(manager, selectedReviewId, decision)
      await reconcile()
      setCommandMessage('Your decision was confirmed.')
    } catch (error) {
      if (error instanceof NetworkError || (error instanceof ApiError && error.status >= 500)) {
        try {
          const refreshed = await reconcile()
          setSelectedEmployeeId('')
          setCommandMessage(refreshedDecisionMessage(refreshed, decision))
        } catch {
          setCommandError(error instanceof Error ? error : new Error('Decision result is unknown.'))
        }
      } else {
        setCommandError(error instanceof Error ? error : new Error('The review decision failed.'))
      }
    } finally {
      setBusy(false)
    }
  }

  const review = detail.data
  const knownTrigger = Boolean(review && Object.hasOwn(triggerLabels, review.triggerOutcome))
  const eligibleCandidates = review?.candidates.filter((candidate) =>
    candidate.active && candidate.employmentStatus === 'ACTIVE') ?? []
  const selectedCandidate = eligibleCandidates.find((candidate) =>
    candidate.partnerEmployeeId === selectedEmployeeId)
  const activeConfirmation = confirmation && review?.status === 'PENDING' && knownTrigger
    && confirmation.reviewUpdatedAt === review.updatedAt
    && (confirmation.decision.outcome === 'REJECT'
      || selectedCandidate?.partnerEmployeeId === confirmation.decision.partnerEmployeeId)
    ? confirmation
    : undefined

  const closeConfirmation = () => {
    const triggerId = confirmation?.decision.outcome === 'APPROVE'
      ? 'eligibility-approval-trigger'
      : 'eligibility-rejection-trigger'
    setConfirmation(undefined)
    setTimeout(() => document.getElementById(triggerId)?.focus(), 0)
  }

  return <section className="mx-auto max-w-7xl space-y-6">
    <div>
      <p className="text-sm font-semibold text-muted-foreground">PARTNER ADMINISTRATION</p>
      <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Eligibility reviews</h1>
      <p className="mt-2 text-muted-foreground">Review Customer employment matches using the current Partner employee records.</p>
    </div>

    <div className="grid gap-6 lg:grid-cols-[minmax(20rem,0.8fr)_minmax(0,1.4fr)]">
      <Card><CardHeader><CardTitle>Pending reviews</CardTitle></CardHeader><CardContent>
        {queue.isPending ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading eligibility reviews…</div> : null}
        {queue.isError ? <PartnerQueryErrorPanel error={queue.error} onRetry={() => void queue.refetch()} /> : null}
        {queue.data?.items.length === 0 ? <p className="text-sm text-muted-foreground">No pending Partner eligibility reviews require action.</p> : null}
        {queue.data?.items.length ? <ul className="space-y-3">{queue.data.items.map((item) => <li key={item.reviewId}>
          <button type="button" onClick={() => { setRequestedReviewId(item.reviewId); setSelectedEmployeeId(''); setConfirmation(undefined) }} className={`w-full rounded-md border p-3 text-left text-sm ${selectedReviewId === item.reviewId ? 'border-primary bg-muted' : ''}`}>
            <span className="block font-semibold">{item.partnerCompanyName}</span>
            <span className="mt-1 block text-muted-foreground">{item.partnerCompanyCode} · {item.effectiveMonth} · {triggerLabel(item.triggerOutcome)}</span>
            <span className="mt-1 block">Requested employee code: {item.requestedEmployeeCode}</span>
            {!item.reviewable ? <span className="mt-1 block text-destructive">Not reviewable: {unavailableReasonLabel(item.nonReviewableReason)}</span> : null}
          </button>
        </li>)}</ul> : null}
      </CardContent></Card>

      <Card><CardHeader><CardTitle>Review detail</CardTitle></CardHeader><CardContent className="space-y-5">
        {!selectedReviewId ? <p className="text-sm text-muted-foreground">Select a pending review to inspect current evidence.</p> : null}
        {detail.isPending && selectedReviewId ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading review evidence…</div> : null}
        {detail.isError ? <PartnerQueryErrorPanel error={detail.error} onRetry={() => void detail.refetch()} /> : null}
        {review ? <>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <Fact label="Partner Company">{review.partnerCompany.name} ({review.partnerCompany.companyCode})</Fact>
            <Fact label="Effective month">{review.effectiveMonth}</Fact>
            <Fact label="Trigger">{triggerLabel(review.triggerOutcome)}</Fact>
            <Fact label="Status">{reviewStatusLabel(review.status)}</Fact>
            <Fact label="Requested employee code">{review.requestedEmployeeCode}</Fact>
            <Fact label="Created">{formatTimestamp(review.createdAt)}</Fact>
          </dl>

          {review.nonReviewableReason ? <Alert variant="destructive"><AlertTitle>Review is not actionable</AlertTitle><AlertDescription>{unavailableReasonLabel(review.nonReviewableReason)}{review.nonReviewableReason === 'SOURCE_BATCH_REPLACED' ? '' : '. Refresh the review before taking another action.'}</AlertDescription></Alert> : null}
          <Button variant="outline" disabled={detail.isFetching || queue.isFetching} onClick={() => void Promise.all([detail.refetch(), queue.refetch()])}>Refresh review</Button>

          <div>
            <h2 className="font-semibold">Current identity-matched candidates</h2>
            {review.candidates.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">No current employee candidate matches the Customer identity evidence.</p> : <div className="mt-2 overflow-x-auto"><table className="w-full min-w-[34rem] text-left text-sm"><caption className="sr-only">Current Partner Employee candidates</caption><thead><tr className="border-b"><th className="p-2">Select</th><th className="p-2">Employee code</th><th className="p-2">Employment</th><th className="p-2">Active</th></tr></thead><tbody>{review.candidates.map((candidate) => <tr className="border-b" key={candidate.partnerEmployeeId}><td className="p-2"><input aria-label={`Select ${candidate.employeeCode}`} type="radio" name="candidate" value={candidate.partnerEmployeeId} checked={selectedCandidate?.partnerEmployeeId === candidate.partnerEmployeeId} disabled={!candidate.active || candidate.employmentStatus !== 'ACTIVE' || !canManage || !review.approvalAvailable} onChange={() => { setSelectedEmployeeId(candidate.partnerEmployeeId); setConfirmation(undefined) }} /></td><td className="p-2 font-mono">{candidate.employeeCode}</td><td className="p-2">{employmentLabel(candidate.employmentStatus)}</td><td className="p-2">{candidate.active ? 'Yes' : 'No'}</td></tr>)}</tbody></table></div>}
          </div>

          {review.decisionOutcome ? <Alert variant="information"><AlertTitle>Terminal outcome</AlertTitle><AlertDescription>{decisionLabel(review.decisionOutcome)} · {review.decisionReason ? reasonLabel(review.decisionReason) : 'Reason unavailable'} · {formatTimestamp(review.reviewedAt)}</AlertDescription></Alert> : null}

          {canManage && review.status === 'PENDING' && knownTrigger ? <div className="grid gap-4 border-t pt-4 sm:grid-cols-2">
            <div className="space-y-2"><p className="text-sm font-medium">Approve selected employee</p><Button id="eligibility-approval-trigger" disabled={busy || !review.approvalAvailable || !selectedCandidate} onClick={() => selectedCandidate && setConfirmation({ decision: { outcome: 'APPROVE', partnerEmployeeId: selectedCandidate.partnerEmployeeId, reasonCode: 'CURRENT_EMPLOYEE_CONFIRMED' }, employeeCode: selectedCandidate.employeeCode, reviewUpdatedAt: review.updatedAt })}>Review approval</Button></div>
            <div className="space-y-2"><label className="block text-sm font-medium">Rejection reason<select className="mt-1 flex h-10 w-full rounded-md border bg-background px-3" value={rejectionReason} onChange={(event) => { setRejectionReason(event.target.value as typeof rejectionReason); setConfirmation(undefined) }}>{rejectionReasons.map((reason) => <option key={reason} value={reason}>{reasonLabel(reason)}</option>)}</select></label><Button id="eligibility-rejection-trigger" variant="destructive" disabled={busy || !review.rejectionAvailable} onClick={() => setConfirmation({ decision: { outcome: 'REJECT', partnerEmployeeId: null, reasonCode: rejectionReason }, reviewUpdatedAt: review.updatedAt })}>Review rejection</Button></div>
          </div> : null}

          {commandMessage ? <Alert variant="information"><AlertTitle>Review refreshed</AlertTitle><AlertDescription>{commandMessage}</AlertDescription></Alert> : null}
          {commandError ? <Alert variant="destructive"><AlertTitle>Decision was not confirmed</AlertTitle><AlertDescription>Refresh this review before deciding whether to try again.{commandError instanceof ApiError && commandError.requestId ? <RequestCorrelation requestId={commandError.requestId} /> : null}</AlertDescription></Alert> : null}
        </> : null}
      </CardContent></Card>
    </div>
    {activeConfirmation && review ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="eligibility-confirm-title"><div className="w-full max-w-lg space-y-4 rounded-lg bg-card p-6 shadow-xl"><h2 id="eligibility-confirm-title" className="text-xl font-semibold">{activeConfirmation.decision.outcome === 'APPROVE' ? 'Confirm employment approval' : 'Confirm eligibility rejection'}</h2><dl className="grid gap-3 text-sm sm:grid-cols-2"><Fact label="Partner Company">{review.partnerCompany.name} ({review.partnerCompany.companyCode})</Fact><Fact label="Effective month">{review.effectiveMonth}</Fact>{activeConfirmation.decision.outcome === 'APPROVE' ? <Fact label="Selected employee">{activeConfirmation.employeeCode}</Fact> : <Fact label="Rejection reason">{reasonLabel(activeConfirmation.decision.reasonCode)}</Fact>}</dl>{activeConfirmation.decision.outcome === 'APPROVE' ? <p className="text-sm text-muted-foreground">This confirms the selected current Partner employment for this Customer and may be used for Partner employment and Salary Advance eligibility. If a verified employment relationship belongs to another Partner, Meridian may replace it through this controlled approval.</p> : <p className="text-sm text-muted-foreground">This closes the review with the selected rejection reason. The rejection does not create or change a Partner Employee link.</p>}<div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={closeConfirmation}>Cancel</Button><Button autoFocus variant={activeConfirmation.decision.outcome === 'REJECT' ? 'destructive' : 'default'} disabled={busy} onClick={() => { const decision = activeConfirmation.decision; setConfirmation(undefined); void decide(decision) }}>{activeConfirmation.decision.outcome === 'APPROVE' ? 'Confirm approval' : 'Confirm rejection'}</Button></div></div></div> : null}
  </section>
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return <div><dt className="text-muted-foreground">{label}</dt><dd className="font-medium">{children}</dd></div>
}
