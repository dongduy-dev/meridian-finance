import { Building2, CircleHelp } from 'lucide-react'
import { useState } from 'react'
import { useForm, type FieldErrors } from 'react-hook-form'

import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { AccountFormField } from '@/features/account/components/AccountFormField'
import { ApiError } from '@/lib/api'

import type { EmployeeVerification } from '../salary-advance-api'
import {
  usePartnerVerificationOptionsQuery,
  useVerifyEmployeeMutation,
} from '../salary-advance-queries'
import { verificationOutcomePresentation } from '../salary-advance-presentation'

interface VerificationFormValues {
  partnerCompanyId: string
  employeeCode: string
}

function VerificationError({ error }: { error: unknown }) {
  return (
    <Alert variant="destructive" tabIndex={-1} data-verification-error>
      <Building2 aria-hidden="true" />
      <AlertTitle>Employment verification was not completed</AlertTitle>
      <AlertDescription>
        <p>{error instanceof ApiError ? error.message : 'The request could not be completed. Check your connection and try again.'}</p>
        {error instanceof ApiError && error.requestId ? (
          <p className="mt-2 break-all text-xs">Support reference: {error.requestId}</p>
        ) : null}
      </AlertDescription>
    </Alert>
  )
}

function VerificationResult({ result }: { result: EmployeeVerification }) {
  const presentation = verificationOutcomePresentation(result.outcome, result.manualReviewRequired)
  const Icon = presentation.icon ?? CircleHelp
  const alertVariant = presentation.tone === 'success'
    ? 'success'
    : presentation.tone === 'danger'
      ? 'destructive'
      : presentation.tone === 'warning'
        ? 'warning'
        : 'information'
  return (
    <Alert variant={alertVariant} aria-live="polite">
      <Icon aria-hidden="true" />
      <AlertTitle className="flex flex-wrap items-center gap-2">
        Verification result
        <StatusBadge presentation={presentation} />
      </AlertTitle>
      <AlertDescription>{presentation.description}</AlertDescription>
    </Alert>
  )
}

export function EmployeeVerificationPanel({
  onCompleted,
  reverify = false,
}: {
  onCompleted?: () => void
  reverify?: boolean
}) {
  const optionsQuery = usePartnerVerificationOptionsQuery()
  const verification = useVerifyEmployeeMutation()
  const [result, setResult] = useState<EmployeeVerification>()
  const [serverError, setServerError] = useState<unknown>()
  const {
    register,
    handleSubmit,
    resetField,
    setFocus,
    formState: { errors },
  } = useForm<VerificationFormValues>({
    defaultValues: { partnerCompanyId: '', employeeCode: '' },
  })

  const onInvalid = (fieldErrors: FieldErrors<VerificationFormValues>) => {
    const first = (['partnerCompanyId', 'employeeCode'] as const).find((field) => fieldErrors[field])
    if (first) setFocus(first)
  }

  const onSubmit = handleSubmit(async (values) => {
    setServerError(undefined)
    setResult(undefined)
    try {
      const nextResult = await verification.submit({
        partnerCompanyId: values.partnerCompanyId,
        employeeCode: values.employeeCode.trim(),
      })
      resetField('employeeCode')
      setResult(nextResult)
      onCompleted?.()
    } catch (error) {
      setServerError(error)
      requestAnimationFrame(() => {
        document.querySelector<HTMLElement>('[data-verification-error]')?.focus()
      })
    }
  }, onInvalid)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{reverify ? 'Refresh employment verification' : 'Verify your employment'}</CardTitle>
        <CardDescription>
          Select your employer and enter your employee code. Meridian checks protected identity evidence on the server; Customer Web never asks for salary or identity matching data here.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {optionsQuery.isPending ? (
          <div role="status" aria-label="Loading Partner verification options" className="space-y-3">
            <Skeleton className="h-11 w-full" />
            <Skeleton className="h-11 w-full" />
          </div>
        ) : null}
        {optionsQuery.isError ? (
          <QueryErrorFeedback
            error={optionsQuery.error}
            title="Partner verification options could not be loaded"
            onRetry={() => void optionsQuery.refetch()}
          />
        ) : null}
        {optionsQuery.data?.length === 0 ? (
          <Alert>
            <Building2 aria-hidden="true" />
            <AlertTitle>No Partner Companies available</AlertTitle>
            <AlertDescription>Meridian is not currently returning a Partner Company that can be selected for employment verification.</AlertDescription>
          </Alert>
        ) : null}
        {optionsQuery.data?.length ? (
          <form noValidate className="space-y-5" onSubmit={onSubmit}>
            {serverError ? <VerificationError error={serverError} /> : null}
            {result ? <VerificationResult result={result} /> : null}
            <AccountFormField
              htmlFor="partnerCompanyId"
              label="Partner Company"
              required
              error={errors.partnerCompanyId?.message}
            >
              <select
                id="partnerCompanyId"
                className="flex min-h-11 w-full min-w-0 rounded-md border border-input bg-card px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/20"
                aria-invalid={Boolean(errors.partnerCompanyId)}
                aria-describedby={errors.partnerCompanyId ? 'partnerCompanyId-error' : undefined}
                {...register('partnerCompanyId', { required: 'Select your Partner Company.' })}
              >
                <option value="">Select a Partner Company</option>
                {optionsQuery.data.map((option) => (
                  <option key={option.partnerCompanyId} value={option.partnerCompanyId}>
                    {option.name} ({option.companyCode})
                  </option>
                ))}
              </select>
            </AccountFormField>
            <AccountFormField
              htmlFor="employeeCode"
              label="Employee code"
              required
              description="Used only for this verification request. It is not placed in the URL or retained after a completed attempt."
              error={errors.employeeCode?.message}
            >
              <Input
                id="employeeCode"
                autoComplete="off"
                aria-invalid={Boolean(errors.employeeCode)}
                aria-describedby={`employeeCode-description${errors.employeeCode ? ' employeeCode-error' : ''}`}
                {...register('employeeCode', {
                  validate: (value) => value.trim().length > 0 || 'Enter your employee code.',
                })}
              />
            </AccountFormField>
            <div className="flex justify-end">
              <Button type="submit" disabled={verification.isPending}>
                {verification.isPending ? <Spinner /> : null}
                {verification.isPending ? 'Checking employment…' : reverify ? 'Refresh verification' : 'Verify employment'}
              </Button>
            </div>
          </form>
        ) : null}
      </CardContent>
    </Card>
  )
}
