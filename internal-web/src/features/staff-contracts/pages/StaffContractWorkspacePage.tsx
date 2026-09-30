import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Clock3, RefreshCw, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { useOperationResultFocus } from '@/components/operations/useOperationResultFocus'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { hasPermission, hasRole } from '@/features/auth/model/access-control'
import { useAuth } from '@/features/auth/model/auth-context'
import { uuidSchema } from '@/features/staff-applications/api/contracts'
import { staffApplicationKeys } from '@/features/staff-applications/api/queries'
import { QueryErrorPanel } from '@/features/staff-applications/components/QueryErrorPanel'
import { StatusBadge } from '@/features/staff-applications/components/StatusBadge'
import { humanizeKnownValue, productLabel } from '@/features/staff-applications/model/presentation'
import { ApiError, NetworkError } from '@/lib/api'
import { formatTimestamp, formatVnd } from '@/lib/format/presentation'
import {
  decideOperationIdentity,
  digestOperationPayload,
  findUnresolvedOperation,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
  type UnresolvedOperation,
  type UnresolvedOperationType,
} from '@/lib/operation/unresolved-operation'
import {
  preparationSemanticPayloadSchema,
  readinessConfirmationSemanticPayloadSchema,
  assistedAcknowledgmentSemanticPayloadSchema,
  type LoanContract,
  type PreparationSemanticPayload,
  type ReadinessConfirmationSemanticPayload,
  type AssistedAcknowledgmentSemanticPayload,
} from '../api/contracts'
import { staffContractCaseQuery, staffContractKeys } from '../api/queries'
import {
  confirmContractReadiness,
  prepareLoanContract,
  recordAssistedContractAcknowledgment,
  uploadContractAcknowledgmentEvidence,
} from '../api/staff-contracts-api'
import {
  blockerLabel,
  contractStageLabel,
  contractStatusLabel,
  hasCoherentContractLifecycle,
  knownContractStatuses,
  knownContractWorkStages,
  knownReadinessBlockers,
} from '../model/presentation'
import { AccountingCaseContextPanel } from '../components/AccountingCaseContextPanel'

type CommandKind = 'PREPARATION' | 'READINESS' | 'ASSISTED_ACKNOWLEDGMENT'
type Confirmation =
  | { kind: 'PREPARATION'; payload: PreparationSemanticPayload }
  | { kind: 'READINESS'; payload: ReadinessConfirmationSemanticPayload }
  | { kind: 'ASSISTED_ACKNOWLEDGMENT'; payload: AssistedAcknowledgmentSemanticPayload }
type OperationState = { status: OperationStatus; detail?: string; error?: Error }

const preparationType: UnresolvedOperationType = 'CONTRACT_PREPARATION'
const readinessType: UnresolvedOperationType = 'CONTRACT_READINESS_CONFIRMATION'
const assistedAcknowledgmentType: UnresolvedOperationType = 'ASSISTED_CONTRACT_ACKNOWLEDGMENT'

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-semibold">{children}</dd></div>
}

function operationType(kind: CommandKind) {
  if (kind === 'PREPARATION') return preparationType
  return kind === 'READINESS' ? readinessType : assistedAcknowledgmentType
}

function parseStoredPayload(operation: UnresolvedOperation): Confirmation | undefined {
  if (operation.type === preparationType) {
    const parsed = preparationSemanticPayloadSchema.safeParse(operation.semanticPayload)
    return parsed.success ? { kind: 'PREPARATION', payload: parsed.data } : undefined
  }
  if (operation.type === readinessType) {
    const parsed = readinessConfirmationSemanticPayloadSchema.safeParse(operation.semanticPayload)
    return parsed.success ? { kind: 'READINESS', payload: parsed.data } : undefined
  }
  if (operation.type === assistedAcknowledgmentType) {
    const parsed = assistedAcknowledgmentSemanticPayloadSchema.safeParse(operation.semanticPayload)
    return parsed.success ? { kind: 'ASSISTED_ACKNOWLEDGMENT', payload: parsed.data } : undefined
  }
  return undefined
}

