import { ArrowLeft, ArrowRight, Info, ShieldAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch, type UseFormRegisterReturn } from 'react-hook-form'
import { Link, useBlocker, useNavigate, useSearchParams } from 'react-router-dom'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { FocusedFlowLayout } from '@/components/layout/FocusedFlowLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { AccountFormField } from '@/features/account/components/AccountFormField'
import {
  EvidenceRequirements, OriginationExitWarning, OriginationSubmissionError,
  ReviewFact,
} from '@/features/applications/components/OriginationSupport'
import { validateWholeVnd } from '@/features/applications/origination-validation'
import { collateralTypes, type CollateralType } from '@/features/collateral-loan/collateral-loan-api'
import { useSubmitCollateralLoanMutation } from '@/features/collateral-loan/collateral-loan-queries'
import { useLoanProductQuery } from '@/features/loan-products/loan-product-queries'
import { AmountInput } from '@/features/salary-advance/components/AmountInput'

interface FormValues {
  requestedAmount: string
  requestedTermMonths: string
  collateralType: string
  description: string
  estimatedValue: string
  ownershipStatus: string
  conditionNote: string
}

const collateralLabels: Record<CollateralType, string> = {
  MOTORBIKE: 'Motorbike', CAR: 'Car', ELECTRONICS: 'Electronics',
  PROPERTY_DOCUMENT: 'Property document', OTHER: 'Other',
}

const fieldOrder: (keyof FormValues)[] = [
  'requestedAmount', 'requestedTermMonths', 'collateralType', 'description',
  'estimatedValue', 'ownershipStatus', 'conditionNote',
]

