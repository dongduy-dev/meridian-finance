import { ArrowRight, ShieldAlert } from 'lucide-react'
import { useRef, type ReactNode } from 'react'
import type { Blocker } from 'react-router-dom'
import { Link } from 'react-router-dom'

import { StatusBadge } from '@/components/common/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import type { SubmissionEvidenceRequirement } from '@/features/loan-products/loan-product-api'
import {
  documentTypeLabel,
  evidenceRequirementPresentation,
} from '@/features/loan-products/loan-product-presentation'
import { ApiError } from '@/lib/api'
import { customerErrorMessage } from '@/lib/errors/customer-error-message'

const submissionErrorMessages: Record<string, string> = {
  CUSTOMER_NOT_FOUND: 'Meridian could not confirm your account for this application.',
  PRODUCT_NOT_FOUND: 'This product is no longer available.',
  CUSTOMER_NOT_ACTIVE: 'Your account must be active before submission.',
  PROFILE_INCOMPLETE: 'Complete your profile before submission.',
  CUSTOMER_IDENTITY_VERIFICATION_REQUIRED: 'Complete identity verification before submitting this application.',
  PRIMARY_BANK_ACCOUNT_REQUIRED: 'An active primary bank account is required before submission.',
  PRODUCT_INACTIVE: 'This product is no longer active for new applications.',
  PRODUCT_POLICY_INVALID: 'Applications for this loan are temporarily unavailable.',
  INVALID_PRODUCT_AMOUNT: 'The requested amount is no longer available.',
  INVALID_PRODUCT_TERM: 'The requested term is no longer available.',
  INVALID_COLLATERAL_DETAILS: 'The collateral details are incomplete or are not supported.',
  BLOCKING_APPLICATION_EXISTS: 'You already have an application for this loan in progress. You can submit another after it is no longer active.',
  OUTSTANDING_LOAN_ACCOUNT_EXISTS: 'A previous loan for this product still has an outstanding balance. It must be fully repaid before you can apply again.',
  SYSTEM_STATE_CONFLICT: "We couldn't confirm the latest application information. Refresh and try again if appropriate.",
  VALIDATION_FAILED: 'Meridian could not validate the submitted request. Review the entered details before trying again.',
}

const errorActions: Record<string, { label: string; to: string }> = {
  PROFILE_INCOMPLETE: { label: 'Open profile', to: '/account/profile' },
  CUSTOMER_IDENTITY_VERIFICATION_REQUIRED: { label: 'Open identity verification', to: '/account/identity-verification' },
  PRIMARY_BANK_ACCOUNT_REQUIRED: { label: 'Open bank accounts', to: '/account/bank-accounts' },
}

export function OriginationSubmissionError({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? submissionErrorMessages[error.errorCode] : undefined
  const action = error instanceof ApiError ? errorActions[error.errorCode] : undefined
  return (
    <Alert variant="destructive" tabIndex={-1} data-submission-error>
      <ShieldAlert aria-hidden="true" />
      <AlertTitle>Application submission was not confirmed</AlertTitle>
      <AlertDescription className="space-y-3">
        <p>{message ?? customerErrorMessage(error, 'We could not confirm the result. Check your connection.')}</p>
        <p>Check your application list and the details entered here before submitting again.</p>
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

export function OriginationExitWarning({ blocker, productName }: { blocker: Blocker; productName: string }) {
  const blocked = blocker.state === 'blocked'
  const returnFocusTarget = useRef<HTMLElement | null>(null)
  return (
    <Dialog open={blocked} onOpenChange={(open) => {
      if (!open && blocker.state === 'blocked') blocker.reset()
    }}>
      <DialogContent
        onOpenAutoFocus={() => {
          returnFocusTarget.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
        }}
        onCloseAutoFocus={(event) => {
          if (returnFocusTarget.current?.isConnected) {
            event.preventDefault()
            returnFocusTarget.current.focus()
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>Leave this application?</DialogTitle>
          <DialogDescription>
            {productName} does not have a saved draft. Details entered in this browser will be lost.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="secondary" onClick={() => blocker.state === 'blocked' && blocker.reset()}>Stay here</Button>
          <Button variant="destructive" onClick={() => blocker.state === 'blocked' && blocker.proceed()}>Leave application</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function EvidenceRequirements({
  requirements,
  description = 'You can provide these documents after your application is created.',
}: {
  requirements: SubmissionEvidenceRequirement[]
  description?: string
}) {
  return (
    <OriginationSection title="Required documents" description={description}>
      {requirements.length ? (
        <ul className="divide-y divide-border border-y border-border">
          {requirements.map((requirement, index) => (
            <li key={`${requirement.documentType}-${index}`} className="flex min-w-0 flex-wrap items-center justify-between gap-3 py-4">
              <span className="min-w-0 break-words font-medium">{documentTypeLabel(requirement.documentType)}</span>
              <StatusBadge presentation={evidenceRequirementPresentation(requirement.requirementStatus)} />
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm leading-5 text-muted-foreground">No documents are currently listed for this application.</p>
      )}
    </OriginationSection>
  )
}

export function ReviewFact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0 space-y-2 border-t border-border py-4 [overflow-wrap:anywhere]">
      <dt className="text-sm leading-5 text-muted-foreground">{label}</dt>
      <dd className="min-w-0 whitespace-pre-wrap font-semibold">{children}</dd>
    </div>
  )
}

export function OriginationSection({ title, description, children }: {
  title: string
  description: string
  children: ReactNode
}) {
  return (
    <section className="min-w-0 space-y-6 border-t border-border pt-6 [overflow-wrap:anywhere]">
      <div className="space-y-2">
        <h2 className="type-section">{title}</h2>
        <p className="max-w-[70ch] text-sm leading-5 text-muted-foreground">{description}</p>
      </div>
      {children}
    </section>
  )
}
