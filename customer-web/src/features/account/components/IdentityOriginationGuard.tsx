import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useOwnCustomerQuery } from '@/features/account/account-queries'
import { Button } from '@/components/ui/button'

export function IdentityOriginationGuard({ children }: { children: ReactNode }) {
  const customer = useOwnCustomerQuery()
  if (customer.isPending) return <div className="mx-auto max-w-2xl p-6" role="status">Checking Customer identity readiness…</div>
  if (customer.isError) return <div className="mx-auto max-w-2xl space-y-4 p-6"><p role="alert">Customer identity readiness could not be loaded.</p><Button onClick={() => void customer.refetch()}>Try again</Button></div>
  if (customer.data.verificationStatus !== 'VERIFIED') return <div className="mx-auto max-w-2xl space-y-4 p-6">
    <h1 className="text-2xl font-semibold">Complete identity verification before applying</h1>
    <p>Submit your identity evidence and wait for Staff review before applying for this product.</p>
    <Button asChild><Link to="/account/identity-verification">Open identity verification</Link></Button>
    <Button variant="secondary" asChild><Link to="/products">Back to products</Link></Button>
  </div>
  return children
}
