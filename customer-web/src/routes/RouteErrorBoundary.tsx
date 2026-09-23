import { CircleAlert } from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { Button } from '@/components/ui/button'

export function RouteErrorBoundary() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10">
      <div className="w-full max-w-2xl">
        <h1 id="page-heading" tabIndex={-1} className="sr-only">
          Page error
        </h1>
        <EmptyState
          icon={CircleAlert}
          title="We could not open this page"
          description="Return to your dashboard and try again."
          action={
            <Button asChild>
              <Link to="/">Return to dashboard</Link>
            </Button>
          }
        />
      </div>
    </main>
  )
}
