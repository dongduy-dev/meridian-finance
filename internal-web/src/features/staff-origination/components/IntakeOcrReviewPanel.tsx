import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import type { IntakeOcrReview } from '../api/contracts'
import { intakeOcrReviewQuery, intakeOcrStatusQuery, originationKeys } from '../api/queries'
import {
  finalizeIntakeOcrReview, getIntakeOcrReview, startIntakeOcr,
} from '../api/staff-origination-api'

const commonFields = [
  ['fullName', 'Full name'], ['identityReference', 'Identity reference'],
  ['phoneNumber', 'Phone number'], ['residentialAddress', 'Residential address'],
  ['employmentStatus', 'Employment status'], ['employerName', 'Employer name'],
  ['bankCode', 'Bank code'], ['bankNameSnapshot', 'Bank name'],
  ['accountHolderName', 'Account holder name'], ['accountNumber', 'Account number'],
  ['requestedAmount', 'Requested amount'], ['requestedTermMonths', 'Requested term months'],
] as const
const collateralFields = [
  ['collateral.type', 'Collateral type'], ['collateral.description', 'Collateral description'],
  ['collateral.estimatedValue', 'Collateral estimated value'],
  ['collateral.ownershipStatus', 'Collateral ownership status'],
  ['collateral.conditionNote', 'Collateral condition note'],
] as const

function expectedFields(evidenceType: string): readonly (readonly [string, string])[] {
  if (evidenceType === 'CUSTOMER_IDENTITY') return commonFields.slice(0, 2)
  return evidenceType === 'COLLATERAL_PAPER_APPLICATION'
    ? [...commonFields, ...collateralFields] : commonFields
}

function fieldsFrom(form: HTMLFormElement): Record<string, string> {
  const data = new FormData(form)
  return Object.fromEntries([...data.entries()]
    .filter(([, value]) => typeof value === 'string' && value !== '')
    .map(([name, value]) => [name, String(value)]))
}

function sameFields(first: Record<string, string>, second: Record<string, string>): boolean {
  const keys = Object.keys(first)
  return keys.length === Object.keys(second).length && keys.every((key) => first[key] === second[key])
}

export function IntakeOcrReviewPanel({
  manager, caseId, evidenceType, versionId, intakeOpen, onApplyReviewedValues,
}: {
  manager: AuthSessionManager
  caseId: string
  evidenceType: string
  versionId: string
  intakeOpen: boolean
  onApplyReviewedValues?: (source: {
    evidenceType: IntakeOcrReview['evidenceType']
    versionId: string
    reviewedFields: Record<string, string>
  }) => number
}) {
  const client = useQueryClient()
  const status = useQuery(intakeOcrStatusQuery(manager, caseId, evidenceType, versionId, true))
  const completed = status.data?.state === 'COMPLETED'
  const review = useQuery(intakeOcrReviewQuery(manager, caseId, evidenceType, versionId, completed))
  const [message, setMessage] = useState<string>()
  const [unknown, setUnknown] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const start = useMutation({
    mutationFn: () => startIntakeOcr(manager, caseId, evidenceType, versionId),
    onSuccess: (job) => {
      client.setQueryData(originationKeys.ocrStatus(caseId, evidenceType, versionId), job)
      setMessage(undefined)
    },
  })
  const noJob = status.error instanceof ApiError && status.error.errorCode === 'OCR_JOB_NOT_FOUND'
  const active = status.data?.state === 'PENDING' || status.data?.state === 'PROCESSING'

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!review.data) return
    const reviewedFields = fieldsFrom(event.currentTarget)
    setMessage(undefined)
    setUnknown(false)
    setSubmitting(true)
    try {
      const value = await finalizeIntakeOcrReview(manager, caseId, evidenceType, versionId, {
        expectedOcrResultId: review.data.ocrResultId, reviewedFields,
      })
      client.setQueryData(originationKeys.ocrReview(caseId, evidenceType, versionId), value)
      client.setQueryData(originationKeys.ocrStatus(caseId, evidenceType, versionId), {
        ...status.data!, disposition: 'REVIEWED',
      })
      setMessage('OCR review completed.')
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setMessage(caught instanceof ApiError ? caught.message : 'OCR review could not be completed.')
        return
      }
      try {
        const authoritative = await getIntakeOcrReview(manager, caseId, evidenceType, versionId)
        client.setQueryData(originationKeys.ocrReview(caseId, evidenceType, versionId), authoritative)
        if (authoritative.disposition === 'REVIEWED' && sameFields(authoritative.reviewedFields, reviewedFields)) {
          setMessage('The completed OCR review was confirmed from the authoritative result.')
          return
        }
      } catch { /* The result remains unknown and sensitive values are not retained. */ }
      setUnknown(true)
      setMessage('The OCR review result is unknown. Refresh and compare the authoritative review before another explicit action.')
    } finally {
      setSubmitting(false)
    }
  }

  if (status.isPending) return <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Checking OCR status…</p>
  if (noJob) return <div className="space-y-2"><Button type="button" variant="outline" disabled={!intakeOpen || start.isPending} onClick={() => start.mutate()}>{start.isPending ? 'Starting extraction…' : 'Extract fields'}</Button>{start.isError ? <p role="alert" className="text-sm">Field extraction could not be started. Manual intake remains available.</p> : null}</div>
  if (status.isError) return <div><p role="alert" className="text-sm">OCR status is unavailable.</p><Button type="button" size="sm" variant="outline" onClick={() => void status.refetch()}>Retry OCR status</Button></div>
  if (active) return <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Field extraction {status.data?.state.toLowerCase()}…</p>
  if (status.data?.state === 'FAILED') return <div className="space-y-2"><p role="alert" className="text-sm">OCR failed: {status.data.failureCategory ?? 'controlled processing failure'}.</p><p className="text-sm text-muted-foreground">Continue the manual intake workflow; OCR is optional.</p></div>
  if (!completed) return null
  if (review.isPending) return <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading OCR suggestions…</p>
  if (review.isError || !review.data) return <div><p role="alert" className="text-sm">OCR suggestions could not be loaded.</p><Button type="button" size="sm" variant="outline" onClick={() => void review.refetch()}>Retry suggestions</Button></div>

  const reconcile = async () => {
    const refreshed = await review.refetch()
    if (refreshed.data?.disposition === 'REVIEWED') {
      setMessage('The authoritative final OCR review is shown below.')
    } else {
      setMessage('No final review was found. Review the fields before another explicit confirmation.')
    }
    setUnknown(false)
  }
  const apply = () => {
    if (review.data?.disposition !== 'REVIEWED' || !onApplyReviewedValues) return
    const applied = onApplyReviewedValues({
      evidenceType: review.data.evidenceType,
      versionId,
      reviewedFields: review.data.reviewedFields,
    })
    setMessage(applied > 0
      ? 'Reviewed values copied to the intake forms. Verify them before saving or creating the application.'
      : 'No supported reviewed values were available to copy. Manual entry remains available.')
  }
  return <ReviewFields data={review.data} intakeOpen={intakeOpen} submitting={submitting} submit={submit} message={message} unknown={unknown} reconcile={reconcile} apply={onApplyReviewedValues ? apply : undefined} />
}

