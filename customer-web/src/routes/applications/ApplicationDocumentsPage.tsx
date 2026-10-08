import { ArrowLeft, CheckCircle2, FileCheck2, FileText, UploadCloud } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { StatusBadge } from '@/components/common/StatusBadge'
import { FocusedFlowLayout } from '@/components/layout/FocusedFlowLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { StaffAssistedApplicationNotice } from '@/features/applications/components/StaffAssistedApplicationNotice'
import { applicationKeys, useOwnApplicationQuery } from '@/features/applications/application-queries'
import { applicationStatusPresentation } from '@/features/applications/application-presentation'
import { DocumentStatus } from '@/features/documents/components/DocumentStatus'
import { DocumentUpload } from '@/features/documents/components/DocumentUpload'
import { documentKeys, useDocumentChecklistQuery } from '@/features/documents/document-queries'
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
  const applicationQuery = useOwnApplicationQuery(loanApplicationId)
  const queryClient = useQueryClient()
  const isDigital = applicationQuery.data?.originationChannel === 'CUSTOMER_DIGITAL'
  const isStaffAssisted = applicationQuery.data?.originationChannel === 'STAFF_ASSISTED'
  const checklistQuery = useDocumentChecklistQuery(loanApplicationId)
  const actionsAllowed = Boolean(loanApplicationId) && isDigital
    && applicationQuery.isSuccess && applicationQuery.fetchStatus === 'idle'
    && checklistQuery.isSuccess && checklistQuery.fetchStatus === 'idle'
  const canUpload = () => actionsAllowed && [
    { key: applicationKeys.detail(loanApplicationId!), data: applicationQuery.data },
    { key: documentKeys.checklist(loanApplicationId!), data: checklistQuery.data },
  ].every(({ key, data }) => {
    const current = queryClient.getQueryState(key)
    return current?.status === 'success' && current.fetchStatus === 'idle' && current.data === data
  })
  const notice = submissionNotice(location.state)

  return (
    <FocusedFlowLayout
      eyebrow="Application documents"
      title="Documents"
      description={isDigital ? "Upload the documents we need and track their review status." : "Track required documents and their review status."}
      backAction={<Button variant="secondary" asChild><Link to={`/applications/${loanApplicationId}`}><ArrowLeft aria-hidden="true" />Back to application</Link></Button>}
    >
      <div className="min-w-0 space-y-[var(--section-transactional)] [overflow-wrap:anywhere]">
      {applicationQuery.isPending ? <Skeleton className="h-24" role="status" aria-label="Loading application details" /> : null}
      {applicationQuery.isError ? <QueryErrorFeedback error={applicationQuery.error} title="Application details could not be loaded" onRetry={() => void applicationQuery.refetch()} /> : null}
      {isStaffAssisted ? <StaffAssistedApplicationNotice>Documents for this application are handled with Meridian staff. If an upload or replacement is required, Meridian staff will coordinate it with you.</StaffAssistedApplicationNotice> : null}
      {isDigital && notice ? (
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
      {checklistQuery.data && applicationQuery.data ? (
        <>
          {checklistQuery.isSuccess && applicationQuery.isSuccess ? <section aria-labelledby="checklist-readiness-heading" className="space-y-6">
            <div className="space-y-2"><h2 id="checklist-readiness-heading" className="type-section">Document progress</h2><p className="text-sm leading-5 text-muted-foreground">Document completion does not mean that your loan is approved.</p></div>
            <dl className="grid gap-6 border-y border-border py-6 sm:grid-cols-2">
              <ReadinessFact label="Documents provided" ready={checklistQuery.data.uploadComplete} readyText="All required documents have been provided or are no longer needed." pendingText="One or more required documents are still needed." />
              <ReadinessFact label="Review status" ready={checklistQuery.data.processingReady} readyText="Your documents are ready for the next step." pendingText="One or more documents are still being reviewed or need attention." />
            </dl>
          </section> : null}
          <section aria-labelledby="document-items-heading" className="space-y-6">
            <div className="space-y-2"><h2 id="document-items-heading" className="type-section">Documents we need</h2><p className="text-sm leading-5 text-muted-foreground">{isDigital ? "Review the status of each document and take action where needed." : "Review the status of each required document."}</p></div>
            {checklistQuery.data.items.length ? (
              <div className="space-y-8">
                {checklistQuery.data.items.map((item) => {
                  const action = isDigital ? documentUploadAction(item.customerStatus) : undefined
                  return (
                    <Card key={item.checklistItemId} className="min-w-0 border-0">
                      <CardHeader className="gap-3">
                        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0"><h3 className="type-section [overflow-wrap:anywhere]">{documentTypeLabel(item.documentType)}</h3><CardDescription className="mt-2">Document status</CardDescription></div>
                          <StatusBadge presentation={evidenceRequirementPresentation(item.requirementStatus)} />
                        </div>
                      </CardHeader>
                      <CardContent className="space-y-6">
                        <DocumentStatus status={item.customerStatus} staffAssisted={isStaffAssisted} />
                        <dl className="grid gap-6 border-t border-border pt-6 sm:grid-cols-2">
                          <ReadinessLine label="Document provided" value={item.uploadComplete ? 'Complete' : 'Still needed'} />
                          <ReadinessLine label="Ready for next step" value={item.processingReady ? 'Yes' : 'No'} />
                        </dl>
                        {item.currentVersion ? (
                          <div className="min-w-0 space-y-4 border-y border-border py-6">
                            <h4 className="flex items-start gap-2 text-base leading-6 font-semibold"><FileText aria-hidden="true" className="mt-0.5 size-5 shrink-0" />Uploaded file · Version {item.currentVersion.versionNumber}</h4>
                            <dl className="grid min-w-0 gap-4">
                              <ReadinessLine label="Filename" value={item.currentVersion.originalFilename} />
                              <ReadinessLine label="File details" value={`${item.currentVersion.mimeType} · ${formatFileSize(item.currentVersion.byteSize)}`} />
                              <ReadinessLine label="Uploaded" value={formatTimestamp(item.currentVersion.uploadedAt)} />
                            </dl>
                          </div>
                        ) : null}
                        {action ? <DocumentUpload loanApplicationId={checklistQuery.data.loanApplicationId} item={item} action={action} actionsAllowed={actionsAllowed} canAct={canUpload} onVersionConflict={() => checklistQuery.refetch()} /> : null}
                      </CardContent>
                    </Card>
                  )
                })}
              </div>
            ) : checklistQuery.isSuccess && applicationQuery.isSuccess ? (
              <EmptyState icon={FileCheck2} title="No documents are currently required" description="There are no documents to upload for this application right now." />
            ) : null}
          </section>
        </>
      ) : null}
      </div>
    </FocusedFlowLayout>
  )
}

function ReadinessFact({ label, ready, readyText, pendingText }: { label: string; ready: boolean; readyText: string; pendingText: string }) {
  const Icon = ready ? CheckCircle2 : UploadCloud
  return <div className="min-w-0"><dt className="flex items-start gap-2 font-semibold"><Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-foreground" /><span>{label}: {ready ? 'Complete' : 'Not complete'}</span></dt><dd className="mt-2 text-sm leading-5 text-muted-foreground">{ready ? readyText : pendingText}</dd></div>
}

function ReadinessLine({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-sm leading-5 text-muted-foreground">{label}</dt><dd className="mt-2 text-base leading-6 [overflow-wrap:anywhere]">{value}</dd></div>
}
