import { ArrowLeft, ArrowRight, Info, ShieldAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
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
  EvidenceRequirements,
  OriginationExitWarning,
  OriginationSubmissionError,
  ReviewFact,
} from '@/features/applications/components/OriginationSupport'
import { validateWholeVnd } from '@/features/applications/origination-validation'
import { useLoanProductQuery } from '@/features/loan-products/loan-product-queries'
import { AmountInput } from '@/features/salary-advance/components/AmountInput'
import { useSubmitUnsecuredConsumerLoanMutation } from '@/features/unsecured-consumer-loan/unsecured-consumer-loan-queries'

interface FormValues {
  requestedAmount: string
  requestedTermMonths: string
}

export function UnsecuredConsumerLoanApplicationPage() {
  const productQuery = useLoanProductQuery('UNSECURED_CONSUMER_LOAN')
  const submission = useSubmitUnsecuredConsumerLoanMutation()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<unknown>()
  const [submissionSucceeded, setSubmissionSucceeded] = useState(false)
  const { control, getValues, handleSubmit, register, reset, formState: { errors, isDirty } } = useForm<FormValues>({
    defaultValues: { requestedAmount: '', requestedTermMonths: '' },
  })
  const amount = useWatch({ control, name: 'requestedAmount' })
  const term = useWatch({ control, name: 'requestedTermMonths' })
  const shouldWarn = isDirty && Boolean(amount || term) && !submissionSucceeded
  const blocker = useBlocker(({ currentLocation, nextLocation }) => (
    shouldWarn && currentLocation.pathname !== nextLocation.pathname
  ))
  const reviewRequested = searchParams.get('step') === 'review'
  const stage = reviewRequested && amount && term ? 'review' : 'request'

  useEffect(() => {
    if (reviewRequested && (!amount || !term)) setSearchParams({}, { replace: true })
  }, [amount, reviewRequested, setSearchParams, term])
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

  const backToProduct = <Button variant="secondary" asChild><Link to="/products/unsecured-consumer-loan"><ArrowLeft aria-hidden="true" />Back to product</Link></Button>

  if (productQuery.isPending) {
    return (
      <FocusedFlowLayout eyebrow="Unsecured Consumer Loan application" title="Prepare your request" description="Meridian is loading the current product policy." currentStep={1} totalSteps={2} backAction={backToProduct} continueAction={<span />}>
        <div className="space-y-5" role="status" aria-label="Loading Unsecured Consumer Loan application"><Skeleton className="h-72" /><Skeleton className="h-48" /></div>
      </FocusedFlowLayout>
    )
  }
  if (productQuery.isError) {
    return (
      <FocusedFlowLayout eyebrow="Unsecured Consumer Loan application" title="Application details unavailable" description="The current product policy must be available before Customer Web can prepare a request." currentStep={1} totalSteps={2} backAction={backToProduct} continueAction={<Button onClick={() => void productQuery.refetch()}>Try again</Button>}>
        <QueryErrorFeedback error={productQuery.error} title="Unsecured Consumer Loan policy could not be loaded" onRetry={() => void productQuery.refetch()} />
      </FocusedFlowLayout>
    )
  }

  const product = productQuery.data
  const usablePolicy = product.active
    && Number.isSafeInteger(product.minAmount) && Number.isSafeInteger(product.maxAmount)
    && product.minAmount > 0 && product.maxAmount >= product.minAmount
    && product.policy.allowedTermsMonths.length > 0
  if (!usablePolicy) {
    return (
      <FocusedFlowLayout eyebrow="Unsecured Consumer Loan application" title="Application cannot be started" description="Meridian must return an active product with usable amount constraints and at least one allowed term." currentStep={1} totalSteps={2} backAction={backToProduct} continueAction={<span />}>
        <Alert variant="warning"><Info aria-hidden="true" /><AlertTitle>Product policy is unavailable for this form</AlertTitle><AlertDescription>Customer Web will not invent amount limits or terms.</AlertDescription></Alert>
      </FocusedFlowLayout>
    )
  }

  const validateAmount = (value: string) => validateWholeVnd(value, product.minAmount, product.maxAmount)
  const validateTerm = (value: string) => product.policy.allowedTermsMonths.includes(Number(value)) || 'Select a term returned by the current product policy.'
  const focusFirstError = () => {
    setTimeout(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(), 50)
  }
  const goToReview = handleSubmit(() => {
    setServerError(undefined)
    setSearchParams({ step: 'review' })
  }, focusFirstError)
  const submit = handleSubmit(async (values) => {
    if (submission.isPending) return
    setServerError(undefined)
    try {
      const application = await submission.submit({
        requestedAmount: Number(values.requestedAmount),
        requestedTermMonths: Number(values.requestedTermMonths),
      })
      setSubmissionSucceeded(true)
      reset(values)
      setTimeout(() => navigate(`/applications/${application.loanApplicationId}/documents`, {
          replace: true,
          state: { submission: { applicationNumber: application.applicationNumber, status: application.status } },
        }), 0)
    } catch (error) {
      setServerError(error)
      requestAnimationFrame(() => document.querySelector<HTMLElement>('[data-submission-error]')?.focus())
    }
  }, focusFirstError)
  const validationMessages = [errors.requestedAmount?.message, errors.requestedTermMonths?.message].filter((value): value is string => typeof value === 'string')

  return (
    <>
      <FocusedFlowLayout
        eyebrow="Unsecured Consumer Loan application"
        title={stage === 'request' ? 'Choose your request' : 'Review your application'}
        description={stage === 'request' ? 'Enter a whole-VND amount and choose a term from Meridian’s current product policy.' : 'Confirm your request and product-derived evidence requirements before submission.'}
        currentStep={stage === 'request' ? 1 : 2}
        totalSteps={2}
        backAction={stage === 'request' ? backToProduct : <Button variant="secondary" onClick={() => setSearchParams({})}><ArrowLeft aria-hidden="true" />Back to request</Button>}
        continueAction={stage === 'request'
          ? <Button type="submit" form="ucl-form">Review request<ArrowRight aria-hidden="true" /></Button>
          : <Button type="submit" form="ucl-form" disabled={submission.isPending}>{submission.isPending ? <Spinner /> : null}{submission.isPending ? 'Submitting…' : 'Submit application'}</Button>}
      >
        <form id="ucl-form" noValidate className="space-y-6" onSubmit={stage === 'request' ? goToReview : submit}>
          <h2 id="application-stage-heading" tabIndex={-1} className="sr-only outline-none">{stage === 'request' ? 'Request details' : 'Application review'}</h2>
          {validationMessages.length > 1 ? <Alert variant="destructive"><ShieldAlert aria-hidden="true" /><AlertTitle>Check the application details</AlertTitle><AlertDescription>{validationMessages.join(' ')}</AlertDescription></Alert> : null}
          {serverError ? <OriginationSubmissionError error={serverError} /> : null}
          {stage === 'request' ? (
            <Card>
              <CardHeader><CardTitle>Request details</CardTitle><CardDescription>Immediate validation uses the returned policy; Meridian remains authoritative at submission.</CardDescription></CardHeader>
              <CardContent className="space-y-5">
                <AccountFormField htmlFor="requestedAmount" label="Requested amount" required description="Enter a positive whole-VND amount within the current product minimum and maximum." error={errors.requestedAmount?.message}>
                  <Controller name="requestedAmount" control={control} rules={{ validate: validateAmount }} render={({ field }) => <AmountInput field={field} invalid={Boolean(errors.requestedAmount)} describedBy={`requestedAmount-description${errors.requestedAmount ? ' requestedAmount-error' : ''}`} />} />
                </AccountFormField>
                <AccountFormField htmlFor="requestedTermMonths" label="Requested term" required description="Terms come directly from the current product policy." error={errors.requestedTermMonths?.message}>
                  <select id="requestedTermMonths" className="flex min-h-11 w-full rounded-md border border-input bg-card px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/20" aria-invalid={Boolean(errors.requestedTermMonths)} aria-describedby={`requestedTermMonths-description${errors.requestedTermMonths ? ' requestedTermMonths-error' : ''}`} {...register('requestedTermMonths', { validate: validateTerm })}>
                    <option value="">Select a term</option>
                    {product.policy.allowedTermsMonths.map((value) => <option key={value} value={value}>{value} {value === 1 ? 'month' : 'months'}</option>)}
                  </select>
                </AccountFormField>
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardHeader><CardTitle>Confirm your request</CardTitle><CardDescription>No lending eligibility or financial result is calculated in Customer Web.</CardDescription></CardHeader>
              <CardContent><dl className="grid gap-4 sm:grid-cols-2"><ReviewFact label="Requested amount"><MoneyDisplay value={Number(getValues('requestedAmount'))} /></ReviewFact><ReviewFact label="Requested term">{getValues('requestedTermMonths')} months</ReviewFact></dl></CardContent>
            </Card>
          )}
          <EvidenceRequirements requirements={product.policy.submissionEvidenceRequirements} />
        </form>
      </FocusedFlowLayout>
      <OriginationExitWarning blocker={blocker} productName="Unsecured Consumer Loan" />
    </>
  )
}
