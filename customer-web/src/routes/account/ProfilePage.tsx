import { IdentityReferenceCorrection } from '@/features/account/components/IdentityReferenceCorrection'
import { useAuth } from '@/features/auth/auth-context'
import { ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm, type FieldErrors } from 'react-hook-form'

import { PageHeader } from '@/components/common/PageHeader'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { useOwnCustomerQuery, useUpdateProfileMutation } from '@/features/account/account-queries'
import { fieldDescriptionIds, focusAccountError } from '@/features/account/account-ui'
import { profileFieldSchemas, validateWith } from '@/features/account/account-validation'
import { AccountErrorFeedback, AccountSuccessFeedback } from '@/features/account/components/AccountFeedback'
import { AccountFormField, ConsentField } from '@/features/account/components/AccountFormField'
import { AccountNavigation } from '@/features/account/components/AccountNavigation'
import { AccountReadinessCard } from '@/features/account/components/AccountReadinessCard'

interface ProfileFormValues {
  fullName: string
  identityReference: string
  phoneNumber: string
  residentialAddress: string
  employmentStatus: string
  employerName: string
  termsConsentAccepted: boolean
  dataProcessingConsentAccepted: boolean
}

const emptyProfile: ProfileFormValues = {
  fullName: '',
  identityReference: '',
  phoneNumber: '',
  residentialAddress: '',
  employmentStatus: '',
  employerName: '',
  termsConsentAccepted: false,
  dataProcessingConsentAccepted: false,
}

function ProfileLoading() {
  return (
    <div className="space-y-5" role="status" aria-label="Loading your profile">
      <Skeleton className="h-36 w-full" />
      <Skeleton className="h-96 w-full" />
    </div>
  )
}

