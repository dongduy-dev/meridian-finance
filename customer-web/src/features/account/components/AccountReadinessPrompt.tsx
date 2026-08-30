import { CircleAlert } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import type { Customer } from '@/features/account/account-api'

export function AccountReadinessPrompt({ customer }: { customer: Customer }) {
  const profileNeedsAttention = customer.profileCompletionStatus !== 'COMPLETE'
  const bankAccountNeedsAttention = !customer.primaryActiveBankAccountPresent

  if (!profileNeedsAttention && !bankAccountNeedsAttention) return null

  return (
    <Alert variant="warning">
      <CircleAlert aria-hidden="true" />
      <AlertTitle>Finish setting up your account</AlertTitle>
      <AlertDescription className="space-y-3">
        <p>
          Complete the basic account details below to prepare for later lending journeys. Account readiness does not confirm loan eligibility.
        </p>
        <div className="flex flex-wrap gap-2">
          {profileNeedsAttention ? (
            <Button variant="secondary" size="sm" asChild>
              <Link to="/account/profile">Complete profile</Link>
            </Button>
          ) : null}
          {bankAccountNeedsAttention ? (
            <Button variant="secondary" size="sm" asChild>
              <Link to="/account/bank-accounts">Manage bank accounts</Link>
            </Button>
          ) : null}
        </div>
      </AlertDescription>
    </Alert>
  )
}