function ReviewFields({ data, intakeOpen, submitting, submit, message, unknown, reconcile, apply }: {
  data: IntakeOcrReview
  intakeOpen: boolean
  submitting: boolean
  submit: (event: FormEvent<HTMLFormElement>) => Promise<void>
  message?: string
  unknown: boolean
  reconcile: () => Promise<void>
  apply?: () => void
}) {
  const reviewed = data.disposition === 'REVIEWED'
  const suggestions = new Map(data.suggestions.map((item) => [item.fieldName, item]))
  return <div className="space-y-3 border-t pt-3">
    <h4 className="font-medium">OCR field review</h4>
    <p className="text-sm text-muted-foreground">OCR is advisory. Only a finalized review can be copied into the existing intake forms, and the existing Save, Add, or Create action is still required.</p>
    <form className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submit(event)}>
      {expectedFields(data.evidenceType).map(([name, label]) => {
        const suggestion = suggestions.get(name)
        const value = reviewed ? data.reviewedFields[name] ?? '' : suggestion?.proposedValue ?? ''
        return <label key={name} className="grid gap-1 text-sm font-medium">{label}
          {suggestion && !reviewed ? <span className="font-normal text-muted-foreground">OCR: {suggestion.proposedValue}{suggestion.confidence == null ? '' : ` · ${Math.round(suggestion.confidence * 100)}%`}</span> : null}
          <Input aria-label={label} name={name} defaultValue={value} readOnly={reviewed} autoComplete="off" />
        </label>
      })}
      {!reviewed ? <Button className="sm:col-span-2" disabled={!intakeOpen || unknown || submitting}>{submitting ? 'Confirming OCR review…' : 'Confirm final OCR review'}</Button> : null}
    </form>
    {reviewed ? <p className="text-sm font-medium">Final OCR review completed.</p> : null}
    {reviewed && apply ? <Button type="button" variant="outline" disabled={!intakeOpen} onClick={apply}>Apply reviewed values</Button> : null}
    {message ? <p role="alert" className="text-sm text-muted-foreground">{message}</p> : null}
    {unknown ? <Button type="button" variant="outline" onClick={() => void reconcile()}>Refresh authoritative OCR review</Button> : null}
  </div>
}
