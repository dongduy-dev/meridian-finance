import { AlertCircle, ArrowLeft, CheckCircle2, Clock3, RefreshCw, ShieldAlert, ThumbsDown } from 'lucide-react'
import { useRef, useState } from 'react'
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
import { StaffAssistedApplicationNotice } from '@/features/applications/components/StaffAssistedApplicationNotice'
import { applicationKeys, useOwnApplicationQuery } from '@/features/applications/application-queries'
import { OfferSummary } from '@/features/offers/components/OfferSummary'
import { offerErrorMessage, supportedOfferAction, type SupportedOfferAction } from '@/features/offers/offer-presentation'
import {
  useAcceptApprovedOfferMutation,
  useApprovedOfferQuery,
  useDeclineApprovedOfferMutation,
  offerKeys,
} from '@/features/offers/offer-queries'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp } from '@/lib/format/presentation'

export function ApplicationOfferPage() {
  const { loanApplicationId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const applicationQuery = useOwnApplicationQuery(loanApplicationId)
  const isDigital = applicationQuery.data?.originationChannel === 'CUSTOMER_DIGITAL'
  const isStaffAssisted = applicationQuery.data?.originationChannel === 'STAFF_ASSISTED'
  const offerQuery = useApprovedOfferQuery(loanApplicationId)
  const acceptance = useAcceptApprovedOfferMutation()
  const decline = useDeclineApprovedOfferMutation()
  const [actionError, setActionError] = useState<unknown>()
  const [uncertainAction, setUncertainAction] = useState<SupportedOfferAction>()
  const [recovering, setRecovering] = useState(false)
  const [declineOpen, setDeclineOpen] = useState(false)
  const declineTrigger = useRef<HTMLButtonElement>(null)
  const prerequisitesValidated = Boolean(loanApplicationId) && isDigital
    && applicationQuery.isSuccess && applicationQuery.fetchStatus === 'idle'
    && offerQuery.isSuccess && offerQuery.fetchStatus === 'idle'

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
    if (!refreshed.isSuccess || refreshed.fetchStatus !== 'idle') return false
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
    const applicationState = queryClient.getQueryState(applicationKeys.detail(loanApplicationId ?? ''))
    const offerState = queryClient.getQueryState(offerKeys.detail(loanApplicationId ?? ''))
    if (!prerequisitesValidated || !loanApplicationId || !offerQuery.data?.availableActions.includes(action)
      || applicationState?.status !== 'success' || applicationState.fetchStatus !== 'idle' || applicationState.data !== applicationQuery.data
      || offerState?.status !== 'success' || offerState.fetchStatus !== 'idle' || offerState.data !== offerQuery.data
      || recovering || uncertainAction || acceptance.isPending || decline.isPending) return
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
  const supportedActions = isDigital ? offer?.availableActions.filter(supportedOfferAction) ?? [] : []
  const hasUnknownAction = isDigital && Boolean(offer?.availableActions.some((action) => !supportedOfferAction(action)))
  const actionsBlocked = !prerequisitesValidated || Boolean(uncertainAction) || recovering

  return (
    <DetailLayout
      header={<PageHeader eyebrow="Approved offer" title="Review your offer" description="Review the terms approved for your application." actions={<BackToApplication loanApplicationId={loanApplicationId} />} />}
    >
      <div className="min-w-0 space-y-[var(--section-transactional)]">
        {applicationQuery.isPending ? <Skeleton className="h-24" role="status" aria-label="Loading application details" /> : null}
        {applicationQuery.isError ? <QueryErrorFeedback error={applicationQuery.error} title="Application details could not be loaded" onRetry={() => void applicationQuery.refetch()} /> : null}
        {isStaffAssisted ? <StaffAssistedApplicationNotice>Review the offer here; Meridian staff will coordinate your response with you.</StaffAssistedApplicationNotice> : null}
        {offerQuery.isPending ? <div role="status" aria-label="Loading approved offer" className="space-y-4"><Skeleton className="h-72" /><Skeleton className="h-52" /></div> : null}
        {offerQuery.isError ? <QueryErrorFeedback error={offerQuery.error} title="Approved offer could not be loaded" onRetry={() => void offerQuery.refetch()} /> : null}
        {isDigital && uncertainAction ? (
          <Alert variant="warning" aria-live="polite">
            <AlertCircle aria-hidden="true" />
            <AlertTitle>Offer response needs confirmation</AlertTitle>
            <AlertDescription className="space-y-3">
              <p>We're checking whether your {uncertainAction === 'ACCEPT' ? 'acceptance' : 'decline'} was completed. Other offer actions are unavailable until this check finishes.</p>
              <div className="flex flex-wrap gap-3">
                <Button size="sm" disabled={actionsBlocked || pending} onClick={() => void respond(uncertainAction)}>{pending ? 'Retrying…' : `Retry ${uncertainAction === 'ACCEPT' ? 'accept' : 'decline'}`}</Button>
                <Button size="sm" variant="secondary" disabled={recovering} onClick={() => void refreshAuthoritative(uncertainAction)}><RefreshCw aria-hidden="true" />{recovering ? 'Checking…' : 'Check current status'}</Button>
              </div>
            </AlertDescription>
          </Alert>
        ) : null}
        {actionError && !uncertainAction ? <OfferMutationError error={actionError} /> : null}
        {offer?.status === 'PENDING' ? (
          <Alert variant="warning"><Clock3 aria-hidden="true" /><AlertTitle>Offer expiry</AlertTitle><AlertDescription>This offer expires at {formatTimestamp(offer.expiresAt)}. Refresh the page if its status changes while you are reviewing it.</AlertDescription></Alert>
        ) : null}
        {offer ? <OfferSummary offer={offer} /> : null}
        {offer ? (
          <div className="min-w-0 space-y-6 border-t border-border pt-6">
            {hasUnknownAction ? (
              <Alert variant="warning"><ShieldAlert aria-hidden="true" /><AlertTitle>Action unavailable</AlertTitle><AlertDescription>This action is not available right now. Refresh the page or try again later.</AlertDescription></Alert>
            ) : null}
            {!actionsBlocked && supportedActions.length ? (
              <div className="min-w-0 space-y-4">
                <h2 className="type-section">Respond to this offer</h2>
                <p className="max-w-[70ch] text-sm leading-5 text-muted-foreground">Accepting confirms these loan terms and takes you to the contract step. Declining ends this application.</p>
                <div className="flex flex-wrap gap-3">
                  {supportedActions.includes('ACCEPT') ? <Button className="w-full sm:w-auto" disabled={pending} onClick={() => void respond('ACCEPT')}><CheckCircle2 aria-hidden="true" />{acceptance.isPending ? 'Accepting…' : 'Accept offer'}</Button> : null}
                  {supportedActions.includes('DECLINE') ? <Button className="w-full sm:w-auto" variant="destructive" disabled={pending} onClick={(event) => { declineTrigger.current = event.currentTarget; setActionError(undefined); setDeclineOpen(true) }}><ThumbsDown aria-hidden="true" />Decline offer</Button> : null}
                </div>
              </div>
            ) : null}
            {isDigital && !actionsBlocked && !hasUnknownAction && supportedActions.length === 0 ? (
              <Alert variant="information"><CheckCircle2 aria-hidden="true" /><AlertTitle>No response required</AlertTitle><AlertDescription>{offer.status === 'EXPIRED' ? 'This offer can no longer be accepted or declined because it has expired.' : offer.status === 'ACCEPTED' ? 'This offer has been accepted. Return to your application to check the next step.' : offer.status === 'DECLINED' ? 'This offer has been declined and can no longer be accepted.' : 'No online response is available for this offer right now. Check its status or contact Meridian support for help.'}</AlertDescription></Alert>
            ) : null}
          </div>
        ) : null}
      </div>

      <Dialog open={!actionsBlocked && supportedActions.includes('DECLINE') && declineOpen} onOpenChange={setDeclineOpen}>
        <DialogContent onCloseAutoFocus={(event) => {
          event.preventDefault()
          if (declineTrigger.current?.isConnected) declineTrigger.current.focus()
          else document.querySelector<HTMLElement>('#page-heading')?.focus()
        }}>
          <DialogHeader><DialogTitle>Decline this offer?</DialogTitle><DialogDescription>Declining this offer ends this application. You will not be able to accept this offer afterward.</DialogDescription></DialogHeader>
          {actionError ? <OfferMutationError error={actionError} /> : null}
          <DialogFooter>
            <DialogClose asChild><Button variant="secondary" disabled={pending}>Keep offer</Button></DialogClose>
            <Button variant="destructive" disabled={actionsBlocked || pending} onClick={() => void respond('DECLINE')}>{decline.isPending ? 'Declining…' : 'Decline offer'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </DetailLayout>
  )
}

function BackToApplication({ loanApplicationId }: { loanApplicationId?: string }) {
  return <Button variant="secondary" asChild><Link to={loanApplicationId ? `/applications/${loanApplicationId}` : '/applications'}><ArrowLeft aria-hidden="true" />Back to application</Link></Button>
}

function OfferMutationError({ error }: { error: unknown }) {
  return (
    <Alert variant="destructive" aria-live="polite">
      <AlertCircle aria-hidden="true" />
      <AlertTitle>Offer response was not confirmed</AlertTitle>
      <AlertDescription className="space-y-2"><p>{offerErrorMessage(error)}</p>{error instanceof ApiError && error.requestId ? <p className="break-all text-sm">Support reference: {error.requestId}</p> : null}</AlertDescription>
    </Alert>
  )
}
