import { Compass } from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { MeridianLogo } from '@/components/common/MeridianLogo'
import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10">
      <div className="w-full max-w-2xl">
        <MeridianLogo variant="primary" className="mx-auto mb-6 w-36" />
        <h1 id="page-heading" tabIndex={-1} className="sr-only">
          Page not found
        </h1>
        <EmptyState
          icon={Compass}
          title="This page is not available"
          description="Check the address or return to the Customer Web foundation."
          action={
            <Button asChild>
              <Link to="/">Return to foundation</Link>
            </Button>
          }
        />
      </div>
    </main>
  )
}
