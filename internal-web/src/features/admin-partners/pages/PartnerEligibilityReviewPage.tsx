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
import type { PartnerEligibilityReviewDecision } from '../api/contracts'
import { decidePartnerEligibilityReview } from '../api/partner-admin-api'
import { partnerEligibilityReviewQuery, partnerEligibilityReviewsQuery } from '../api/queries'
import { PartnerQueryErrorPanel } from '../components/PartnerQueryErrorPanel'

const rejectionReasons = [
  'NO_ELIGIBLE_CURRENT_EMPLOYEE',
  'IDENTITY_EVIDENCE_MISMATCH',
  'INSUFFICIENT_SOURCE_EVIDENCE',
] as const

const humanizeKnownValue = (value: string) =>
  value.toLowerCase().split('_').map((word) =>
    word.replace(/^./, (letter) => letter.toUpperCase())).join(' ')

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

  const reconcile = async () => {
    const [detailResult, queueResult] = await Promise.all([detail.refetch(), queue.refetch()])
    if (detailResult.isError) throw detailResult.error
    if (queueResult.isError) throw queueResult.error
  }

  const decide = async (decision: PartnerEligibilityReviewDecision) => {
    if (!selectedReviewId) return
    setBusy(true)
    setCommandError(undefined)
    setCommandMessage(undefined)
    try {
      await decidePartnerEligibilityReview(manager, selectedReviewId, decision)
      await reconcile()
      setCommandMessage('The authoritative review outcome was confirmed.')
    } catch (error) {
      if (error instanceof NetworkError || (error instanceof ApiError && error.status >= 500)) {
        try {
          await reconcile()
          setCommandMessage('The command result was uncertain. Meridian reconciled this review through an authoritative GET.')
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
  const eligibleCandidates = review?.candidates.filter((candidate) =>
    candidate.active && candidate.employmentStatus === 'ACTIVE') ?? []
  const effectiveSelectedEmployeeId = eligibleCandidates.some((candidate) =>
    candidate.partnerEmployeeId === selectedEmployeeId)
    ? selectedEmployeeId
    : eligibleCandidates[0]?.partnerEmployeeId ?? ''

  return <section className="mx-auto max-w-7xl space-y-6">
    <div>
      <p className="text-sm font-semibold text-muted-foreground">PARTNER ADMINISTRATION</p>
      <h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Eligibility reviews</h1>
      <p className="mt-2 text-muted-foreground">Inspect ambiguous Customer–Partner Employee matches against current authoritative Partner evidence.</p>
    </div>

    <div className="grid gap-6 lg:grid-cols-[minmax(20rem,0.8fr)_minmax(0,1.4fr)]">
      <Card><CardHeader><CardTitle>Pending shared queue</CardTitle></CardHeader><CardContent>
        {queue.isPending ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading eligibility reviews…</div> : null}
        {queue.isError ? <PartnerQueryErrorPanel error={queue.error} onRetry={() => void queue.refetch()} /> : null}
        {queue.data?.items.length === 0 ? <p className="text-sm text-muted-foreground">No pending Partner eligibility reviews require action.</p> : null}
        {queue.data?.items.length ? <ul className="space-y-3">{queue.data.items.map((item) => <li key={item.reviewId}>
          <button type="button" onClick={() => { setRequestedReviewId(item.reviewId); setSelectedEmployeeId('') }} className={`w-full rounded-md border p-3 text-left text-sm ${selectedReviewId === item.reviewId ? 'border-primary bg-muted' : ''}`}>
            <span className="block font-semibold">{item.partnerCompanyName}</span>
            <span className="mt-1 block text-muted-foreground">{item.partnerCompanyCode} · {item.effectiveMonth} · {humanizeKnownValue(item.triggerOutcome)}</span>
            <span className="mt-1 block">Requested employee code: {item.requestedEmployeeCode}</span>
            {!item.reviewable ? <span className="mt-1 block text-destructive">Not reviewable: {humanizeKnownValue(item.nonReviewableReason ?? 'REVIEW_UNAVAILABLE')}</span> : null}
          </button>
        </li>)}</ul> : null}
      </CardContent></Card>

      <Card><CardHeader><CardTitle>Authoritative review detail</CardTitle></CardHeader><CardContent className="space-y-5">
        {!selectedReviewId ? <p className="text-sm text-muted-foreground">Select a pending review to inspect current evidence.</p> : null}
        {detail.isPending && selectedReviewId ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading review evidence…</div> : null}
        {detail.isError ? <PartnerQueryErrorPanel error={detail.error} onRetry={() => void detail.refetch()} /> : null}
        {review ? <>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <Fact label="Partner Company">{review.partnerCompany.name} ({review.partnerCompany.companyCode})</Fact>
            <Fact label="Effective month">{review.effectiveMonth}</Fact>
            <Fact label="Trigger">{humanizeKnownValue(review.triggerOutcome)}</Fact>
            <Fact label="Status">{humanizeKnownValue(review.status)}</Fact>
            <Fact label="Requested employee code">{review.requestedEmployeeCode}</Fact>
            <Fact label="Created">{formatTimestamp(review.createdAt)}</Fact>
          </dl>

          {review.nonReviewableReason ? <Alert variant="destructive"><AlertTitle>Review is not actionable</AlertTitle><AlertDescription>{humanizeKnownValue(review.nonReviewableReason)}. Fresh Customer verification may be required.</AlertDescription></Alert> : null}

          <div>
            <h2 className="font-semibold">Current identity-matched candidates</h2>
            {review.candidates.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">No current authoritative employee candidate matches the Customer identity evidence.</p> : <div className="mt-2 overflow-x-auto"><table className="w-full min-w-[34rem] text-left text-sm"><caption className="sr-only">Current Partner Employee candidates</caption><thead><tr className="border-b"><th className="p-2">Select</th><th className="p-2">Employee code</th><th className="p-2">Employment</th><th className="p-2">Active</th></tr></thead><tbody>{review.candidates.map((candidate) => <tr className="border-b" key={candidate.partnerEmployeeId}><td className="p-2"><input aria-label={`Select ${candidate.employeeCode}`} type="radio" name="candidate" value={candidate.partnerEmployeeId} checked={effectiveSelectedEmployeeId === candidate.partnerEmployeeId} disabled={!candidate.active || candidate.employmentStatus !== 'ACTIVE' || !canManage || !review.approvalAvailable} onChange={() => setSelectedEmployeeId(candidate.partnerEmployeeId)} /></td><td className="p-2 font-mono">{candidate.employeeCode}</td><td className="p-2">{humanizeKnownValue(candidate.employmentStatus)}</td><td className="p-2">{candidate.active ? 'Yes' : 'No'}</td></tr>)}</tbody></table></div>}
          </div>

          {review.decisionOutcome ? <Alert variant="information"><AlertTitle>Terminal outcome</AlertTitle><AlertDescription>{humanizeKnownValue(review.decisionOutcome)} · {review.decisionReason ? humanizeKnownValue(review.decisionReason) : 'Reason unavailable'} · {formatTimestamp(review.reviewedAt)}</AlertDescription></Alert> : null}

          {canManage && review.status === 'PENDING' ? <div className="grid gap-4 border-t pt-4 sm:grid-cols-2">
            <div className="space-y-2"><p className="text-sm font-medium">Approve exact current employee</p><Button disabled={busy || !review.approvalAvailable || !effectiveSelectedEmployeeId || eligibleCandidates.length === 0} onClick={() => void decide({ outcome: 'APPROVE', partnerEmployeeId: effectiveSelectedEmployeeId, reasonCode: 'CURRENT_EMPLOYEE_CONFIRMED' })}>Approve selected employee</Button></div>
            <div className="space-y-2"><label className="block text-sm font-medium">Rejection reason<select className="mt-1 flex h-10 w-full rounded-md border bg-background px-3" value={rejectionReason} onChange={(event) => setRejectionReason(event.target.value as typeof rejectionReason)}>{rejectionReasons.map((reason) => <option key={reason} value={reason}>{humanizeKnownValue(reason)}</option>)}</select></label><Button variant="destructive" disabled={busy || !review.rejectionAvailable} onClick={() => void decide({ outcome: 'REJECT', partnerEmployeeId: null, reasonCode: rejectionReason })}>Reject review</Button></div>
          </div> : null}

          {commandMessage ? <Alert variant="information"><AlertTitle>Review refreshed</AlertTitle><AlertDescription>{commandMessage}</AlertDescription></Alert> : null}
          {commandError ? <Alert variant="destructive"><AlertTitle>Decision was not confirmed</AlertTitle><AlertDescription>{commandError.message}{commandError instanceof ApiError && commandError.requestId ? <RequestCorrelation requestId={commandError.requestId} /> : null}</AlertDescription></Alert> : null}
        </> : null}
      </CardContent></Card>
    </div>
  </section>
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return <div><dt className="text-muted-foreground">{label}</dt><dd className="font-medium">{children}</dd></div>
}