export function ProfilePage() {
  const { state: authState } = useAuth()
  const customerQuery = useOwnCustomerQuery()
  const updateProfile = useUpdateProfileMutation()
  const [serverError, setServerError] = useState<unknown>()
  const [saved, setSaved] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<ProfileFormValues>({ defaultValues: emptyProfile })

  const customer = customerQuery.data
  const profileComplete = customer?.profileCompletionStatus === 'COMPLETE'
  const identityVerified = customer?.verificationStatus === 'VERIFIED'

  useEffect(() => {
    if (!customer) return
    reset({
      ...emptyProfile,
      fullName: customer.profile?.fullName ?? '',
      phoneNumber: customer.profile?.phoneNumber ?? '',
      residentialAddress: customer.profile?.residentialAddress ?? '',
      employmentStatus: customer.profile?.employmentStatus ?? '',
      employerName: customer.profile?.employerName ?? '',
      termsConsentAccepted: customer.profile?.termsConsentAccepted ?? false,
      dataProcessingConsentAccepted: customer.profile?.dataProcessingConsentAccepted ?? false,
    })
  }, [customer, reset])

  const onInvalid = (fieldErrors: FieldErrors<ProfileFormValues>) => {
    const order: (keyof ProfileFormValues)[] = [
      'fullName',
      'identityReference',
      'phoneNumber',
      'residentialAddress',
      'employmentStatus',
      'employerName',
      'termsConsentAccepted',
      'dataProcessingConsentAccepted',
    ]
    const firstInvalid = order.find((field) => fieldErrors[field])
    if (firstInvalid) setFocus(firstInvalid)
  }

  const onSubmit = handleSubmit(async (values) => {
    setServerError(undefined)
    setSaved(false)
    try {
      await updateProfile.submit({
        ...(!identityVerified ? { fullName: values.fullName.trim() } : {}),
        phoneNumber: values.phoneNumber.trim(),
        residentialAddress: values.residentialAddress.trim(),
        employmentStatus: values.employmentStatus.trim(),
        employerName: values.employerName.trim() || null,
        termsConsentAccepted: values.termsConsentAccepted,
        dataProcessingConsentAccepted: values.dataProcessingConsentAccepted,
        ...(!profileComplete && !identityVerified ? { identityReference: values.identityReference.trim() } : {}),
      })
      setSaved(true)
    } catch (error) {
      setServerError(error)
      focusAccountError()
    }
  }, onInvalid)

  const validationMessages = Object.values(errors)
    .map((error) => error?.message)
    .filter((message): message is string => typeof message === 'string')

  return (
    <div className="min-w-0 space-y-[var(--section-transactional)]">
      <PageHeader
        eyebrow="Your account"
        title="Profile"
        description="Review and update the personal details saved with your account."
      />
      <AccountNavigation />

      {customerQuery.isPending ? <ProfileLoading /> : null}
      {customerQuery.isError ? (
        <div className="space-y-4">
          <AccountErrorFeedback error={customerQuery.error} title="Profile could not be loaded" />
          <Button variant="secondary" onClick={() => void customerQuery.refetch()}>Try again</Button>
        </div>
      ) : null}

      {customer ? (
        <>
          <AccountReadinessCard customer={customer} presentation="workspace" />
          {customer.status === 'ACTIVE'
            && profileComplete && ['UNVERIFIED', 'REJECTED'].includes(customer.verificationStatus)
            && authState.status === 'authenticated' && authState.actor.permissions.includes('customer:profile:write:own')
            ? <IdentityReferenceCorrection key={authState.actor.userId} /> : null}
          <section aria-labelledby="profile-form-heading" className="min-w-0 max-w-[var(--width-flow)] space-y-8 bg-card p-4 sm:p-6 [overflow-wrap:anywhere]">
            <div className="space-y-2">
              <h2 id="profile-form-heading" className="type-section">{profileComplete ? 'Update your profile' : 'Complete your profile'}</h2>
              <p className="text-sm leading-5 text-muted-foreground">
                Required fields are marked with an asterisk. Review your details before saving.
              </p>
            </div>
            <form className="space-y-8" noValidate onSubmit={onSubmit}>
              {validationMessages.length > 1 ? (
                <Alert variant="destructive">
                  <ShieldCheck aria-hidden="true" />
                  <AlertTitle>Check the highlighted fields</AlertTitle>
                  <AlertDescription>{validationMessages.join(' ')}</AlertDescription>
                </Alert>
              ) : null}
              {serverError ? <AccountErrorFeedback error={serverError} title="Profile save was not confirmed" /> : null}
              {saved ? (
                <AccountSuccessFeedback
                  title="Profile saved"
                  description="Your personal details have been updated."
                />
              ) : null}

              <fieldset className="min-w-0 space-y-6 border-t border-border pt-6">
                <legend className="pr-3 text-base font-semibold">Personal details</legend>
                <div className="grid gap-6 md:grid-cols-2">
                  <AccountFormField htmlFor="fullName" label="Full name" required error={errors.fullName?.message}>
                    {identityVerified ? <Input id="fullName" value={customer.profile?.fullName ?? ''} readOnly /> : <Input
                      id="fullName"
                      autoComplete="name"
                      aria-invalid={Boolean(errors.fullName)}
                      aria-describedby={fieldDescriptionIds('fullName', false, Boolean(errors.fullName))}
                      {...register('fullName', { validate: validateWith(profileFieldSchemas.fullName) })}
                    />}
                  </AccountFormField>
                  <AccountFormField htmlFor="phoneNumber" label="Phone number" required error={errors.phoneNumber?.message}>
                    <Input
                      id="phoneNumber"
                      type="tel"
                      autoComplete="tel"
                      aria-invalid={Boolean(errors.phoneNumber)}
                      aria-describedby={fieldDescriptionIds('phoneNumber', false, Boolean(errors.phoneNumber))}
                      {...register('phoneNumber', { validate: validateWith(profileFieldSchemas.phoneNumber) })}
                    />
                  </AccountFormField>
                </div>

                {profileComplete || identityVerified ? (
                  <div className="border-y border-border py-6">
                    <div className="flex items-start gap-2 font-semibold">
                      <ShieldCheck aria-hidden="true" className="size-5 shrink-0" />
                      Identity reference: On file
                    </div>
                    <p className="mt-2 text-sm leading-5 text-muted-foreground">
                      {identityVerified
                        ? 'Your full name and identity reference are verified and cannot be changed here. You can still update your contact and employment details.'
                        : 'Your identity reference is saved securely and is not displayed. If it is incorrect, use Correct identity reference when available before verification.'}
                    </p>
                  </div>
                ) : (
                  <AccountFormField
                    htmlFor="identityReference"
                    label="Identity reference"
                    required
                    description="Required for first-time profile completion. This value is submitted securely and will not be displayed afterward."
                    error={errors.identityReference?.message}
                  >
                    <Input
                      id="identityReference"
                      autoComplete="off"
                      aria-invalid={Boolean(errors.identityReference)}
                      aria-describedby={fieldDescriptionIds('identityReference', true, Boolean(errors.identityReference))}
                      {...register('identityReference', { validate: validateWith(profileFieldSchemas.identityReference) })}
                    />
                  </AccountFormField>
                )}

                <AccountFormField htmlFor="residentialAddress" label="Residential address" required error={errors.residentialAddress?.message}>
                  <Input
                    id="residentialAddress"
                    autoComplete="street-address"
                    aria-invalid={Boolean(errors.residentialAddress)}
                    aria-describedby={fieldDescriptionIds('residentialAddress', false, Boolean(errors.residentialAddress))}
                    {...register('residentialAddress', { validate: validateWith(profileFieldSchemas.residentialAddress) })}
                  />
                </AccountFormField>
              </fieldset>

              <fieldset className="min-w-0 space-y-6 border-t border-border pt-6">
                <legend className="pr-3 text-base font-semibold">Employment details</legend>
                <div className="grid gap-6 md:grid-cols-2">
                  <AccountFormField htmlFor="employmentStatus" label="Employment status" required error={errors.employmentStatus?.message}>
                    <Input
                      id="employmentStatus"
                      aria-invalid={Boolean(errors.employmentStatus)}
                      aria-describedby={fieldDescriptionIds('employmentStatus', false, Boolean(errors.employmentStatus))}
                      {...register('employmentStatus', { validate: validateWith(profileFieldSchemas.employmentStatus) })}
                    />
                  </AccountFormField>
                  <AccountFormField htmlFor="employerName" label="Employer name" description="Optional." error={errors.employerName?.message}>
                    <Input
                      id="employerName"
                      autoComplete="organization"
                      aria-invalid={Boolean(errors.employerName)}
                      aria-describedby={fieldDescriptionIds('employerName', true, Boolean(errors.employerName))}
                      {...register('employerName', { validate: validateWith(profileFieldSchemas.employerName) })}
                    />
                  </AccountFormField>
                </div>
              </fieldset>

              <fieldset className="min-w-0 space-y-4 border-t border-border pt-6">
                <legend className="pr-3 text-base font-semibold">Consent</legend>
                <ConsentField id="termsConsentAccepted" label="I accept the Meridian terms." error={errors.termsConsentAccepted?.message}>
                  <input
                    id="termsConsentAccepted"
                    type="checkbox"
                    className="mt-1 size-5 shrink-0 accent-primary"
                    aria-invalid={Boolean(errors.termsConsentAccepted)}
                    aria-describedby={errors.termsConsentAccepted ? 'termsConsentAccepted-error' : undefined}
                    {...register('termsConsentAccepted', { validate: (value) => value || 'Accept the Meridian terms to continue.' })}
                  />
                </ConsentField>
                <ConsentField id="dataProcessingConsentAccepted" label="I consent to the processing of my data for this account." error={errors.dataProcessingConsentAccepted?.message}>
                  <input
                    id="dataProcessingConsentAccepted"
                    type="checkbox"
                    className="mt-1 size-5 shrink-0 accent-primary"
                    aria-invalid={Boolean(errors.dataProcessingConsentAccepted)}
                    aria-describedby={errors.dataProcessingConsentAccepted ? 'dataProcessingConsentAccepted-error' : undefined}
                    {...register('dataProcessingConsentAccepted', { validate: (value) => value || 'Accept data processing to continue.' })}
                  />
                </ConsentField>
              </fieldset>

              <div className="flex border-t border-border pt-6 sm:justify-end">
                <Button className="w-full sm:w-auto" size="lg" type="submit" disabled={isSubmitting}>
                  {isSubmitting ? <Spinner /> : null}
                  {isSubmitting ? 'Saving profile…' : 'Save profile'}
                </Button>
              </div>
            </form>
          </section>
        </>
      ) : null}
    </div>
  )
}
