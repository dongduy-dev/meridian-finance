import { CheckCircle2, FileCheck2, FileText, UploadCloud } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { StatusBadge } from '@/components/common/StatusBadge'
import { FocusedFlowLayout } from '@/components/layout/FocusedFlowLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { applicationStatusPresentation } from '@/features/applications/application-presentation'
import { DocumentStatus } from '@/features/documents/components/DocumentStatus'
import { DocumentUpload } from '@/features/documents/components/DocumentUpload'
import { useDocumentChecklistQuery } from '@/features/documents/document-queries'
import { documentUploadAction, formatFileSize } from '@/features/documents/document-presentation'
import {
  documentTypeLabel, evidenceRequirementPresentation,
} from '@/features/loan-products/loan-product-presentation'
import { formatTimestamp } from '@/lib/format/presentation'

interface SubmissionNotice {
  applicationNumber: string
  status: string
}

function submissionNotice(value: unknown): SubmissionNotice | undefined {
  if (!value || typeof value !== 'object' || !('submission' in value)) return undefined
  const submission = value.submission
  if (!submission || typeof submission !== 'object') return undefined
  if (!('applicationNumber' in submission) || typeof submission.applicationNumber !== 'string') return undefined
  if (!('status' in submission) || typeof submission.status !== 'string') return undefined
  return { applicationNumber: submission.applicationNumber, status: submission.status }
}

export function ApplicationDocumentsPage() {
  const { loanApplicationId } = useParams()
  const location = useLocation()
  const checklistQuery = useDocumentChecklistQuery(loanApplicationId)
  const notice = submissionNotice(location.state)

  return (
    <FocusedFlowLayout
      eyebrow="Application documents"
      title="Documents"
      description="Upload the documents we need and track their review status."
      backAction={<Button variant="secondary" asChild><Link to="/">Return to Dashboard</Link></Button>}
    >
      <div className="space-y-8">
      {notice ? (
        <Alert variant="success">
          <CheckCircle2 aria-hidden="true" />
          <AlertTitle>Application submitted</AlertTitle>
          <AlertDescription className="flex min-w-0 flex-wrap items-center gap-3">
            <span className="break-all">Application {notice.applicationNumber} was created. Review and upload any required documents below.</span>
            <StatusBadge presentation={applicationStatusPresentation(notice.status)} />
          </AlertDescription>
        </Alert>
      ) : null}
      {checklistQuery.isPending ? (
        <div className="space-y-5" role="status" aria-label="Loading documents"><Skeleton className="h-40" /><Skeleton className="h-72" /></div>
      ) : null}
      {checklistQuery.isError ? (
        <QueryErrorFeedback error={checklistQuery.error} title="Documents could not be loaded" onRetry={() => void checklistQuery.refetch()} />
      ) : null}
      {checklistQuery.data ? (
        <>
          <section aria-labelledby="checklist-readiness-heading" className="space-y-4">
            <div><h2 id="checklist-readiness-heading" className="text-xl font-semibold">Document progress</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">Document completion does not mean that your loan is approved.</p></div>
            <dl className="grid gap-4 sm:grid-cols-2">
              <ReadinessFact label="Documents provided" ready={checklistQuery.data.uploadComplete} readyText="All required documents have been provided or are no longer needed." pendingText="One or more required documents are still needed." />
              <ReadinessFact label="Review status" ready={checklistQuery.data.processingReady} readyText="Your documents are ready for the next step." pendingText="One or more documents are still being reviewed or need attention." />
            </dl>
          </section>
          <section aria-labelledby="document-items-heading" className="space-y-4">
            <div><h2 id="document-items-heading" className="text-xl font-semibold">Documents we need</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">Review the status of each document and take action where needed.</p></div>
            {checklistQuery.data.items.length ? (
              <div className="space-y-5">
                {checklistQuery.data.items.map((item) => {
                  const action = documentUploadAction(item.customerStatus)
                  return (
                    <Card key={item.checklistItemId} className="min-w-0">
                      <CardHeader className="gap-3">
                        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0"><CardTitle className="break-words">{documentTypeLabel(item.documentType)}</CardTitle><CardDescription className="mt-1">Document status</CardDescription></div>
                          <StatusBadge presentation={evidenceRequirementPresentation(item.requirementStatus)} />
                        </div>
                      </CardHeader>
                      <CardContent className="space-y-5">
                        <DocumentStatus status={item.customerStatus} />
                        <dl className="grid gap-3 text-sm sm:grid-cols-2">
                          <ReadinessLine label="Document provided" value={item.uploadComplete ? 'Complete' : 'Still needed'} />
                          <ReadinessLine label="Ready for next step" value={item.processingReady ? 'Yes' : 'No'} />
                        </dl>
                        {item.currentVersion ? (
                          <div className="min-w-0 rounded-md border border-border bg-background p-4">
                            <p className="flex items-center gap-2 text-sm font-semibold"><FileText aria-hidden="true" className="size-4 shrink-0" />Uploaded file · Version {item.currentVersion.versionNumber}</p>
                            <dl className="mt-3 grid min-w-0 gap-2 text-sm text-muted-foreground sm:grid-cols-2">
                              <div className="min-w-0"><dt className="font-medium text-foreground">Filename</dt><dd className="break-all">{item.currentVersion.originalFilename}</dd></div>
                              <div className="min-w-0"><dt className="font-medium text-foreground">File details</dt><dd className="break-all">{item.currentVersion.mimeType} · {formatFileSize(item.currentVersion.byteSize)}</dd></div>
                              <div className="sm:col-span-2"><dt className="font-medium text-foreground">Uploaded</dt><dd>{formatTimestamp(item.currentVersion.uploadedAt)}</dd></div>
                            </dl>
                          </div>
                        ) : null}
                        {action ? <DocumentUpload loanApplicationId={checklistQuery.data.loanApplicationId} item={item} action={action} onVersionConflict={() => checklistQuery.refetch()} /> : null}
                      </CardContent>
                    </Card>
                  )
                })}
              </div>
            ) : (
              <EmptyState icon={FileCheck2} title="No documents are currently required" description="There are no documents to upload for this application right now." className="min-h-56" />
            )}
          </section>
        </>
      ) : null}
      </div>
    </FocusedFlowLayout>
  )
}

function ReadinessFact({ label, ready, readyText, pendingText }: { label: string; ready: boolean; readyText: string; pendingText: string }) {
  const Icon = ready ? CheckCircle2 : UploadCloud
  return <div className="rounded-md border border-border bg-card p-5"><dt className="flex items-center gap-2 font-semibold"><Icon aria-hidden="true" className="size-5 text-accent" />{label}: {ready ? 'Complete' : 'Not complete'}</dt><dd className="mt-2 text-sm leading-6 text-muted-foreground">{ready ? readyText : pendingText}</dd></div>
}

function ReadinessLine({ label, value }: { label: string; value: string }) {
  return <div className="rounded-md bg-muted p-3"><dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">{label}</dt><dd className="mt-1 font-medium">{value}</dd></div>
}
