import { CheckCircle2, Landmark, Plus, ShieldCheck } from 'lucide-react'
import { useRef, useState } from 'react'
import { useForm, type FieldErrors } from 'react-hook-form'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import type { CustomerBankAccount } from '@/features/account/account-api'
import { useAddBankAccountMutation, useDeactivateBankAccountMutation, useMakePrimaryMutation, useOwnBankAccountsQuery, useOwnCustomerQuery } from '@/features/account/account-queries'
import { fieldDescriptionIds, focusAccountError } from '@/features/account/account-ui'
import { bankAccountFieldSchemas, validateWith } from '@/features/account/account-validation'
import { AccountErrorFeedback, AccountSuccessFeedback } from '@/features/account/components/AccountFeedback'
import { AccountFormField } from '@/features/account/components/AccountFormField'
import { AccountNavigation } from '@/features/account/components/AccountNavigation'
import { AccountReadinessCard } from '@/features/account/components/AccountReadinessCard'

interface AddBankAccountFormValues {
  bankCode: string
  bankNameSnapshot: string
  accountHolderName: string
  accountNumber: string
}

type Confirmation = { type: 'primary' | 'deactivate'; account: CustomerBankAccount }

const bankAccountStatusLabels: Record<string, string> = {
  ACTIVE: 'Active',
  DEACTIVATED: 'Inactive',
}

function accountStatusLabel(status: string) {
  return bankAccountStatusLabels[status] ?? 'Status unavailable'
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.valueOf())
    ? 'Date unavailable'
    : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date)
}

function BankAccountCard({
  account,
  activeAccountCount,
  onConfirm,
}: {
  account: CustomerBankAccount
  activeAccountCount: number
  onConfirm: (confirmation: Confirmation, trigger: HTMLButtonElement) => void
}) {
  const active = account.status === 'ACTIVE'
  const primaryDeactivationBlocked = active && account.primaryAccount && activeAccountCount > 1
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="break-words">{account.bankNameSnapshot}</CardTitle>
            <CardDescription className="mt-1">{account.bankCode}</CardDescription>
          </div>
          <div className="flex flex-wrap gap-2">
            <span className="inline-flex items-center rounded-full bg-muted px-2.5 py-1 text-xs font-semibold text-foreground">
              {accountStatusLabel(account.status)}
            </span>
            {account.primaryAccount ? (
              <span className="inline-flex items-center gap-1 rounded-full bg-success-subtle px-2.5 py-1 text-xs font-semibold text-success">
                <CheckCircle2 aria-hidden="true" className="size-3.5" /> Primary
              </span>
            ) : null}
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="break-all text-xl font-semibold tracking-[0.08em] tabular-nums">{account.maskedAccountNumber}</p>
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div><dt className="text-muted-foreground">Account holder</dt><dd className="mt-1 font-medium break-words">{account.accountHolderName}</dd></div>
          <div><dt className="text-muted-foreground">Added</dt><dd className="mt-1 font-medium">{formatDate(account.createdAt)}</dd></div>
        </dl>
        {primaryDeactivationBlocked ? (
          <p className="text-xs leading-5 text-muted-foreground">Make another active account primary before deactivating this account.</p>
        ) : null}
      </CardContent>
      {active ? (
        <CardFooter className="flex-wrap">
          {!account.primaryAccount ? (
            <Button variant="secondary" size="sm" onClick={(event) => onConfirm({ type: 'primary', account }, event.currentTarget)}>Make primary</Button>
          ) : null}
          <Button
            variant="ghost"
            size="sm"
            disabled={primaryDeactivationBlocked}
            onClick={(event) => onConfirm({ type: 'deactivate', account }, event.currentTarget)}
          >
            Deactivate
          </Button>
        </CardFooter>
      ) : null}
    </Card>
  )
}

