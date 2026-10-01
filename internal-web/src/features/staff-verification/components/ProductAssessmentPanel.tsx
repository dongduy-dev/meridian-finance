import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { hasPermission } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { humanizeKnownValue } from '@/features/staff-applications/model/presentation'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import type { StaffVerificationCase } from '../api/contracts'
import { staffVerificationCaseQuery } from '../api/queries'

const resultLabels: Record<string, string> = {
  PENDING_MANUAL_REVIEW: 'Pending manual review',
  VERIFIED: 'Verified',
  FAILED: 'Failed',
  REQUIRES_MORE_INFORMATION: 'Requires more information',
}

export function verificationResultLabel(value: string): string {
  return resultLabels[value] ?? 'Verification result unavailable'
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-sm text-muted-foreground">{label}</dt>
    <dd className="mt-1 break-words font-medium">{children}</dd></div>
}

export function ProductAssessmentEvidence({ data }: { data: StaffVerificationCase }) {
  if (data.productCode === 'SALARY_ADVANCE') {
    const verification = data.productVerification
    return <div className="space-y-4">
      <h3 className="font-semibold">Automated product verification</h3>
      <p className="text-sm text-muted-foreground">Read-only Salary Advance evidence recorded at submission or correction. Later Partner updates do not change it. No manual verification is required.</p>
      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Fact label="Sequence">{verification.verificationSequence}</Fact>
        <Fact label="Employee outcome">{humanizeKnownValue(verification.employeeVerificationOutcome)}</Fact>
        <Fact label="Product result">{verificationResultLabel(verification.productVerificationResult)}</Fact>
        <Fact label="Recorded total limit">{formatVnd(verification.totalLimitSnapshot)}</Fact>
        <Fact label="Recorded used amount">{formatVnd(verification.usedAmountSnapshot)}</Fact>
        <Fact label="Recorded reserved amount">{formatVnd(verification.reservedAmountSnapshot)}</Fact>
        <Fact label="Recorded available limit">{formatVnd(verification.availableLimitSnapshot)}</Fact>
        <Fact label="Verified">{formatTimestamp(verification.verifiedAt)}</Fact>
      </dl>
    </div>
  }

  return <div className="space-y-5">
    <p className="text-sm text-muted-foreground">Verified means the evidence is sufficient for credit review. It does not mean credit approval. Saved assessments are restricted lending evidence.</p>
    {data.productVerification.collateral ? <section className="space-y-3">
      <h3 className="font-semibold">Submitted collateral details</h3>
      <dl className="grid gap-4 sm:grid-cols-2">
        <Fact label="Collateral type">{humanizeKnownValue(data.productVerification.collateral.collateralType)}</Fact>
        <Fact label="Estimated value">{formatVnd(data.productVerification.collateral.estimatedValue)}</Fact>
        <Fact label="Description">{data.productVerification.collateral.description}</Fact>
        <Fact label="Ownership status">{data.productVerification.collateral.ownershipStatus}</Fact>
        <Fact label="Condition note">{data.productVerification.collateral.conditionNote}</Fact>
      </dl>
    </section> : null}
    <ol className="space-y-4" aria-label="Product assessment cycles">
      {data.productVerification.history.map((cycle) => <li key={cycle.verificationId} className="min-w-0 space-y-3 rounded-md border p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-semibold">Assessment cycle {cycle.verificationSequence}</h3>
          <span className="text-sm font-semibold">{verificationResultLabel(cycle.productVerificationResult)}</span>
        </div>
        <p className="text-sm text-muted-foreground">{cycle.sourceCorrectionRequestId ? 'Re-verification after correction' : 'Initial assessment'}
          {cycle.verificationId === data.productVerification.currentCycle.verificationId ? ' · Current authoritative cycle' : ' · Earlier cycle'}</p>
        <dl className="grid gap-4 sm:grid-cols-2">
          <Fact label="Created">{formatTimestamp(cycle.createdAt)}</Fact>
          <Fact label="Reviewed">{cycle.reviewedAt ? formatTimestamp(cycle.reviewedAt) : 'Not completed'}</Fact>
          <Fact label="Reviewed by">{cycle.reviewedBy ? <>{cycle.reviewedBy.displayName}<span className="block text-sm">{cycle.reviewedBy.email}</span></>
            : cycle.reviewedAt ? 'Staff member unavailable' : 'Not yet reviewed'}</Fact>
        </dl>
        <div className="min-w-0 rounded-md border border-dashed p-3">
          <h4 className="font-semibold">Verification assessment</h4>
          <p className="mt-1 whitespace-pre-wrap break-words">{cycle.assessmentNote ?? 'Assessment has not been completed.'}</p>
        </div>
      </li>)}
    </ol>
  </div>
}

export function ProductAssessmentPanel({ loanApplicationId }: { loanApplicationId: string }) {
  const { manager, state } = useAuth()
  const canReview = state.status === 'authenticated' && hasPermission(state.actor, 'loan:review')
  const canRead = state.status === 'authenticated'
    && (canReview || hasPermission(state.actor, 'approval:decide'))
  const query = useQuery(staffVerificationCaseQuery(manager, loanApplicationId,
    canRead && uuidSchema.safeParse(loanApplicationId).success))
  if (!canRead) return null
  const pending = query.data?.productCode !== 'SALARY_ADVANCE'
    && query.data?.productVerification.currentCycle.productVerificationResult === 'PENDING_MANUAL_REVIEW'
  return <Card><CardHeader><CardTitle>Product assessment / verification</CardTitle></CardHeader>
    <CardContent className="space-y-4">
      {query.isPending ? <p role="status">Loading product assessment…</p>
        : query.isError ? <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} />
          : query.data ? <><ProductAssessmentEvidence data={query.data} />
            {pending && canReview ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline"
              to={`/staff/applications/${loanApplicationId}/verification`}>Open product assessment workspace</Link> : null}</> : null}
    </CardContent>
  </Card>
}
