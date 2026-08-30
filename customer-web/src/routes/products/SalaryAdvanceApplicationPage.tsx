import { ArrowLeft, ArrowRight, CheckCircle2, Info, ShieldAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch, type FieldErrors } from 'react-hook-form'
import {
  Link,
  useBlocker,
  useNavigate,
  useSearchParams,
} from 'react-router-dom'

import { MoneyDisplay } from '@/components/common/MoneyDisplay'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { StatusBadge } from '@/components/common/StatusBadge'
import { FocusedFlowLayout } from '@/components/layout/FocusedFlowLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { AccountFormField } from '@/features/account/components/AccountFormField'
import { useLoanProductQuery } from '@/features/loan-products/loan-product-queries'
import type { SalaryAdvanceApplication } from '@/features/salary-advance/salary-advance-api'
import { AmountInput } from '@/features/salary-advance/components/AmountInput'
import { SalaryAdvanceReadiness } from '@/features/salary-advance/components/SalaryAdvanceReadiness'
import {
  useSalaryAdvanceReadinessQuery,
  useSubmitSalaryAdvanceMutation,
} from '@/features/salary-advance/salary-advance-queries'
import { applicationStatusPresentation } from '@/features/salary-advance/salary-advance-presentation'
import { ApiError } from '@/lib/api'

interface SalaryAdvanceFormValues {
  requestedAmount: string
  requestedTermMonths: string
}

const submissionErrorMessages: Record<string, string> = {
  CUSTOMER_NOT_FOUND: 'Meridian could not confirm the Customer account for this application.',
  PRODUCT_NOT_FOUND: 'The Salary Advance product is no longer available.',
  CUSTOMER_NOT_ACTIVE: 'The Customer account is no longer active for Salary Advance submission.',
  PROFILE_INCOMPLETE: 'Your current profile no longer satisfies Salary Advance submission readiness.',
  PRIMARY_BANK_ACCOUNT_REQUIRED: 'An active primary bank account is now required before submission.',
  PRODUCT_INACTIVE: 'Salary Advance is no longer active for new applications.',
  PRODUCT_POLICY_INVALID: 'The current Salary Advance policy cannot accept this application.',
  INVALID_PRODUCT_AMOUNT: 'The requested amount no longer satisfies the current product policy.',
  INVALID_PRODUCT_TERM: 'The requested term is no longer allowed by the current product policy.',
  EMPLOYEE_NOT_VERIFIED: 'Current employment verification is required before submission.',
  SALARY_ADVANCE_ELIGIBILITY_DATA_STALE: 'Partner employment evidence changed and must be refreshed before submission.',
  SALARY_ADVANCE_LIMIT_UNAVAILABLE: 'Meridian can no longer confirm a usable Salary Advance limit.',
  INSUFFICIENT_AVAILABLE_LIMIT: 'The current available amount is no longer sufficient for this request.',
  BLOCKING_APPLICATION_EXISTS: 'Another Salary Advance application now blocks this submission.',
  OUTSTANDING_LOAN_ACCOUNT_EXISTS: 'A prior Salary Advance balance now blocks this submission.',
  SYSTEM_STATE_CONFLICT: 'Meridian could not safely reconcile the current Salary Advance state.',
  VALIDATION_FAILED: 'Meridian could not validate the submitted request. Review the entered amount and term before trying again.',
}

const submissionErrorActions: Record<string, { label: string; to: string }> = {
  PROFILE_INCOMPLETE: { label: 'Open profile', to: '/account/profile' },
  PRIMARY_BANK_ACCOUNT_REQUIRED: { label: 'Open bank accounts', to: '/account/bank-accounts' },
  EMPLOYEE_NOT_VERIFIED: { label: 'Return to employment verification', to: '/products/salary-advance' },
  SALARY_ADVANCE_ELIGIBILITY_DATA_STALE: { label: 'Return to employment verification', to: '/products/salary-advance' },
}

