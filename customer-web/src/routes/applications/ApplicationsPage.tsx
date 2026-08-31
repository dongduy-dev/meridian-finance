import { ArrowRight, FileSearch } from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useOwnApplicationsQuery } from '@/features/applications/application-queries'
import { ApplicationSummary } from '@/features/applications/components/ApplicationSummary'

export function ApplicationsPage() {
  const applicationsQuery = useOwnApplicationsQuery()

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Application tracking"
        title="Applications"
        description="Review every owned application in the authoritative order returned by Meridian."
      />
      {applicationsQuery.isPending ? (
        <div className="space-y-4" role="status" aria-label="Loading applications">
          <Skeleton className="h-56" />
          <Skeleton className="h-56" />
        </div>
      ) : null}
      {applicationsQuery.isError ? (
        <QueryErrorFeedback
          error={applicationsQuery.error}
          title="Applications could not be loaded"
          onRetry={() => void applicationsQuery.refetch()}
        />
      ) : null}
      {applicationsQuery.data?.length ? (
        <div className="space-y-5">
          {applicationsQuery.data.map((application) => (
            <ApplicationSummary
              key={application.loanApplicationId}
              application={application}
              action={(
                <Button variant="secondary" asChild>
                  <Link to={`/applications/${application.loanApplicationId}`}>
                    View application <ArrowRight aria-hidden="true" />
                  </Link>
                </Button>
              )}
            />
          ))}
        </div>
      ) : null}
      {applicationsQuery.data?.length === 0 ? (
        <EmptyState
          icon={FileSearch}
          title="No applications yet"
          description="When you submit a Meridian product application, its current durable state will appear here."
          action={<Button asChild><Link to="/products">Explore products</Link></Button>}
        />
      ) : null}
    </div>
  )
}
