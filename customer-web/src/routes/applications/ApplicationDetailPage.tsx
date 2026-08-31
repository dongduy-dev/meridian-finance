import { ArrowLeft, CircleCheck, Info } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { DetailLayout } from '@/components/layout/DetailLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import {
  useOwnApplicationQuery,
  useOwnApplicationsQuery,
} from '@/features/applications/application-queries'
import { ApplicationSummary } from '@/features/applications/components/ApplicationSummary'
import { RequiredActionCard } from '@/features/applications/components/RequiredActionCard'

interface WorkflowNotice {
  kind: 'resubmitted' | 'cancelled'
  status: string
}

function workflowNotice(value: unknown): WorkflowNotice | undefined {
  if (!value || typeof value !== 'object' || !('workflowResult' in value)) return undefined
  const result = value.workflowResult
  if (!result || typeof result !== 'object') return undefined
  if (!('kind' in result) || (result.kind !== 'resubmitted' && result.kind !== 'cancelled')) return undefined
  if (!('status' in result) || typeof result.status !== 'string') return undefined
  return { kind: result.kind, status: result.status }
}

export function ApplicationDetailPage() {
  const { loanApplicationId } = useParams()
  const location = useLocation()
  const detailQuery = useOwnApplicationQuery(loanApplicationId)
  const indexQuery = useOwnApplicationsQuery()
  const indexedApplication = indexQuery.data?.find(
    (application) => application.loanApplicationId === loanApplicationId,
  )
  const notice = workflowNotice(location.state)
  const notFound = detailQuery.error instanceof ApiError && detailQuery.error.status === 404

  if (notFound) {
    return (
      <DetailLayout
        header={<PageHeader eyebrow="Application tracking" title="Application unavailable" description="This application could not be found or is not available to this Customer." actions={<BackToApplications />} />}
      >
        <EmptyState icon={Info} title="Application unavailable" description="Return to your application list and choose an available application." action={<BackToApplications />} />
      </DetailLayout>
    )
  }

  return (
    <DetailLayout
      header={(
        <PageHeader
          eyebrow="Application detail"
          title={detailQuery.data?.applicationNumber ?? 'Application details'}
          description="Authoritative durable application facts returned by Meridian."
          actions={<BackToApplications />}
        />
      )}
      rail={(
        <div className="space-y-4">
          {indexQuery.isPending ? <Skeleton className="h-64" role="status" aria-label="Loading Customer action" /> : null}
          {indexQuery.isError ? (
            <QueryErrorFeedback error={indexQuery.error} title="Customer action could not be loaded" onRetry={() => void indexQuery.refetch()} />
          ) : null}
          {indexedApplication ? <RequiredActionCard application={indexedApplication} /> : null}
          {indexQuery.data && !indexedApplication ? (
            <Card>
              <CardHeader><CardTitle>Customer action unavailable</CardTitle><CardDescription>The application index did not return an action projection for this application.</CardDescription></CardHeader>
            </Card>
          ) : null}
          {indexedApplication?.requiredAction === 'NONE' ? (
            <Card>
              <CardHeader><CardTitle>No Customer action required</CardTitle><CardDescription>Meridian is not currently requesting Customer work for this application.</CardDescription></CardHeader>
            </Card>
          ) : null}
        </div>
      )}
    >
      <div className="space-y-5">
        {notice ? (
          <Alert variant="success">
            <CircleCheck aria-hidden="true" />
            <AlertTitle>{notice.kind === 'cancelled' ? 'Application cancelled' : 'Corrections resubmitted'}</AlertTitle>
            <AlertDescription>Meridian returned the resulting application status: {notice.status}.</AlertDescription>
          </Alert>
        ) : null}
        {detailQuery.isPending ? <Skeleton className="h-64" role="status" aria-label="Loading application details" /> : null}
        {detailQuery.isError ? (
          <QueryErrorFeedback error={detailQuery.error} title="Application details could not be loaded" onRetry={() => void detailQuery.refetch()} />
        ) : null}
        {detailQuery.data ? <ApplicationSummary application={detailQuery.data} /> : null}
        {detailQuery.data ? (
          <Alert>
            <Info aria-hidden="true" />
            <AlertTitle>Current state only</AlertTitle>
            <AlertDescription>This page does not reconstruct a lifecycle timeline or Staff review history from the current status.</AlertDescription>
          </Alert>
        ) : null}
      </div>
    </DetailLayout>
  )
}

function BackToApplications() {
  return <Button variant="secondary" asChild><Link to="/applications"><ArrowLeft aria-hidden="true" />Applications</Link></Button>
}