export function BankAccountsPage() {
  const customerQuery = useOwnCustomerQuery()
  const accountsQuery = useOwnBankAccountsQuery()
  const addAccount = useAddBankAccountMutation()
  const makePrimary = useMakePrimaryMutation()
  const deactivate = useDeactivateBankAccountMutation()
  const [serverError, setServerError] = useState<unknown>()
  const [successMessage, setSuccessMessage] = useState<string>()
  const [confirmation, setConfirmation] = useState<Confirmation>()
  const confirmationTrigger = useRef<HTMLButtonElement | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    resetField,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<AddBankAccountFormValues>({
    defaultValues: { bankCode: '', bankNameSnapshot: '', accountHolderName: '', accountNumber: '' },
  })

  const onInvalid = (fieldErrors: FieldErrors<AddBankAccountFormValues>) => {
    const first = (['bankCode', 'bankNameSnapshot', 'accountHolderName', 'accountNumber'] as const)
      .find((field) => fieldErrors[field])
    if (first) setFocus(first)
  }

  const onAdd = handleSubmit(async (values) => {
    setServerError(undefined)
    setSuccessMessage(undefined)
    try {
      await addAccount.submit({
        bankCode: values.bankCode.trim(),
        bankNameSnapshot: values.bankNameSnapshot.trim(),
        accountHolderName: values.accountHolderName.trim(),
        accountNumber: values.accountNumber,
      })
      reset()
      setSuccessMessage('Bank account added. Meridian now displays only the protected masked account number.')
    } catch (error) {
      setServerError(error)
      focusAccountError()
    } finally {
      resetField('accountNumber', { defaultValue: '' })
    }
  }, onInvalid)

  const runConfirmation = async () => {
    if (!confirmation) return
    setServerError(undefined)
    setSuccessMessage(undefined)
    try {
      if (confirmation.type === 'primary') {
        await makePrimary.mutateAsync(confirmation.account.customerBankAccountId)
        setSuccessMessage('Primary bank account updated from Meridian’s authoritative account state.')
      } else {
        await deactivate.mutateAsync(confirmation.account.customerBankAccountId)
        setSuccessMessage('Bank account deactivated. Account readiness has been refreshed.')
      }
    } catch (error) {
      setServerError(error)
      focusAccountError()
    } finally {
      setConfirmation(undefined)
    }
  }

  const accounts = accountsQuery.data ?? []
  const activeAccountCount = accounts.filter((account) => account.status === 'ACTIVE').length
  const confirmationPending = makePrimary.isPending || deactivate.isPending
  const openConfirmation = (nextConfirmation: Confirmation, trigger: HTMLButtonElement) => {
    confirmationTrigger.current = trigger
    setConfirmation(nextConfirmation)
  }
  const restoreConfirmationFocus = (event: Event) => {
    event.preventDefault()
    if (confirmationTrigger.current?.isConnected) {
      confirmationTrigger.current.focus()
    } else {
      document.querySelector<HTMLElement>('#saved-accounts-heading')?.focus()
    }
    confirmationTrigger.current = null
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Customer account"
        title="Bank accounts"
        description="Manage the masked bank-account details held with your Meridian Customer account."
      />
      <AccountNavigation />

      {customerQuery.isPending ? <Skeleton className="h-40 w-full" /> : null}
      {customerQuery.isError ? <AccountErrorFeedback error={customerQuery.error} title="Account readiness could not be loaded" /> : null}
      {customerQuery.data ? <AccountReadinessCard customer={customerQuery.data} /> : null}

      {serverError ? <AccountErrorFeedback error={serverError} title="Bank account was not updated" /> : null}
      {successMessage ? <AccountSuccessFeedback title="Bank accounts updated" description={successMessage} /> : null}

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(20rem,0.8fr)]">
        <section aria-labelledby="saved-accounts-heading" className="space-y-4">
          <div>
            <h2 id="saved-accounts-heading" tabIndex={-1} className="text-xl font-semibold outline-none">Saved bank accounts</h2>
            <p className="mt-1 text-sm text-muted-foreground">Only backend-provided masked account numbers are shown.</p>
          </div>
          {accountsQuery.isPending ? (
            <div className="space-y-4" role="status" aria-label="Loading bank accounts">
              <Skeleton className="h-56 w-full" />
              <Skeleton className="h-56 w-full" />
            </div>
          ) : null}
          {accountsQuery.isError ? (
            <div className="space-y-4">
              <AccountErrorFeedback error={accountsQuery.error} title="Bank accounts could not be loaded" />
              <Button variant="secondary" onClick={() => void accountsQuery.refetch()}>Try again</Button>
            </div>
          ) : null}
          {accountsQuery.isSuccess && accounts.length === 0 ? (
            <EmptyState
              icon={Landmark}
              title="No bank accounts yet"
              description="Add a bank account to establish a primary active account for your Customer setup. This does not determine loan eligibility."
              action={<Button variant="secondary" asChild><a href="#add-bank-account">Add bank account</a></Button>}
            />
          ) : null}
          {accounts.length > 0 ? (
            <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-1">
              {accounts.map((account) => (
                <BankAccountCard
                  key={account.customerBankAccountId}
                  account={account}
                  activeAccountCount={activeAccountCount}
                  onConfirm={openConfirmation}
                />
              ))}
            </div>
          ) : null}
        </section>

        <Card id="add-bank-account">
          <CardHeader>
            <div className="flex size-10 items-center justify-center rounded-full bg-accent-subtle"><Plus aria-hidden="true" className="size-5" /></div>
            <CardTitle>Add bank account</CardTitle>
            <CardDescription>Meridian uses the bank name you provide as a snapshot; no bank directory is inferred.</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-5" noValidate onSubmit={onAdd}>
              <AccountFormField htmlFor="bankCode" label="Bank code" required error={errors.bankCode?.message}>
                <Input id="bankCode" autoCapitalize="characters" aria-invalid={Boolean(errors.bankCode)} aria-describedby={fieldDescriptionIds('bankCode', false, Boolean(errors.bankCode))} {...register('bankCode', { validate: validateWith(bankAccountFieldSchemas.bankCode) })} />
              </AccountFormField>
              <AccountFormField htmlFor="bankNameSnapshot" label="Bank name" required error={errors.bankNameSnapshot?.message}>
                <Input id="bankNameSnapshot" aria-invalid={Boolean(errors.bankNameSnapshot)} aria-describedby={fieldDescriptionIds('bankNameSnapshot', false, Boolean(errors.bankNameSnapshot))} {...register('bankNameSnapshot', { validate: validateWith(bankAccountFieldSchemas.bankNameSnapshot) })} />
              </AccountFormField>
              <AccountFormField htmlFor="accountHolderName" label="Account holder name" required error={errors.accountHolderName?.message}>
                <Input id="accountHolderName" autoComplete="name" aria-invalid={Boolean(errors.accountHolderName)} aria-describedby={fieldDescriptionIds('accountHolderName', false, Boolean(errors.accountHolderName))} {...register('accountHolderName', { validate: validateWith(bankAccountFieldSchemas.accountHolderName) })} />
              </AccountFormField>
              <AccountFormField
                htmlFor="accountNumber"
                label="Account number"
                required
                description="The full number is transient input. After this request completes, Customer Web clears it and uses only Meridian’s mask."
                error={errors.accountNumber?.message}
              >
                <Input id="accountNumber" autoComplete="off" spellCheck={false} aria-invalid={Boolean(errors.accountNumber)} aria-describedby={fieldDescriptionIds('accountNumber', true, Boolean(errors.accountNumber))} {...register('accountNumber', { validate: validateWith(bankAccountFieldSchemas.accountNumber) })} />
              </AccountFormField>
              <Button className="w-full" type="submit" disabled={isSubmitting}>
                {isSubmitting ? <Spinner /> : <ShieldCheck aria-hidden="true" />}
                {isSubmitting ? 'Adding account…' : 'Add bank account'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>

      <Dialog open={Boolean(confirmation)} onOpenChange={(open) => { if (!open && !confirmationPending) setConfirmation(undefined) }}>
        <DialogContent onCloseAutoFocus={restoreConfirmationFocus}>
          <DialogHeader>
            <DialogTitle>{confirmation?.type === 'primary' ? 'Make this the primary account?' : 'Deactivate this bank account?'}</DialogTitle>
            <DialogDescription>
              {confirmation?.type === 'primary'
                ? `Meridian will make ${confirmation.account.bankNameSnapshot} ${confirmation.account.maskedAccountNumber} primary and remove the primary designation from the current account.`
                : `Meridian will deactivate ${confirmation?.account.bankNameSnapshot ?? ''} ${confirmation?.account.maskedAccountNumber ?? ''}. It will remain visible as inactive.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild><Button variant="secondary" disabled={confirmationPending}>Cancel</Button></DialogClose>
            <Button
              variant={confirmation?.type === 'deactivate' ? 'destructive' : 'default'}
              disabled={confirmationPending}
              onClick={() => void runConfirmation()}
            >
              {confirmationPending ? <Spinner /> : null}
              {confirmationPending ? 'Updating…' : confirmation?.type === 'primary' ? 'Make primary' : 'Deactivate account'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
