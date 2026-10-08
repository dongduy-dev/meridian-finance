import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useOwnCustomerQuery } from '@/features/account/account-queries'
import { Button } from '@/components/ui/button'
import { FocusedFlowLayout } from '@/components/layout/FocusedFlowLayout'
import { Skeleton } from '@/components/ui/skeleton'

export function IdentityOriginationGuard({ children }: { children: ReactNode }) {
  const customer = useOwnCustomerQuery()
  if (customer.isPending) return <FocusedFlowLayout title="Checking your identity verification…" description="We'll confirm your identity verification status before loading the application.">
    <Skeleton className="h-48" role="status" aria-label="Checking your identity verification…" />
  </FocusedFlowLayout>
  if (customer.isError) return <FocusedFlowLayout title="Application unavailable" description="We need your identity verification status before you can continue.">
    <div className="space-y-6 border-t border-border pt-6"><p role="alert">Your identity verification status could not be loaded.</p><Button onClick={() => void customer.refetch()}>Try again</Button></div>
  </FocusedFlowLayout>
  if (customer.data.verificationStatus !== 'VERIFIED') return <FocusedFlowLayout
    title="Complete identity verification before applying"
    description="Complete your profile, then submit an identity document for review. Your identity must be verified before you can apply."
    continueAction={<Button asChild><Link to="/account/identity-verification">Open identity verification</Link></Button>}
    backAction={<Button variant="secondary" asChild><Link to="/products">Back to products</Link></Button>}
    children={null}
  />
  return children
}
