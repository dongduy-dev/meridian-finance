import { AlertCircle, ArrowLeft, CheckCircle2, FileClock, FileWarning, Info, RefreshCw } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryErrorFeedback } from '@/components/common/QueryErrorFeedback'
import { DetailLayout } from '@/components/layout/DetailLayout'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { useOwnApplicationQuery } from '@/features/applications/application-queries'
import { ContractSummary } from '@/features/contracts/components/ContractSummary'
import { contractErrorMessage } from '@/features/contracts/contract-presentation'
import {
  useAcknowledgeCurrentContractMutation,
  useCurrentContractQuery,
} from '@/features/contracts/contract-queries'
import { ApiError, NetworkError } from '@/lib/api'
import { useOperationIdentity } from '@/lib/ids/use-operation-identity'

type ContractNotice = 'acknowledged' | 'new-version'

export function ApplicationContractPage() {
  const { loanApplicationId } = useParams()
  const applicationQuery = useOwnApplicationQuery(loanApplicationId)
  const contractQuery = useCurrentContractQuery(loanApplicationId)
  const acknowledgment = useAcknowledgeCurrentContractMutation()
  const { begin: beginAcknowledgment, reset: resetAcknowledgment } = useOperationIdentity()
  const lastVersion = useRef<number | undefined>(undefined)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [acknowledgmentError, setAcknowledgmentError] = useState<unknown>()
  const [notice, setNotice] = useState<ContractNotice>()

  const currentVersion = contractQuery.data?.contractVersion
  useEffect(() => {
    if (currentVersion === undefined) return
    if (lastVersion.current !== undefined && lastVersion.current !== currentVersion) {
      resetAcknowledgment()
      setAcknowledgmentError(undefined)
      setDialogOpen(false)
    }
    lastVersion.current = currentVersion
  }, [currentVersion, resetAcknowledgment])

  const reconcileCurrent = async (expectedVersion: number, stale = false) => {
    const refreshed = await contractQuery.refetch()
    if (!refreshed.isSuccess) return false
    if (refreshed.data.contractVersion !== expectedVersion) {
      resetAcknowledgment()
      setNotice('new-version')
      setDialogOpen(false)
      return true
    }
    if (refreshed.data.availableCustomerAction !== 'ACKNOWLEDGE') {
      resetAcknowledgment()
      setNotice('acknowledged')
      setDialogOpen(false)
      return true
    }
    if (stale) setNotice('new-version')
    return true
  }

  const acknowledge = async () => {
    const contract = contractQuery.data
    if (!loanApplicationId || !contract || acknowledgment.isPending || contract.availableCustomerAction !== 'ACKNOWLEDGE') return
    setAcknowledgmentError(undefined)
    setNotice(undefined)
    const expectedVersion = contract.contractVersion
    try {
      await acknowledgment.acknowledge({
        loanApplicationId,
        request: {
          acknowledgmentRequestId: beginAcknowledgment(),
          expectedContractVersion: expectedVersion,
        },
      })
      const recovered = await reconcileCurrent(expectedVersion)
      if (!recovered) setAcknowledgmentError(new Error('Current contract refresh failed.'))
    } catch (error) {
      setAcknowledgmentError(error)
      if (error instanceof NetworkError) {
        await reconcileCurrent(expectedVersion)
      } else if (error instanceof ApiError) {
        const stale = error.errorCode === 'CONTRACT_VERSION_STALE'
        if (stale || error.errorCode === 'IDEMPOTENCY_KEY_REUSED') resetAcknowledgment()
        if (stale || ['CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED', 'IDEMPOTENCY_KEY_REUSED'].includes(error.errorCode)) {
          await reconcileCurrent(expectedVersion, stale)
        }
      }
    }
  }

  const contractMissing = contractQuery.error instanceof ApiError
    && contractQuery.error.errorCode === 'CURRENT_CONTRACT_MISSING'
  const waiting = contractMissing && applicationQuery.data?.status === 'CONTRACT_PENDING'
  const contract = contractQuery.data
  const unknownAction = Boolean(contract?.availableCustomerAction && contract.availableCustomerAction !== 'ACKNOWLEDGE')
  const canAcknowledge = contract?.availableCustomerAction === 'ACKNOWLEDGE'

  return (
    <DetailLayout
      header={<PageHeader eyebrow="Operational contract" title={waiting ? 'Contract preparation in progress' : 'Review your contract'} description="Review the current version and its immutable accepted terms." actions={<BackToApplication loanApplicationId={loanApplicationId} />} />}
      rail={contract ? (
        <div className="space-y-4">
          {canAcknowledge ? (
            <div className="space-y-3 rounded-lg border border-border bg-card p-5 shadow-soft">
              <h2 className="font-semibold">Customer acknowledgment</h2>
              <p className="text-sm leading-6 text-muted-foreground">Acknowledge only after reviewing exact contract version {contract.contractVersion}.</p>
              <Button className="w-full" disabled={acknowledgment.isPending} onClick={() => { beginAcknowledgment(); setAcknowledgmentError(undefined); setDialogOpen(true) }}><CheckCircle2 aria-hidden="true" />Acknowledge version {contract.contractVersion}</Button>
            </div>
          ) : null}
          {unknownAction ? <Alert variant="warning"><FileWarning aria-hidden="true" /><AlertTitle>Action unavailable</AlertTitle><AlertDescription>Meridian returned a contract action this Customer Web version cannot execute safely.</AlertDescription></Alert> : null}
          {!canAcknowledge && !unknownAction ? <Alert variant="information"><CheckCircle2 aria-hidden="true" /><AlertTitle>No Customer action required</AlertTitle><AlertDescription>No further Customer action is currently required.</AlertDescription></Alert> : null}
        </div>
      ) : undefined}
    >
      <div className="space-y-6">
        {waiting ? (
          <EmptyState icon={FileClock} title="Your operational contract is not ready yet" description="Your offer has been accepted. Check again later for the current operational contract." action={<Button onClick={() => void contractQuery.refetch()}><RefreshCw aria-hidden="true" />Check again</Button>} />
        ) : null}
        {!waiting && (contractQuery.isPending || (contractMissing && applicationQuery.isPending)) ? <div role="status" aria-label="Loading current contract" className="space-y-4"><Skeleton className="h-72" /><Skeleton className="h-52" /></div> : null}
        {!waiting && contractQuery.isError && !contract ? <QueryErrorFeedback error={contractQuery.error} title="Current contract could not be loaded" onRetry={() => void contractQuery.refetch()} /> : null}
        {notice === 'acknowledged' ? <Alert variant="success" aria-live="polite"><CheckCircle2 aria-hidden="true" /><AlertTitle>Contract acknowledged</AlertTitle><AlertDescription>Meridian confirms that the displayed current contract version is acknowledged.</AlertDescription></Alert> : null}
        {notice === 'new-version' ? <Alert variant="warning" aria-live="polite"><FileWarning aria-hidden="true" /><AlertTitle>Review the current contract version</AlertTitle><AlertDescription>The current contract changed. Review this version before beginning a new acknowledgment.</AlertDescription></Alert> : null}
        <Alert variant="information"><Info aria-hidden="true" /><AlertTitle>Operational acknowledgment</AlertTitle><AlertDescription>Acknowledgment records operational evidence for this exact contract version. It is not a generated legal agreement, PDF-signing workflow, electronic signature, or digital signature.</AlertDescription></Alert>
        {contract ? <ContractSummary contract={contract} /> : null}
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Acknowledge contract version {contract?.contractVersion}?</DialogTitle><DialogDescription>This acknowledgment applies only to the exact current version shown. Confirm after reviewing its terms and masked destination.</DialogDescription></DialogHeader>
          {acknowledgmentError ? <ContractMutationError error={acknowledgmentError} /> : null}
          <DialogFooter>
            <DialogClose asChild><Button variant="secondary" disabled={acknowledgment.isPending}>Review again</Button></DialogClose>
            <Button disabled={acknowledgment.isPending} onClick={() => void acknowledge()}>{acknowledgment.isPending ? 'Acknowledging…' : `Acknowledge version ${contract?.contractVersion}`}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </DetailLayout>
  )
}

function BackToApplication({ loanApplicationId }: { loanApplicationId?: string }) {
  return <Button variant="secondary" asChild><Link to={loanApplicationId ? `/applications/${loanApplicationId}` : '/applications'}><ArrowLeft aria-hidden="true" />Application</Link></Button>
}

function ContractMutationError({ error }: { error: unknown }) {
  return (
    <Alert variant="destructive" aria-live="polite">
      <AlertCircle aria-hidden="true" />
      <AlertTitle>Contract acknowledgment was not completed</AlertTitle>
      <AlertDescription className="space-y-2"><p>{contractErrorMessage(error)}</p>{error instanceof ApiError && error.requestId ? <p className="break-all text-xs">Support reference: {error.requestId}</p> : null}</AlertDescription>
    </Alert>
  )
}
