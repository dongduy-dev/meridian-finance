import { AlertCircle, ArrowLeft, CheckCircle2, Clock3, RefreshCw, ShieldAlert, ThumbsDown } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

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
import { applicationKeys } from '@/features/applications/application-queries'
import { OfferSummary } from '@/features/offers/components/OfferSummary'
import { offerErrorMessage, supportedOfferAction, type SupportedOfferAction } from '@/features/offers/offer-presentation'
import {
  useAcceptApprovedOfferMutation,
  useApprovedOfferQuery,
  useDeclineApprovedOfferMutation,
} from '@/features/offers/offer-queries'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'

export function ApplicationOfferPage() {
  const { loanApplicationId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const offerQuery = useApprovedOfferQuery(loanApplicationId)
  const acceptance = useAcceptApprovedOfferMutation()
  const decline = useDeclineApprovedOfferMutation()
  const [actionError, setActionError] = useState<unknown>()
  const [uncertainAction, setUncertainAction] = useState<SupportedOfferAction>()
  const [recovering, setRecovering] = useState(false)
  const [declineOpen, setDeclineOpen] = useState(false)

  const navigateForProvenAction = (action: SupportedOfferAction) => {
    if (!loanApplicationId) return
    if (action === 'ACCEPT') {
      navigate(`/applications/${loanApplicationId}/contract`)
    } else {
      navigate(`/applications/${loanApplicationId}`, {
        state: { workflowResult: { kind: 'offer-declined' } },
      })
    }
  }

  const refreshAuthoritative = async (action?: SupportedOfferAction) => {
    if (!loanApplicationId) return false
    setRecovering(true)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: applicationKeys.index() }),
      queryClient.invalidateQueries({ queryKey: applicationKeys.detail(loanApplicationId) }),
    ])
    const refreshed = await offerQuery.refetch()
    setRecovering(false)
    if (!refreshed.isSuccess) return false
    if (action === 'ACCEPT' && refreshed.data.status === 'ACCEPTED') {
      navigateForProvenAction(action)
      return true
    }
    if (action === 'DECLINE' && refreshed.data.status === 'DECLINED') {
      navigateForProvenAction(action)
      return true
    }
    setUncertainAction(undefined)
    return true
  }

  const respond = async (action: SupportedOfferAction) => {
    if (!loanApplicationId || acceptance.isPending || decline.isPending) return
    setActionError(undefined)
    try {
      if (action === 'ACCEPT') await acceptance.accept(loanApplicationId)
      else await decline.decline(loanApplicationId)
      setUncertainAction(undefined)
      setDeclineOpen(false)
      navigateForProvenAction(action)
    } catch (error) {
      setActionError(error)
      if (error instanceof NetworkError) {
        setUncertainAction(action)
        await refreshAuthoritative(action)
      } else if (error instanceof ApiError && ['OFFER_EXPIRED', 'OFFER_ACTION_CONFLICT'].includes(error.errorCode)) {
        await refreshAuthoritative()
      }
    }
  }

  const offer = offerQuery.data
  const pending = acceptance.isPending || decline.isPending
  const supportedActions = offer?.availableActions.filter(supportedOfferAction) ?? []
  const hasUnknownAction = Boolean(offer?.availableActions.some((action) => !supportedOfferAction(action)))
  const actionsBlocked = Boolean(uncertainAction) || recovering

  return (
    <DetailLayout
      header={<PageHeader eyebrow="Approved offer" title="Review your offer" description="Review the immutable terms Meridian approved for this application." actions={<BackToApplication loanApplicationId={loanApplicationId} />} />}
      rail={offer ? (
        <div className="space-y-4">
          {offer.status === 'PENDING' ? (
            <Alert variant="warning"><Clock3 aria-hidden="true" /><AlertTitle>Offer expiry</AlertTitle><AlertDescription>This offer expires at {formatTimestamp(offer.expiresAt)}. Meridian's returned status and actions remain authoritative.</AlertDescription></Alert>
          ) : null}
          {hasUnknownAction ? (
            <Alert variant="warning"><ShieldAlert aria-hidden="true" /><AlertTitle>Action unavailable</AlertTitle><AlertDescription>Meridian returned an action this Customer Web version cannot execute safely.</AlertDescription></Alert>
          ) : null}
          {!actionsBlocked && supportedActions.length ? (
            <div className="space-y-3 rounded-lg border border-border bg-card p-5 shadow-soft">
              <h2 className="font-semibold">Respond to this offer</h2>
              {supportedActions.includes('ACCEPT') ? <Button className="w-full" disabled={pending} onClick={() => void respond('ACCEPT')}><CheckCircle2 aria-hidden="true" />{acceptance.isPending ? 'Accepting…' : 'Accept offer'}</Button> : null}
              {supportedActions.includes('DECLINE') ? <Button className="w-full" variant="destructive" disabled={pending} onClick={() => { setActionError(undefined); setDeclineOpen(true) }}><ThumbsDown aria-hidden="true" />Decline offer</Button> : null}
            </div>
          ) : null}
          {!actionsBlocked && supportedActions.length === 0 ? (
            <Alert variant="information"><CheckCircle2 aria-hidden="true" /><AlertTitle>No offer response required</AlertTitle><AlertDescription>Meridian is not currently requesting a Customer response to this offer.</AlertDescription></Alert>
          ) : null}
        </div>
      ) : undefined}
    >
      <div className="space-y-6">
        {offerQuery.isPending ? <div role="status" aria-label="Loading approved offer" className="space-y-4"><Skeleton className="h-72" /><Skeleton className="h-52" /></div> : null}
        {offerQuery.isError && !offer ? <QueryErrorFeedback error={offerQuery.error} title="Approved offer could not be loaded" onRetry={() => void offerQuery.refetch()} /> : null}
        {uncertainAction ? (
          <Alert variant="warning" aria-live="polite">
            <AlertCircle aria-hidden="true" />
            <AlertTitle>Offer response needs confirmation</AlertTitle>
            <AlertDescription className="space-y-3">
              <p>Meridian could not confirm whether the {uncertainAction === 'ACCEPT' ? 'acceptance' : 'decline'} completed. The opposite response is unavailable until current state is recovered.</p>
              <div className="flex flex-wrap gap-3">
                <Button size="sm" disabled={pending || recovering} onClick={() => void respond(uncertainAction)}>{pending ? 'Retrying…' : `Retry ${uncertainAction === 'ACCEPT' ? 'accept' : 'decline'}`}</Button>
                <Button size="sm" variant="secondary" disabled={recovering} onClick={() => void refreshAuthoritative(uncertainAction)}><RefreshCw aria-hidden="true" />{recovering ? 'Checking…' : 'Check current status'}</Button>
              </div>
            </AlertDescription>
          </Alert>
        ) : null}
        {actionError && !uncertainAction ? <OfferMutationError error={actionError} /> : null}
        {offer ? <OfferSummary offer={offer} /> : null}
      </div>

      <Dialog open={declineOpen} onOpenChange={setDeclineOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Decline this offer?</DialogTitle><DialogDescription>Declining this offer ends this application. You will not be able to accept this offer afterward.</DialogDescription></DialogHeader>
          {actionError ? <OfferMutationError error={actionError} /> : null}
          <DialogFooter>
            <DialogClose asChild><Button variant="secondary" disabled={pending}>Keep offer</Button></DialogClose>
            <Button variant="destructive" disabled={pending} onClick={() => void respond('DECLINE')}>{decline.isPending ? 'Declining…' : 'Decline offer'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </DetailLayout>
  )
}

function BackToApplication({ loanApplicationId }: { loanApplicationId?: string }) {
  return <Button variant="secondary" asChild><Link to={loanApplicationId ? `/applications/${loanApplicationId}` : '/applications'}><ArrowLeft aria-hidden="true" />Application</Link></Button>
}

function OfferMutationError({ error }: { error: unknown }) {
  return (
    <Alert variant="destructive" aria-live="polite">
      <AlertCircle aria-hidden="true" />
      <AlertTitle>Offer response was not completed</AlertTitle>
      <AlertDescription className="space-y-2"><p>{offerErrorMessage(error)}</p>{error instanceof ApiError && error.requestId ? <p className="break-all text-xs">Support reference: {error.requestId}</p> : null}</AlertDescription>
    </Alert>
  )
}