export function StaffContractWorkspacePage() {
  const { manager, state } = useAuth()
  const queryClient = useQueryClient()
  const { loanApplicationId = '' } = useParams()
  const validId = uuidSchema.safeParse(loanApplicationId).success
  const canRead = state.status === 'authenticated' && hasPermission(state.actor, 'loan:contract:read')
  const canPrepare = state.status === 'authenticated' && hasPermission(state.actor, 'loan:contract:prepare')
  const canConfirm = state.status === 'authenticated' && hasPermission(state.actor, 'loan:disbursement:prepare')
  const canAcknowledgeAssisted = state.status === 'authenticated'
    && hasPermission(state.actor, 'loan:contract:acknowledge:staff')
  const canUploadAssistedEvidence = state.status === 'authenticated'
    && hasPermission(state.actor, 'document:upload:assisted-action')
  const canReadApplication = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const isAccounting = state.status === 'authenticated' && hasRole(state.actor, 'ACCOUNTING_OFFICER')
  const query = useQuery(staffContractCaseQuery(manager, loanApplicationId, canRead && validId))
  const [confirmation, setConfirmation] = useState<Confirmation>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const requestResultFocus = useOperationResultFocus(operation.status, 'contract-command-result')
  const [staleEvidence, setStaleEvidence] = useState(false)
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const [temporaryContract, setTemporaryContract] = useState<LoanContract>()
  const [acknowledgmentFile, setAcknowledgmentFile] = useState<File>()
  const [uploadingAcknowledgment, setUploadingAcknowledgment] = useState(false)
  const [acknowledgmentConfirmed, setAcknowledgmentConfirmed] = useState(false)
  const resource = loanApplicationId
  const unresolvedPreparation = validId ? findUnresolvedOperation(preparationType, resource) : undefined
  const unresolvedReadiness = validId ? findUnresolvedOperation(readinessType, resource) : undefined
  const unresolvedAcknowledgment = validId ? findUnresolvedOperation(assistedAcknowledgmentType, resource) : undefined
  const hasUnresolved = Boolean(unresolvedPreparation || unresolvedReadiness || unresolvedAcknowledgment)

  const invalidateRelatedReads = async () => {
    await queryClient.invalidateQueries({ queryKey: staffContractKeys.all }).catch(() => undefined)
    if (canReadApplication) {
      await queryClient.invalidateQueries({ queryKey: staffApplicationKeys.all }).catch(() => undefined)
    }
  }

  const refreshAfterConfirmedCommand = async (result: LoanContract) => {
    setTemporaryContract(result)
    setOperation({ status: 'RECONCILING' })
    try {
      await query.refetch({ throwOnError: true })
      await invalidateRelatedReads()
      setConfirmedRefreshFailed(false)
      setTemporaryContract(undefined)
      setOperation({
        status: 'RESOLVED',
        detail: 'The contract action was confirmed and the latest contract information is loaded.',
      })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({
        status: 'BLOCKED',
        error: error instanceof Error ? error : new NetworkError(),
        detail: 'Action confirmed; the latest contract information is unavailable. Use Refresh to load it, and do not submit the action again.',
      })
    }
  }

  const reconcileUnknownResult = async (commandError: Error) => {
    setOperation({ status: 'RECONCILING' })
    try {
      await query.refetch({ throwOnError: true })
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: commandError,
        detail: 'Meridian could not confirm this action from the latest contract information. Retry the same contract action before starting a different one.',
      })
    } catch (error) {
      const reconciliationError = commandError instanceof ApiError && commandError.requestId
        ? commandError
        : error instanceof Error ? error : new NetworkError()
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: reconciliationError,
        detail: 'Meridian could not confirm the result or load the latest contract information. It did not submit the action again automatically.',
      })
    }
  }

  const postCommand = async (submitted: Confirmation, operationId: string, retrying = false) => {
    const type = operationType(submitted.kind)
    setOperation({ status: 'IN_FLIGHT' })
    try {
      const result = submitted.kind === 'PREPARATION'
        ? await prepareLoanContract(manager, submitted.payload.loanApplicationId, {
          preparationRequestId: operationId,
          expectedCurrentContractVersion: submitted.payload.expectedCurrentContractVersion,
          supersessionReasonCode: submitted.payload.supersessionReasonCode,
        })
        : submitted.kind === 'READINESS' ? await confirmContractReadiness(manager, submitted.payload.loanApplicationId, {
          confirmationRequestId: operationId,
          expectedContractVersion: submitted.payload.expectedContractVersion,
        })
          : await recordAssistedContractAcknowledgment(manager, submitted.payload, operationId)
      removeUnresolvedOperation(type, resource)
      await refreshAfterConfirmedCommand(result)
    } catch (error) {
      const commandError = error instanceof Error ? error : new NetworkError()
      if (commandError instanceof ApiError && commandError.status < 500) {
        removeUnresolvedOperation(type, resource)
        const stale = commandError.errorCode === 'CONTRACT_VERSION_STALE'
        if (stale) {
          setStaleEvidence(true)
          await query.refetch().catch(() => undefined)
        }
        setOperation({
          status: 'BLOCKED',
          error: commandError,
          detail: stale
            ? 'The displayed contract version is out of date. Review the updated contract before confirming another action.'
            : 'The action was rejected. Review the reason before trying again.',
        })
        return
      }
      const payloadDigest = await digestOperationPayload(submitted.payload)
      saveUnresolvedOperation({
        type,
        resource,
        operationId,
        payloadDigest,
        semanticPayload: submitted.payload,
        unresolvedAt: new Date().toISOString(),
      })
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: commandError,
        detail: retrying
          ? 'The result is still unconfirmed. Retry the same contract action with unchanged details.'
          : 'The result is not confirmed. Meridian did not submit the action again automatically; use the retry action shown with unchanged details.',
      })
      await reconcileUnknownResult(commandError)
    }
  }

  const submitConfirmed = async () => {
    if (!confirmation) return
    requestResultFocus()
    const submitted = confirmation
    setConfirmation(undefined)
    const type = operationType(submitted.kind)
    const payloadDigest = await digestOperationPayload(submitted.payload)
    const identity = decideOperationIdentity(type, resource, payloadDigest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation({
        status: 'BLOCKED',
        detail: 'A different contract action is still unresolved. Retry that same contract action before starting a new one.',
      })
      return
    }
    await postCommand(submitted, identity.operationId, identity.kind === 'REUSE_EXISTING')
  }

  const retryExact = async (unresolved: UnresolvedOperation) => {
    const stored = parseStoredPayload(unresolved)
    if (!stored) {
      setOperation({
        status: 'BLOCKED',
        detail: 'The saved recovery information cannot confirm the exact action. Do not start a replacement action; review the latest contract information.',
      })
      return
    }
    await postCommand(stored, unresolved.operationId, true)
  }

  const refreshOnly = async () => {
    try {
      await query.refetch({ throwOnError: true })
      if (confirmedRefreshFailed) {
        await invalidateRelatedReads()
        setConfirmedRefreshFailed(false)
        setTemporaryContract(undefined)
        setOperation({ status: 'RESOLVED', detail: 'The action remains confirmed and the latest contract information is now loaded.' })
      }
    } catch {
      // React Query retains the query error and the last validated data for presentation.
    }
  }

  const uploadAcknowledgmentEvidence = async () => {
    const current = query.data?.currentContract
    if (!current || !acknowledgmentFile || !canUploadAssistedEvidence || !isAccounting) return
    setUploadingAcknowledgment(true)
    setOperation({ status: 'IN_FLIGHT', detail: 'Uploading a new version of the signed acknowledgment.' })
    try {
      await uploadContractAcknowledgmentEvidence(
        manager,
        loanApplicationId,
        current.contractId,
        current.contractVersion,
        acknowledgmentFile,
        crypto.randomUUID(),
        query.data?.assistedAcknowledgmentEvidence?.documentVersionId,
      )
      setAcknowledgmentFile(undefined)
      setAcknowledgmentConfirmed(false)
      await query.refetch({ throwOnError: true })
      setOperation({ status: 'RESOLVED', detail: 'Signed Customer Contract Acknowledgment evidence uploaded. Review the current document version before recording the acknowledgment.' })
    } catch (error) {
      setOperation({
        status: 'BLOCKED',
        error: error instanceof Error ? error : new NetworkError(),
        detail: 'Meridian did not retry the evidence upload automatically. Refresh before selecting another file.',
      })
      await query.refetch().catch(() => undefined)
    } finally {
      setUploadingAcknowledgment(false)
    }
  }

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Contract workspace unavailable</h1><Alert variant="warning"><Clock3 /><AlertTitle>Contract workspace unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading contract workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading contract and readiness information…</div></section>
  const accountingRejected = query.error instanceof ApiError && query.error.errorCode === 'ACCOUNTING_ROLE_REQUIRED'
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Contract and readiness workspace</h1>{accountingRejected ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>Accounting Officer access is required for contract operations.</AlertDescription></Alert> : <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} />}</section>
  if (!query.data) return null
  const data = query.data
  const contract = data.currentContract
  const knownStage = knownContractWorkStages.has(data.workStage)
  const knownStatus = !contract || knownContractStatuses.has(contract.status)
  const unknownBlockers = data.readiness.blockerCodes.filter((code) => !knownReadinessBlockers.has(code))
  const canonicalReadiness = data.readiness.calculationSemantics === 'POINT_IN_TIME_ADVISORY'
    && data.readiness.recomputedDuringConfirmation
  const safeEvidence = knownStage && knownStatus && unknownBlockers.length === 0 && canonicalReadiness
    && hasCoherentContractLifecycle(data)
  const controlsLocked = query.isError || hasUnresolved || staleEvidence || confirmedRefreshFailed
    || operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING'
  const preparationAvailable = canPrepare && isAccounting && safeEvidence && !controlsLocked
    && data.applicationStatus === 'CONTRACT_PENDING'
    && ((!contract && data.workStage === 'NEEDS_PREPARATION')
      || Boolean(contract && (contract.status === 'PREPARED' || contract.status === 'ACKNOWLEDGED')))
  const confirmationAvailable = canConfirm && isAccounting && safeEvidence && !controlsLocked
    && data.applicationStatus === 'CONTRACT_PENDING'
    && data.workStage === 'READY_TO_CONFIRM'
    && contract?.status === 'ACKNOWLEDGED'
    && data.readiness.ready
    && data.readiness.blockerCodes.length === 0
  const assistedAcknowledgmentAvailable = canAcknowledgeAssisted && isAccounting && safeEvidence
    && !controlsLocked
    && data.originationChannel === 'STAFF_ASSISTED'
    && data.applicationStatus === 'CONTRACT_PENDING'
    && data.workStage === 'CUSTOMER_ACKNOWLEDGMENT_REQUIRED'
    && contract?.status === 'PREPARED'
    && contract.availableCustomerAction === 'ACKNOWLEDGE'
  const acknowledgmentEvidenceMatches = Boolean(
    contract
      && data.assistedAcknowledgmentEvidence?.evidenceType === 'CUSTOMER_CONTRACT_ACKNOWLEDGMENT'
      && data.assistedAcknowledgmentEvidence.targetId === contract.contractId
      && data.assistedAcknowledgmentEvidence.targetVersion === contract.contractVersion,
  )

  const openPreparation = () => {
    if (!preparationAvailable) return
    setConfirmation({
      kind: 'PREPARATION',
      payload: {
        loanApplicationId,
        expectedCurrentContractVersion: contract?.contractVersion ?? 0,
        supersessionReasonCode: contract ? 'DISBURSEMENT_ACCOUNT_REFRESH' : null,
      },
    })
  }

  const openReadinessConfirmation = () => {
    if (!confirmationAvailable || !contract) return
    setConfirmation({
      kind: 'READINESS',
      payload: { loanApplicationId, expectedContractVersion: contract.contractVersion },
    })
  }

  const openAssistedAcknowledgmentConfirmation = () => {
    if (!assistedAcknowledgmentAvailable || !acknowledgmentEvidenceMatches || !acknowledgmentConfirmed
      || !contract || !data.assistedAcknowledgmentEvidence) return
    setConfirmation({
      kind: 'ASSISTED_ACKNOWLEDGMENT',
      payload: {
        loanApplicationId,
        contractId: contract.contractId,
        expectedContractVersion: contract.contractVersion,
        evidenceDocumentVersionId: data.assistedAcknowledgmentEvidence.documentVersionId,
      },
    })
  }

  const closeConfirmation = () => {
    const kind = confirmation?.kind
    setConfirmation(undefined)
    setTimeout(() => document.getElementById(
      kind === 'PREPARATION' ? 'prepare-contract-trigger'
        : kind === 'READINESS' ? 'confirm-readiness-trigger' : 'assisted-acknowledgment-trigger',
    )?.focus(), 0)
  }

  const handleConfirmationKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeConfirmation()
      return
    }
    if (event.key !== 'Tab') return
    const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'))
    const first = controls[0]
    const last = controls.at(-1)
    if (!first || !last) return
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return <section className="mx-auto max-w-6xl space-y-6">
    <div className="flex flex-wrap gap-4"><Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to="/staff/work/contracts">← Contract queue</Link>{canReadApplication ? <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" to={`/staff/applications/${loanApplicationId}`}>Application case</Link> : null}</div>
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">CONTRACT AND READINESS</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">{data.applicationNumber}</h1><div className="mt-3 flex flex-wrap items-center gap-3"><StatusBadge status={data.applicationStatus} /><span className="text-sm text-muted-foreground">{productLabel(data.productCode)}</span><span className="text-sm font-semibold">{contractStageLabel(data.workStage)}</span></div></div><Button variant="outline" onClick={() => void refreshOnly()} disabled={query.isFetching}><RefreshCw className={query.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div><dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-5"><Fact label="Origination channel">{humanizeKnownValue(data.originationChannel)}</Fact><Fact label="Requested amount">{formatVnd(data.requestedAmount)}</Fact><Fact label="Requested term">{data.requestedTermMonths} months</Fact><Fact label="Submitted">{formatTimestamp(data.submittedAt)}</Fact></dl></header>
    {!isAccounting ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>Accounting Officer access is required to prepare contracts and confirm readiness.</AlertDescription></Alert> : null}
    {query.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Previously loaded contract details are shown. Refresh successfully before taking action.</AlertDescription></Alert> : null}
    {!safeEvidence ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Operational evidence unavailable</AlertTitle><AlertDescription>The latest contract or readiness information is incomplete or unrecognized. Consequential actions are disabled.</AlertDescription></Alert> : null}
    {data.workStage === 'READINESS_CONFIRMED' ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Readiness confirmed — not disbursed</AlertTitle><AlertDescription>The contract is ready for disbursement. The transfer must still be completed and recorded separately to activate the loan account.</AlertDescription></Alert> : null}
    <AccountingCaseContextPanel context={data.accountingContext} contractVersion={contract?.contractVersion ?? null} />
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1.35fr)_minmax(18rem,.65fr)]">
      <Card><CardHeader><CardTitle>Current contract</CardTitle></CardHeader><CardContent>{contract ? <div className="space-y-6"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Reference">{contract.contractReference}</Fact><Fact label="Version">{contract.contractVersion}</Fact><Fact label="Status">{contractStatusLabel(contract.status)}</Fact><Fact label="Prepared">{formatTimestamp(contract.preparedAt)}</Fact><Fact label="Acknowledged">{formatTimestamp(contract.acknowledgedAt)}</Fact><Fact label="Readiness confirmed">{formatTimestamp(contract.readinessConfirmedAt)}</Fact></dl><div><h3 className="font-semibold">Accepted financial terms</h3><dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Approved principal">{formatVnd(contract.approvedPrincipal)}</Fact><Fact label="Approved term">{contract.approvedTermMonths} months</Fact><Fact label="Interest method">{humanizeKnownValue(contract.interestCalculationMethod)}</Fact><Fact label="Flat monthly rate">{contract.flatMonthlyInterestRate}</Fact><Fact label="Total interest">{formatVnd(contract.totalInterest)}</Fact><Fact label="Fee">{formatVnd(contract.feeAmount)}</Fact><Fact label="Total repayment">{formatVnd(contract.totalRepaymentAmount)}</Fact><Fact label="Repayment method">{humanizeKnownValue(contract.repaymentMethod)}</Fact></dl></div><div><h3 className="font-semibold">Disbursement destination</h3><dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Bank">{contract.disbursementBankAccount.bankNameSnapshot} ({contract.disbursementBankAccount.bankCode})</Fact><Fact label="Account holder">{contract.disbursementBankAccount.accountHolderName}</Fact><Fact label="Account number">{contract.disbursementBankAccount.maskedAccountNumber}</Fact><Fact label="Primary at capture">{contract.disbursementBankAccount.primaryAtCapture ? 'Yes' : 'No'}</Fact><Fact label="Active at capture">{contract.disbursementBankAccount.activeAtCapture ? 'Yes' : 'No'}</Fact><Fact label="Captured">{formatTimestamp(contract.disbursementBankAccount.capturedAt)}</Fact></dl></div><div><h3 className="font-semibold">Provisional repayment items</h3><div className="mt-3 overflow-x-auto rounded-md border"><table className="min-w-[42rem] w-full text-left text-sm"><thead className="bg-muted text-xs uppercase text-muted-foreground"><tr><th className="p-3">Item</th><th className="p-3">Principal</th><th className="p-3">Interest</th><th className="p-3">Fee</th><th className="p-3">Total</th></tr></thead><tbody>{contract.repaymentPreview.map((item) => <tr key={item.installmentNumber} className="border-t"><td className="p-3 font-semibold">{item.installmentNumber}</td><td className="p-3">{formatVnd(item.principalDue)}</td><td className="p-3">{formatVnd(item.interestDue)}</td><td className="p-3">{formatVnd(item.feeDue)}</td><td className="p-3 font-semibold">{formatVnd(item.totalDue)}</td></tr>)}</tbody></table></div></div></div> : <p className="text-sm text-muted-foreground">No current contract has been prepared.</p>}</CardContent></Card>
      <Card><CardHeader><CardTitle>Current readiness checks</CardTitle></CardHeader><CardContent className="space-y-5"><Alert variant={data.readiness.ready ? 'success' : 'warning'}>{data.readiness.ready ? <CheckCircle2 /> : <AlertTriangle />}<AlertTitle>{data.readiness.ready ? 'Ready at latest read' : 'Not ready at latest read'}</AlertTitle><AlertDescription>These checks can become outdated. Meridian checks them again when you confirm readiness, and may identify a new blocker.</AlertDescription></Alert>{data.readiness.blockerCodes.length > 0 ? <ul className="space-y-2 text-sm">{data.readiness.blockerCodes.map((code) => <li key={code} className="rounded-md border p-3"><span>{blockerLabel(code)}</span></li>)}</ul> : <p className="text-sm text-muted-foreground">No readiness blockers are currently reported.</p>}{contract?.availableCustomerAction === 'ACKNOWLEDGE' || data.workStage === 'CUSTOMER_ACKNOWLEDGMENT_REQUIRED' ? <Alert variant="information"><Clock3 /><AlertTitle>Customer acknowledgment required</AlertTitle><AlertDescription>{data.originationChannel === 'STAFF_ASSISTED' ? 'The Customer must sign the acknowledgment for this exact version. An authorized Accounting Officer may record the signed acknowledgment below.' : 'The Customer must acknowledge this contract version in Customer Web. Staff cannot acknowledge it on their behalf.'}</AlertDescription></Alert> : null}</CardContent></Card>
    </div>
    {data.originationChannel === 'STAFF_ASSISTED' && data.workStage === 'CUSTOMER_ACKNOWLEDGMENT_REQUIRED' ? <Card><CardHeader><CardTitle>Signed Customer Contract Acknowledgment</CardTitle><p className="text-sm text-muted-foreground">Record the Customer&apos;s signed acknowledgment for the current contract version.</p></CardHeader><CardContent className="space-y-4"><label className="block text-sm font-semibold">Signed acknowledgment form<input className="mt-2 block w-full text-sm" type="file" accept="application/pdf,image/jpeg,image/png" onChange={(event) => setAcknowledgmentFile(event.target.files?.[0])} /></label><Button variant="outline" disabled={!assistedAcknowledgmentAvailable || !canUploadAssistedEvidence || !acknowledgmentFile || uploadingAcknowledgment} onClick={() => void uploadAcknowledgmentEvidence()}>{uploadingAcknowledgment ? <Spinner /> : null}{data.assistedAcknowledgmentEvidence ? 'Replace signed evidence' : 'Upload signed evidence'}</Button>{data.assistedAcknowledgmentEvidence ? <p className="text-sm">Current document version {data.assistedAcknowledgmentEvidence.versionNumber} · {data.assistedAcknowledgmentEvidence.detectedMimeType} · uploaded {formatTimestamp(data.assistedAcknowledgmentEvidence.uploadedAt)} · contract v{data.assistedAcknowledgmentEvidence.targetVersion}</p> : <p className="text-sm text-muted-foreground">No current signed evidence exists for this contract version.</p>}<label className="flex items-start gap-3 text-sm"><input className="mt-1" type="checkbox" checked={acknowledgmentConfirmed} onChange={(event) => setAcknowledgmentConfirmed(event.target.checked)} /><span>I confirm this signed form records the Customer&apos;s acknowledgment of contract {contract?.contractReference}, version {contract?.contractVersion}.</span></label><Button id="assisted-acknowledgment-trigger" disabled={!assistedAcknowledgmentAvailable || !acknowledgmentEvidenceMatches || !acknowledgmentConfirmed} onClick={openAssistedAcknowledgmentConfirmation}>Review signed acknowledgment</Button>{!acknowledgmentEvidenceMatches ? <p className="text-sm text-warning">The signed form must match this contract version. A form for an older version cannot be used.</p> : null}{unresolvedAcknowledgment ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Contract acknowledgment result unknown</AlertTitle><AlertDescription><p>Meridian could not confirm this acknowledgment. Retry this same acknowledgment before starting a different contract action.</p><Button className="mt-3" variant="outline" disabled={operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING'} onClick={() => void retryExact(unresolvedAcknowledgment)}>Retry this acknowledgment</Button></AlertDescription></Alert> : null}</CardContent></Card> : null}
    <Card><CardHeader><CardTitle>Accounting actions</CardTitle><p className="text-sm text-muted-foreground">If a result is uncertain, retry the same action with unchanged details. Other contract actions remain blocked until the result is confirmed.</p></CardHeader><CardContent className="space-y-5">{preparationAvailable ? <div className="space-y-2"><h3 className="font-semibold">{contract ? 'Refresh disbursement destination' : 'Prepare version 1'}</h3><p className="text-sm text-muted-foreground">{contract ? 'The current version will be superseded to refresh the disbursement destination. Accepted financial terms and repayment items remain unchanged; the eligible destination is captured again and the Customer must acknowledge the new version.' : 'Meridian prepares version 1 from the accepted offer and current eligible destination.'}</p><Button id="prepare-contract-trigger" onClick={openPreparation}>{contract ? 'Review regeneration' : 'Review preparation'}</Button></div> : null}{confirmationAvailable ? <div className="space-y-2 border-t pt-5"><h3 className="font-semibold">Confirm contract readiness</h3><p className="text-sm text-muted-foreground">Meridian checks readiness again for version {contract?.contractVersion}. Success makes the application ready for disbursement. It does not record a transfer or create a loan account.</p><Button id="confirm-readiness-trigger" onClick={openReadinessConfirmation}>Review readiness confirmation</Button></div> : null}{!preparationAvailable && !confirmationAvailable && !hasUnresolved ? <p className="text-sm text-muted-foreground">No Accounting action is available for the application's current state.</p> : null}{unresolvedPreparation ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Contract preparation result unknown</AlertTitle><AlertDescription><p>Meridian could not confirm contract preparation. Retry this preparation with the same version and reason.</p><Button className="mt-3" variant="outline" disabled={operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING'} onClick={() => void retryExact(unresolvedPreparation)}>Retry this action</Button></AlertDescription></Alert> : null}{unresolvedReadiness ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Readiness confirmation result unknown</AlertTitle><AlertDescription><p>The latest contract information does not confirm this readiness action. Retry this readiness confirmation for the same contract version.</p><Button className="mt-3" variant="outline" disabled={operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING'} onClick={() => void retryExact(unresolvedReadiness)}>Retry this action</Button></AlertDescription></Alert> : null}{staleEvidence ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Displayed version changed</AlertTitle><AlertDescription><p>The contract version changed. Review the refreshed contract before starting another action.</p><Button className="mt-3" variant="outline" onClick={() => setStaleEvidence(false)}>I reviewed the current contract</Button></AlertDescription></Alert> : null}{temporaryContract && confirmedRefreshFailed ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Action confirmed</AlertTitle><AlertDescription>Recorded result: contract v{temporaryContract.contractVersion}, {contractStatusLabel(temporaryContract.status)}. The latest contract information is still unavailable. Use Refresh, and do not submit the action again.</AlertDescription></Alert> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} headingId="contract-command-result" headingLabel={`Contract action result for ${data.applicationNumber}`} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent></Card>
    {confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="contract-confirm-title" aria-describedby="contract-confirm-description" onKeyDown={handleConfirmationKeyDown}><div className="w-full max-w-xl space-y-4 rounded-lg bg-card p-6 shadow-xl"><h2 id="contract-confirm-title" className="text-xl font-semibold">{confirmation.kind === 'PREPARATION' ? confirmation.payload.expectedCurrentContractVersion === 0 ? 'Confirm version 1 preparation' : 'Confirm contract replacement' : confirmation.kind === 'READINESS' ? 'Confirm readiness' : 'Confirm Customer acknowledgment'}</h2>{confirmation.kind === 'PREPARATION' ? confirmation.payload.expectedCurrentContractVersion === 0 ? <p id="contract-confirm-description" className="text-sm text-muted-foreground">Prepare version 1 from the accepted offer and current eligible destination. Meridian checks the latest offer, document, verification, Customer, destination, and access requirements.</p> : <p id="contract-confirm-description" className="text-sm text-muted-foreground">Supersede exact version {confirmation.payload.expectedCurrentContractVersion} to refresh the disbursement destination. Financial terms and repayment items remain unchanged. The new contract records the updated destination and requires a new Customer acknowledgment.</p> : confirmation.kind === 'READINESS' ? <p id="contract-confirm-description" className="text-sm text-muted-foreground">Meridian checks readiness again for exact version {confirmation.payload.expectedContractVersion}. Success means the application is ready for disbursement; it does not mean funds were transferred or a loan account was activated.</p> : <p id="contract-confirm-description" className="text-sm text-muted-foreground">Record the Customer&apos;s signed acknowledgment for contract {confirmation.payload.contractId}, version {confirmation.payload.expectedContractVersion}. You are the recording actor; the Customer remains the action subject.</p>}<div className="flex justify-end gap-2"><Button variant="outline" onClick={closeConfirmation}>Cancel</Button><Button autoFocus onClick={() => void submitConfirmed()}>Confirm contract action</Button></div></div></div> : null}
  </section>
}