function SubmissionError({ error }: { error: unknown }) {
  const knownMessage = error instanceof ApiError ? submissionErrorMessages[error.errorCode] : undefined
  const action = error instanceof ApiError ? submissionErrorActions[error.errorCode] : undefined
  return (
    <Alert variant="destructive" tabIndex={-1} data-submission-error>
      <ShieldAlert aria-hidden="true" />
      <AlertTitle>Application was not submitted</AlertTitle>
      <AlertDescription className="space-y-3">
        <p>{knownMessage ?? (error instanceof ApiError
          ? error.message
          : 'The request could not be completed. Check your connection and try again.')}</p>
        <p>Meridian has refreshed the affected current state where appropriate. Review it and submit again only when you are ready.</p>
        {error instanceof ApiError && error.requestId ? (
          <p className="break-all text-xs">Support reference: {error.requestId}</p>
        ) : null}
        {action ? (
          <Button variant="secondary" size="sm" asChild>
            <Link to={action.to}>{action.label}<ArrowRight aria-hidden="true" /></Link>
          </Button>
        ) : null}
      </AlertDescription>
    </Alert>
  )
}

function ExitWarning({ blocker }: { blocker: ReturnType<typeof useBlocker> }) {
  const blocked = blocker.state === 'blocked'
  return (
    <Dialog
      open={blocked}
      onOpenChange={(open) => {
        if (!open && blocker.state === 'blocked') blocker.reset()
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Leave this application?</DialogTitle>
          <DialogDescription>
            Salary Advance does not have a saved draft. The amount and term entered in this browser will be lost.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="secondary" onClick={() => blocker.state === 'blocked' && blocker.reset()}>
            Stay here
          </Button>
          <Button variant="destructive" onClick={() => blocker.state === 'blocked' && blocker.proceed()}>
            Leave application
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function SuccessState({ application }: { application: SalaryAdvanceApplication }) {
  return (
    <FocusedFlowLayout
      eyebrow="Salary Advance application"
      title="Application submitted"
      description="Meridian recorded the application and reserved the approved current exposure under its authoritative submission checks."
      currentStep={2}
      totalSteps={2}
      backAction={<span />}
      continueAction={<Button asChild><Link to="/">Return to Dashboard<ArrowRight aria-hidden="true" /></Link></Button>}
    >
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle className="break-words">{application.applicationNumber}</CardTitle>
              <CardDescription className="mt-1">Keep this application number for your records.</CardDescription>
            </div>
            <StatusBadge presentation={applicationStatusPresentation(application.status)} />
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          <Alert variant="success" aria-live="polite">
            <CheckCircle2 aria-hidden="true" />
            <AlertTitle>Submission confirmed</AlertTitle>
            <AlertDescription>Your Dashboard can now reflect this active application from Meridian's application index.</AlertDescription>
          </Alert>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-md border border-border bg-background p-4">
              <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">Requested amount</dt>
              <dd className="mt-2 text-lg"><MoneyDisplay value={application.requestedAmount} /></dd>
            </div>
            <div className="rounded-md border border-border bg-background p-4">
              <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">Requested term</dt>
              <dd className="mt-2 text-lg font-semibold">{application.requestedTermMonths} {application.requestedTermMonths === 1 ? 'month' : 'months'}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </FocusedFlowLayout>
  )
}

export function SalaryAdvanceApplicationPage() {
  const productQuery = useLoanProductQuery('SALARY_ADVANCE')
  const readinessQuery = useSalaryAdvanceReadinessQuery()
  const submission = useSubmitSalaryAdvanceMutation()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<unknown>()
  const [success, setSuccess] = useState<SalaryAdvanceApplication>()
  const {
    control,
    getValues,
    handleSubmit,
    register,
    reset,
    setFocus,
    formState: { errors, isDirty },
  } = useForm<SalaryAdvanceFormValues>({
    defaultValues: { requestedAmount: '', requestedTermMonths: '' },
  })

  const amountValue = useWatch({ control, name: 'requestedAmount' })
  const termValue = useWatch({ control, name: 'requestedTermMonths' })
  const meaningfulInput = Boolean(amountValue || termValue)
  const shouldWarnOnExit = isDirty && meaningfulInput && !success
  const blocker = useBlocker(({ currentLocation, nextLocation }) => (
    shouldWarnOnExit && currentLocation.pathname !== nextLocation.pathname
  ))
  const reviewRequested = searchParams.get('step') === 'review'
  const hasReviewValues = Boolean(amountValue && termValue)
  const stage = reviewRequested && hasReviewValues ? 'review' : 'request'

  useEffect(() => {
    if (reviewRequested && !hasReviewValues) {
      setSearchParams({}, { replace: true })
    }
  }, [hasReviewValues, reviewRequested, setSearchParams])

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!shouldWarnOnExit) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [shouldWarnOnExit])

  useEffect(() => {
    requestAnimationFrame(() => document.getElementById('application-stage-heading')?.focus())
  }, [stage])

  if (success) {
    return <SuccessState application={success} />
  }

  const backToProduct = (
    <Button variant="secondary" asChild>
      <Link to="/products/salary-advance"><ArrowLeft aria-hidden="true" />Back to product</Link>
    </Button>
  )

  if (productQuery.isPending || readinessQuery.isPending) {
    return (
      <FocusedFlowLayout
        eyebrow="Salary Advance application"
        title="Prepare your request"
        description="Meridian is loading the current product policy and Salary Advance readiness."
        currentStep={1}
        totalSteps={2}
        backAction={backToProduct}
        continueAction={<span />}
      >
        <div className="space-y-5" role="status" aria-label="Loading Salary Advance application">
          <Skeleton className="h-72 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
      </FocusedFlowLayout>
    )
  }

  if (productQuery.isError || readinessQuery.isError) {
    return (
      <FocusedFlowLayout
        eyebrow="Salary Advance application"
        title="Application details unavailable"
        description="The current product policy and readiness must both be available before Customer Web can prepare a request."
        currentStep={1}
        totalSteps={2}
        backAction={backToProduct}
        continueAction={<Button onClick={() => void Promise.all([productQuery.refetch(), readinessQuery.refetch()])}>Try again</Button>}
      >
        <div className="space-y-5">
          {productQuery.isError ? (
            <QueryErrorFeedback error={productQuery.error} title="Salary Advance policy could not be loaded" onRetry={() => void productQuery.refetch()} />
          ) : null}
          {readinessQuery.isError ? (
            <QueryErrorFeedback error={readinessQuery.error} title="Salary Advance readiness could not be loaded" onRetry={() => void readinessQuery.refetch()} />
          ) : null}
          {readinessQuery.data ? <SalaryAdvanceReadiness readiness={readinessQuery.data} showApplyAction={false} /> : null}
        </div>
      </FocusedFlowLayout>
    )
  }

  const product = productQuery.data
  const readiness = readinessQuery.data
  const usableAmountFacts = [product.minAmount, product.maxAmount, readiness.availableAmount]
    .every((value) => Number.isSafeInteger(value) && value >= 0)
  const allowedTerms = product.policy.allowedTermsMonths
  const canUseForm = readiness.applicationAllowed
    && Boolean(readiness.customerPartnerEmployeeLinkId)
    && usableAmountFacts
    && allowedTerms.length > 0

  if (!canUseForm && serverError) {
    const retainedAmount = Number(getValues('requestedAmount'))
    const retainedTerm = Number(getValues('requestedTermMonths'))
    return (
      <>
        <FocusedFlowLayout
          eyebrow="Salary Advance application"
          title="Application state changed"
          description="Meridian rejected the submission after re-checking authoritative state. Your request remains in browser memory for a deliberate retry if readiness becomes available again."
          currentStep={2}
          totalSteps={2}
          backAction={backToProduct}
          continueAction={<Button variant="secondary" onClick={() => void readinessQuery.refetch()}>Refresh readiness</Button>}
        >
          <div className="space-y-6">
            <SubmissionError error={serverError} />
            <Card>
              <CardHeader>
                <CardTitle>Retained request</CardTitle>
                <CardDescription>Customer Web has not changed or resubmitted these values.</CardDescription>
              </CardHeader>
              <CardContent>
                <dl className="grid gap-4 sm:grid-cols-2">
                  <div className="rounded-md border border-border bg-background p-4">
                    <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">Requested amount</dt>
                    <dd className="mt-2 text-lg"><MoneyDisplay value={retainedAmount} /></dd>
                  </div>
                  <div className="rounded-md border border-border bg-background p-4">
                    <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">Requested term</dt>
                    <dd className="mt-2 text-lg font-semibold">{retainedTerm} {retainedTerm === 1 ? 'month' : 'months'}</dd>
                  </div>
                </dl>
              </CardContent>
            </Card>
            <SalaryAdvanceReadiness readiness={readiness} showApplyAction={false} />
          </div>
        </FocusedFlowLayout>
        <ExitWarning blocker={blocker} />
      </>
    )
  }

  if (!canUseForm) {
    return (
      <FocusedFlowLayout
        eyebrow="Salary Advance application"
        title="Application cannot be started"
        description="Review the current authoritative readiness and policy state before trying to apply."
        currentStep={1}
        totalSteps={2}
        backAction={backToProduct}
        continueAction={<Button variant="secondary" onClick={() => void readinessQuery.refetch()}>Refresh readiness</Button>}
      >
        <div className="space-y-6">
          {!usableAmountFacts || allowedTerms.length === 0 ? (
            <Alert variant="warning">
              <Info aria-hidden="true" />
              <AlertTitle>Product policy is unavailable for this form</AlertTitle>
              <AlertDescription>Meridian must return usable whole-VND amount constraints and at least one allowed term. Customer Web will not invent defaults.</AlertDescription>
            </Alert>
          ) : null}
          <SalaryAdvanceReadiness readiness={readiness} showApplyAction={false} />
        </div>
      </FocusedFlowLayout>
    )
  }

  const validateAmount = (value: string) => {
    if (!value) return 'Enter a requested amount.'
    if (!/^\d+$/.test(value)) return 'Enter a positive whole-VND amount using digits only.'
    const amount = BigInt(value)
    if (amount <= 0n) return 'Requested amount must be greater than zero.'
    if (amount > BigInt(Number.MAX_SAFE_INTEGER)) return 'Requested amount is too large to submit safely.'
    if (amount < BigInt(product.minAmount)) return 'Requested amount is below the current product minimum.'
    if (amount > BigInt(product.maxAmount)) return 'Requested amount is above the current product maximum.'
    if (amount > BigInt(readiness.availableAmount)) return 'Requested amount exceeds the currently available Salary Advance amount.'
    return true
  }

  const validateTerm = (value: string) => (
    allowedTerms.includes(Number(value)) || 'Select a term returned by the current product policy.'
  )

  const focusFirstError = (fieldErrors: FieldErrors<SalaryAdvanceFormValues>) => {
    const first = (['requestedAmount', 'requestedTermMonths'] as const).find((field) => fieldErrors[field])
    if (first) setFocus(first)
  }

  const goToReview = handleSubmit(() => {
    setServerError(undefined)
    setSearchParams({ step: 'review' })
  }, focusFirstError)

  const submitApplication = handleSubmit(async (values) => {
    if (submission.isPending || !readiness.customerPartnerEmployeeLinkId) return
    setServerError(undefined)
    try {
      const application = await submission.submit({
        customerPartnerEmployeeLinkId: readiness.customerPartnerEmployeeLinkId,
        requestedAmount: Number(values.requestedAmount),
        requestedTermMonths: Number(values.requestedTermMonths),
      })
      reset(values)
      setSuccess(application)
    } catch (error) {
      setServerError(error)
      requestAnimationFrame(() => document.querySelector<HTMLElement>('[data-submission-error]')?.focus())
    }
  }, focusFirstError)

  const validationMessages = [errors.requestedAmount?.message, errors.requestedTermMonths?.message]
    .filter((message): message is string => typeof message === 'string')
  const reviewAmount = Number(getValues('requestedAmount'))
  const reviewTerm = Number(getValues('requestedTermMonths'))

  return (
    <>
      <FocusedFlowLayout
        eyebrow="Salary Advance application"
        title={stage === 'request' ? 'Choose your request' : 'Review your application'}
        description={stage === 'request'
          ? 'Enter a whole-VND amount and select a term from Meridian’s current Salary Advance policy.'
          : 'Confirm the request below. Meridian will re-check readiness, eligibility, limits, and competing state when you submit.'}
        currentStep={stage === 'request' ? 1 : 2}
        totalSteps={2}
        backAction={stage === 'request' ? backToProduct : (
          <Button variant="secondary" onClick={() => navigate(-1)}><ArrowLeft aria-hidden="true" />Back to request</Button>
        )}
        continueAction={stage === 'request' ? (
          <Button type="submit" form="salary-advance-form">Review request<ArrowRight aria-hidden="true" /></Button>
        ) : (
          <Button type="submit" form="salary-advance-form" disabled={submission.isPending}>
            {submission.isPending ? <Spinner /> : null}
            {submission.isPending ? 'Submitting…' : 'Submit application'}
          </Button>
        )}
      >
        <form
          id="salary-advance-form"
          noValidate
          className="space-y-6"
          onSubmit={stage === 'request' ? goToReview : submitApplication}
        >
          <h2 id="application-stage-heading" tabIndex={-1} className="sr-only outline-none">
            {stage === 'request' ? 'Request details' : 'Application review'}
          </h2>
          {validationMessages.length > 1 ? (
            <Alert variant="destructive">
              <ShieldAlert aria-hidden="true" />
              <AlertTitle>Check the application details</AlertTitle>
              <AlertDescription>{validationMessages.join(' ')}</AlertDescription>
            </Alert>
          ) : null}
          {serverError ? <SubmissionError error={serverError} /> : null}

          {stage === 'request' ? (
            <>
              <Card>
                <CardHeader>
                  <CardTitle>Request details</CardTitle>
                  <CardDescription>Customer Web validates the returned constraints for immediate feedback. Meridian remains authoritative at submission.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                  <AccountFormField
                    htmlFor="requestedAmount"
                    label="Requested amount"
                    required
                    description="Enter a positive whole-VND amount. Product minimum, product maximum, and current available amount are checked independently."
                    error={errors.requestedAmount?.message}
                  >
                    <Controller
                      name="requestedAmount"
                      control={control}
                      rules={{ validate: validateAmount }}
                      render={({ field }) => (
                        <AmountInput
                          field={field}
                          invalid={Boolean(errors.requestedAmount)}
                          describedBy={`requestedAmount-description${errors.requestedAmount ? ' requestedAmount-error' : ''}`}
                        />
                      )}
                    />
                  </AccountFormField>
                  <AccountFormField
                    htmlFor="requestedTermMonths"
                    label="Requested term"
                    required
                    description="Terms come directly from the current Salary Advance product policy."
                    error={errors.requestedTermMonths?.message}
                  >
                    <select
                      id="requestedTermMonths"
                      className="flex min-h-11 w-full rounded-md border border-input bg-card px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/20"
                      aria-invalid={Boolean(errors.requestedTermMonths)}
                      aria-describedby={`requestedTermMonths-description${errors.requestedTermMonths ? ' requestedTermMonths-error' : ''}`}
                      {...register('requestedTermMonths', { validate: validateTerm })}
                    >
                      <option value="">Select a term</option>
                      {allowedTerms.map((term) => (
                        <option key={term} value={term}>{term} {term === 1 ? 'month' : 'months'}</option>
                      ))}
                    </select>
                  </AccountFormField>
                </CardContent>
              </Card>
              <SalaryAdvanceReadiness readiness={readiness} showApplyAction={false} showVerification={false} />
            </>
          ) : (
            <>
              <Card>
                <CardHeader>
                  <CardTitle>Confirm your request</CardTitle>
                  <CardDescription>No interest, repayment, limit, or eligibility calculation is performed in Customer Web.</CardDescription>
                </CardHeader>
                <CardContent>
                  <dl className="grid gap-4 sm:grid-cols-2">
                    <div className="rounded-md border border-border bg-background p-4">
                      <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">Requested amount</dt>
                      <dd className="mt-2 text-lg"><MoneyDisplay value={reviewAmount} /></dd>
                    </div>
                    <div className="rounded-md border border-border bg-background p-4">
                      <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">Requested term</dt>
                      <dd className="mt-2 text-lg font-semibold">{reviewTerm} {reviewTerm === 1 ? 'month' : 'months'}</dd>
                    </div>
                  </dl>
                </CardContent>
              </Card>
              <SalaryAdvanceReadiness readiness={readiness} showApplyAction={false} showVerification={false} />
            </>
          )}
        </form>
      </FocusedFlowLayout>
      <ExitWarning blocker={blocker} />
    </>
  )
}
