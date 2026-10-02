import type { DocumentReviewHistory as Review } from '../api/contracts'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { formatTimestamp } from '@/lib/format/presentation'

export function DocumentReviewHistory({ reviews }: { reviews: Review[] }) {
  return <ul className="space-y-3">{reviews.map((review) => <li key={review.reviewDecisionId} className="rounded-md border p-3">
    <h3 className="font-semibold">Review</h3>
    <dl className="mt-2 grid gap-3 text-sm sm:grid-cols-2">
      <div><dt className="text-muted-foreground">Outcome</dt><dd>{humanizeKnownValue(review.outcome)}</dd></div>
      <div><dt className="text-muted-foreground">Reviewed at</dt><dd>{formatTimestamp(review.decidedAt)}</dd></div>
      <div><dt className="text-muted-foreground">Reviewed by</dt><dd>{review.reviewer?.displayName ?? 'Reviewer unavailable'}{review.reviewer ? <span className="block break-all text-xs text-muted-foreground">{review.reviewer.email}</span> : null}</dd></div>
      {review.waiverReasonCode ? <div><dt className="text-muted-foreground">Waiver reason</dt><dd>{humanizeKnownValue(review.waiverReasonCode)}</dd></div> : null}
      {review.correctionReasonCode ? <div><dt className="text-muted-foreground">Correction reason</dt><dd>{humanizeKnownValue(review.correctionReasonCode)}</dd></div> : null}
      {review.customerInstruction ? <div><dt className="text-muted-foreground">Customer instruction</dt><dd className="whitespace-pre-wrap break-words">{review.customerInstruction}</dd></div> : null}
      {review.restrictedStaffNoteReadable && review.restrictedStaffNotes ? <div className="sm:col-span-2"><dt className="text-muted-foreground">Restricted document note</dt><dd className="whitespace-pre-wrap break-words">{review.restrictedStaffNotes}</dd></div> : null}
    </dl>
  </li>)}</ul>
}