export function CollateralLoanApplicationPage() {
  const productQuery = useLoanProductQuery('COLLATERAL_LOAN')
  const submission = useSubmitCollateralLoanMutation()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<unknown>()
  const [submissionSucceeded, setSubmissionSucceeded] = useState(false)
  const { control, getValues, handleSubmit, register, reset, formState: { errors, isDirty } } = useForm<FormValues>({
    defaultValues: { requestedAmount: '', requestedTermMonths: '', collateralType: '', description: '', estimatedValue: '', ownershipStatus: '', conditionNote: '' },
  })
  const values = useWatch({ control })
  const meaningfulInput = Object.values(values).some(Boolean)
  const shouldWarn = isDirty && meaningfulInput && !submissionSucceeded
  const blocker = useBlocker(({ currentLocation, nextLocation }) => shouldWarn && currentLocation.pathname !== nextLocation.pathname)
  const reviewRequested = searchParams.get('step') === 'review'
  const hasReviewValues = fieldOrder.every((name) => Boolean(getValues(name)))
  const stage = reviewRequested && hasReviewValues ? 'review' : 'request'

  useEffect(() => {
    if (reviewRequested && !hasReviewValues) setSearchParams({}, { replace: true })
  }, [hasReviewValues, reviewRequested, setSearchParams])
  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!shouldWarn) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', beforeUnload)
    return () => window.removeEventListener('beforeunload', beforeUnload)
  }, [shouldWarn])
  useEffect(() => {
    requestAnimationFrame(() => document.getElementById('application-stage-heading')?.focus())
  }, [stage])

  const backToProduct = <Button variant="secondary" asChild><Link to="/products/collateral-loan"><ArrowLeft aria-hidden="true" />Back to product</Link></Button>
  if (productQuery.isPending) {
    return <FocusedFlowLayout eyebrow="Collateral Loan application" title="Prepare your request" description="Meridian is loading the current product policy." currentStep={1} totalSteps={2} backAction={backToProduct} continueAction={<span />}><div className="space-y-5" role="status" aria-label="Loading Collateral Loan application"><Skeleton className="h-80" /><Skeleton className="h-72" /></div></FocusedFlowLayout>
  }
  if (productQuery.isError) {
    return <FocusedFlowLayout eyebrow="Collateral Loan application" title="Application details unavailable" description="The current product policy must be available before Customer Web can prepare a request." currentStep={1} totalSteps={2} backAction={backToProduct} continueAction={<Button onClick={() => void productQuery.refetch()}>Try again</Button>}><QueryErrorFeedback error={productQuery.error} title="Collateral Loan policy could not be loaded" onRetry={() => void productQuery.refetch()} /></FocusedFlowLayout>
  }
  const product = productQuery.data
  const usablePolicy = product.active && Number.isSafeInteger(product.minAmount) && Number.isSafeInteger(product.maxAmount) && product.minAmount > 0 && product.maxAmount >= product.minAmount && product.policy.allowedTermsMonths.length > 0
  if (!usablePolicy) {
    return <FocusedFlowLayout eyebrow="Collateral Loan application" title="Application cannot be started" description="Meridian must return an active product with usable amount constraints and at least one allowed term." currentStep={1} totalSteps={2} backAction={backToProduct} continueAction={<span />}><Alert variant="warning"><Info aria-hidden="true" /><AlertTitle>Product policy is unavailable for this form</AlertTitle><AlertDescription>Customer Web will not invent amount limits or terms.</AlertDescription></Alert></FocusedFlowLayout>
  }

  const requiredText = (label: string, max: number) => (value: string) => {
    if (!value.trim()) return `Enter ${label.toLowerCase()}.`
    return value.length <= max || `${label} must be ${max} characters or fewer.`
  }
  const focusFirstError = () => {
    setTimeout(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(), 50)
  }
  const goToReview = handleSubmit(() => {
    setServerError(undefined)
    setSearchParams({ step: 'review' })
  }, focusFirstError)
  const submit = handleSubmit(async (form) => {
    if (submission.isPending) return
    setServerError(undefined)
    try {
      const application = await submission.submit({
        requestedAmount: Number(form.requestedAmount),
        requestedTermMonths: Number(form.requestedTermMonths),
        collateral: {
          type: form.collateralType as CollateralType,
          description: form.description,
          estimatedValue: Number(form.estimatedValue),
          ownershipStatus: form.ownershipStatus,
          conditionNote: form.conditionNote,
        },
      })
      setSubmissionSucceeded(true)
      reset(form)
      setTimeout(() => navigate(`/applications/${application.loanApplicationId}/documents`, {
          replace: true,
          state: { submission: { applicationNumber: application.applicationNumber, status: application.status } },
        }), 0)
    } catch (error) {
      setServerError(error)
      requestAnimationFrame(() => document.querySelector<HTMLElement>('[data-submission-error]')?.focus())
    }
  }, focusFirstError)
  const validationMessages = fieldOrder.map((name) => errors[name]?.message).filter((message): message is string => typeof message === 'string')
  const review = getValues()

  return (
    <>
      <FocusedFlowLayout
        eyebrow="Collateral Loan application"
        title={stage === 'request' ? 'Describe your request' : 'Review your application'}
        description={stage === 'request' ? 'Enter the requested terms and one set of structured collateral facts for manual assessment.' : 'Confirm every entered fact and the product-derived evidence requirements before submission.'}
        currentStep={stage === 'request' ? 1 : 2}
        totalSteps={2}
        backAction={stage === 'request' ? backToProduct : <Button variant="secondary" onClick={() => setSearchParams({})}><ArrowLeft aria-hidden="true" />Back to request</Button>}
        continueAction={stage === 'request' ? <Button type="submit" form="collateral-form">Review request<ArrowRight aria-hidden="true" /></Button> : <Button type="submit" form="collateral-form" disabled={submission.isPending}>{submission.isPending ? <Spinner /> : null}{submission.isPending ? 'Submitting…' : 'Submit application'}</Button>}
      >
        <form id="collateral-form" noValidate className="space-y-6" onSubmit={stage === 'request' ? goToReview : submit}>
          <h2 id="application-stage-heading" tabIndex={-1} className="sr-only outline-none">{stage === 'request' ? 'Request and collateral details' : 'Application review'}</h2>
          {validationMessages.length > 1 ? <Alert variant="destructive"><ShieldAlert aria-hidden="true" /><AlertTitle>Check the application details</AlertTitle><AlertDescription>{validationMessages.join(' ')}</AlertDescription></Alert> : null}
          {serverError ? <OriginationSubmissionError error={serverError} /> : null}
          {stage === 'request' ? (
            <>
              <Card>
                <CardHeader><CardTitle>Request details</CardTitle><CardDescription>Amount limits and terms come from the current product policy.</CardDescription></CardHeader>
                <CardContent className="space-y-5">
                  <AccountFormField htmlFor="requestedAmount" label="Requested amount" required description="Positive whole VND within the current product minimum and maximum." error={errors.requestedAmount?.message}><Controller name="requestedAmount" control={control} rules={{ validate: (value) => validateWholeVnd(value, product.minAmount, product.maxAmount) }} render={({ field }) => <AmountInput field={field} invalid={Boolean(errors.requestedAmount)} describedBy={`requestedAmount-description${errors.requestedAmount ? ' requestedAmount-error' : ''}`} />} /></AccountFormField>
                  <AccountFormField htmlFor="requestedTermMonths" label="Requested term" required description="Terms come directly from the current product policy." error={errors.requestedTermMonths?.message}><select id="requestedTermMonths" className="flex min-h-11 w-full rounded-md border border-input bg-card px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/20" aria-invalid={Boolean(errors.requestedTermMonths)} aria-describedby={`requestedTermMonths-description${errors.requestedTermMonths ? ' requestedTermMonths-error' : ''}`} {...register('requestedTermMonths', { validate: (value) => product.policy.allowedTermsMonths.includes(Number(value)) || 'Select a term returned by the current product policy.' })}><option value="">Select a term</option>{product.policy.allowedTermsMonths.map((value) => <option key={value} value={value}>{value} {value === 1 ? 'month' : 'months'}</option>)}</select></AccountFormField>
                </CardContent>
              </Card>
              <Card>
                <CardHeader><CardTitle>Collateral facts</CardTitle><CardDescription>Meridian assesses these facts manually. Customer Web does not calculate loan-to-value or approval.</CardDescription></CardHeader>
                <CardContent className="space-y-5">
                  <AccountFormField htmlFor="collateralType" label="Collateral type" required error={errors.collateralType?.message}><select id="collateralType" className="flex min-h-11 w-full rounded-md border border-input bg-card px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/20" aria-invalid={Boolean(errors.collateralType)} aria-describedby={errors.collateralType ? 'collateralType-error' : undefined} {...register('collateralType', { validate: (value) => collateralTypes.includes(value as CollateralType) || 'Select a supported collateral type.' })}><option value="">Select a collateral type</option>{collateralTypes.map((value) => <option key={value} value={value}>{collateralLabels[value]}</option>)}</select></AccountFormField>
                  <TextAreaField name="description" label="Description" maxLength={500} control={register('description', { validate: requiredText('Description', 500) })} error={errors.description?.message} />
                  <AccountFormField htmlFor="estimatedValue" label="Estimated value" required description="Positive whole VND for manual assessment only; this is not an automated LTV calculation." error={errors.estimatedValue?.message}><Controller name="estimatedValue" control={control} rules={{ validate: (value) => validateWholeVnd(value) }} render={({ field }) => <AmountInput field={field} invalid={Boolean(errors.estimatedValue)} describedBy={`estimatedValue-description${errors.estimatedValue ? ' estimatedValue-error' : ''}`} />} /></AccountFormField>
                  <TextAreaField name="ownershipStatus" label="Ownership status" maxLength={200} description="Describe the current ownership status in your own words." control={register('ownershipStatus', { validate: requiredText('Ownership status', 200) })} error={errors.ownershipStatus?.message} />
                  <TextAreaField name="conditionNote" label="Condition note" maxLength={500} control={register('conditionNote', { validate: requiredText('Condition note', 500) })} error={errors.conditionNote?.message} />
                </CardContent>
              </Card>
            </>
          ) : (
            <Card>
              <CardHeader><CardTitle>Confirm your request</CardTitle><CardDescription>Review the exact facts that will be submitted. No financial result is calculated here.</CardDescription></CardHeader>
              <CardContent><dl className="grid gap-4 sm:grid-cols-2"><ReviewFact label="Requested amount"><MoneyDisplay value={Number(review.requestedAmount)} /></ReviewFact><ReviewFact label="Requested term">{review.requestedTermMonths} months</ReviewFact><ReviewFact label="Collateral type">{collateralLabels[review.collateralType as CollateralType] ?? 'Type unavailable'}</ReviewFact><ReviewFact label="Estimated value"><MoneyDisplay value={Number(review.estimatedValue)} /></ReviewFact><ReviewFact label="Description">{review.description}</ReviewFact><ReviewFact label="Ownership status">{review.ownershipStatus}</ReviewFact><ReviewFact label="Condition note">{review.conditionNote}</ReviewFact></dl></CardContent>
            </Card>
          )}
          <EvidenceRequirements requirements={product.policy.submissionEvidenceRequirements} />
        </form>
      </FocusedFlowLayout>
      <OriginationExitWarning blocker={blocker} productName="Collateral Loan" />
    </>
  )
}

function TextAreaField({ name, label, maxLength, description, control, error }: { name: string; label: string; maxLength: number; description?: string; control: UseFormRegisterReturn; error?: string }) {
  return <AccountFormField htmlFor={name} label={label} required description={description ?? `Maximum ${maxLength} characters.`} error={error}><textarea id={name} rows={4} maxLength={maxLength} className="flex min-h-24 w-full resize-y rounded-md border border-input bg-card px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/20" aria-invalid={Boolean(error)} aria-describedby={`${name}-description${error ? ` ${name}-error` : ''}`} {...control} /></AccountFormField>
}
