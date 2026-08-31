import { ArrowRight, ShieldAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Blocker } from 'react-router-dom'
import { Link } from 'react-router-dom'

import { StatusBadge } from '@/components/common/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import type { SubmissionEvidenceRequirement } from '@/features/loan-products/loan-product-api'
import {
  documentTypeLabel,
  evidenceRequirementPresentation,
} from '@/features/loan-products/loan-product-presentation'
import { ApiError } from '@/lib/api'

const submissionErrorMessages: Record<string, string> = {
  CUSTOMER_NOT_FOUND: 'Meridian could not confirm the Customer account for this application.',
  PRODUCT_NOT_FOUND: 'This product is no longer available.',
  CUSTOMER_NOT_ACTIVE: 'The Customer account must be active before submission.',
  PROFILE_INCOMPLETE: 'Your Customer profile must be complete before submission.',
  PRIMARY_BANK_ACCOUNT_REQUIRED: 'An active primary bank account is required before submission.',
  PRODUCT_INACTIVE: 'This product is no longer active for new applications.',
  PRODUCT_POLICY_INVALID: 'The current product policy cannot accept this application.',
  INVALID_PRODUCT_AMOUNT: 'The requested amount no longer satisfies the current product policy.',
  INVALID_PRODUCT_TERM: 'The requested term is no longer allowed by the current product policy.',
  INVALID_COLLATERAL_DETAILS: 'The collateral facts are incomplete or outside their technical limits.',
  BLOCKING_APPLICATION_EXISTS: 'Another application for this product now blocks this submission.',
  OUTSTANDING_LOAN_ACCOUNT_EXISTS: 'A prior Unsecured Consumer Loan balance now blocks this submission.',
  SYSTEM_STATE_CONFLICT: 'Meridian could not safely reconcile the current application state.',
  VALIDATION_FAILED: 'Meridian could not validate the submitted request. Review the entered details before trying again.',
}

const errorActions: Record<string, { label: string; to: string }> = {
  PROFILE_INCOMPLETE: { label: 'Open profile', to: '/account/profile' },
  PRIMARY_BANK_ACCOUNT_REQUIRED: { label: 'Open bank accounts', to: '/account/bank-accounts' },
}

export function OriginationSubmissionError({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? submissionErrorMessages[error.errorCode] : undefined
  const action = error instanceof ApiError ? errorActions[error.errorCode] : undefined
  return (
    <Alert variant="destructive" tabIndex={-1} data-submission-error>
      <ShieldAlert aria-hidden="true" />
      <AlertTitle>Application was not submitted</AlertTitle>
      <AlertDescription className="space-y-3">
        <p>{message ?? (error instanceof ApiError
          ? error.message
          : 'The request could not be completed. Check your connection and try again.')}</p>
        <p>Review the retained details and submit again only when you are ready.</p>
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
  return (
    <Dialog open={blocked} onOpenChange={(open) => {
      if (!open && blocker.state === 'blocked') blocker.reset()
    }}>
      <DialogContent>
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
  description = 'These requirements come from the current product policy. Documents are supplied after the application is created.',
}: {
  requirements: SubmissionEvidenceRequirement[]
  description?: string
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Submission evidence</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {requirements.length ? (
          <ul className="divide-y divide-border">
            {requirements.map((requirement, index) => (
              <li key={`${requirement.documentType}-${index}`} className="flex min-w-0 flex-wrap items-center justify-between gap-3 py-4 first:pt-0 last:pb-0">
                <span className="min-w-0 break-words font-medium">{documentTypeLabel(requirement.documentType)}</span>
                <StatusBadge presentation={evidenceRequirementPresentation(requirement.requirementStatus)} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="rounded-md bg-background p-4 text-sm leading-6 text-muted-foreground">No documents are listed by the current product policy.</p>
        )}
      </CardContent>
    </Card>
  )
}

export function ReviewFact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0 rounded-md border border-border bg-background p-4">
      <dt className="text-xs font-semibold tracking-[0.1em] text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-2 min-w-0 break-words font-semibold">{children}</dd>
    </div>
  )
}
