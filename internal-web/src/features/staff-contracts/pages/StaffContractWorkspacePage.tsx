import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Clock3, RefreshCw, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { RequestCorrelation } from '@/components/common/RequestCorrelation'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
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
  type LoanContract,
  type PreparationSemanticPayload,
  type ReadinessConfirmationSemanticPayload,
} from '../api/contracts'
import { staffContractCaseQuery, staffContractKeys } from '../api/queries'
import { confirmContractReadiness, prepareLoanContract } from '../api/staff-contracts-api'
import {
  blockerLabel,
  contractStageLabel,
  contractStatusLabel,
  hasCoherentContractLifecycle,
  knownContractStatuses,
  knownContractWorkStages,
  knownReadinessBlockers,
} from '../model/presentation'

type CommandKind = 'PREPARATION' | 'READINESS'
type Confirmation =
  | { kind: 'PREPARATION'; payload: PreparationSemanticPayload }
  | { kind: 'READINESS'; payload: ReadinessConfirmationSemanticPayload }
type OperationState = { status: OperationStatus; detail?: string; error?: Error }

const preparationType: UnresolvedOperationType = 'CONTRACT_PREPARATION'
const readinessType: UnresolvedOperationType = 'CONTRACT_READINESS_CONFIRMATION'

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-semibold">{children}</dd></div>
}

function operationType(kind: CommandKind) {
  return kind === 'PREPARATION' ? preparationType : readinessType
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
  const canReadApplication = state.status === 'authenticated' && hasPermission(state.actor, 'loan:read')
  const isAccounting = state.status === 'authenticated' && hasRole(state.actor, 'ACCOUNTING_OFFICER')
  const query = useQuery(staffContractCaseQuery(manager, loanApplicationId, canRead && validId))
  const [confirmation, setConfirmation] = useState<Confirmation>()
  const [operation, setOperation] = useState<OperationState>({ status: 'DRAFT' })
  const [staleEvidence, setStaleEvidence] = useState(false)
  const [confirmedRefreshFailed, setConfirmedRefreshFailed] = useState(false)
  const [temporaryContract, setTemporaryContract] = useState<LoanContract>()
  const resource = loanApplicationId
  const unresolvedPreparation = validId ? findUnresolvedOperation(preparationType, resource) : undefined
  const unresolvedReadiness = validId ? findUnresolvedOperation(readinessType, resource) : undefined
  const hasUnresolved = Boolean(unresolvedPreparation || unresolvedReadiness)

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
        detail: 'The command response and refreshed authoritative contract case are confirmed.',
      })
    } catch (error) {
      setConfirmedRefreshFailed(true)
      setOperation({
        status: 'BLOCKED',
        error: error instanceof Error ? error : new NetworkError(),
        detail: 'Command confirmed; refreshed state unavailable. Only the authoritative GET will be retried.',
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
        detail: 'Authoritative state was refreshed, but it cannot prove this exact request identity. Retry the exact operation with the retained UUID.',
      })
    } catch (error) {
      const reconciliationError = commandError instanceof ApiError && commandError.requestId
        ? commandError
        : error instanceof Error ? error : new NetworkError()
      setOperation({
        status: 'RESULT_UNKNOWN',
        error: reconciliationError,
        detail: 'The command result remains unknown. No POST was retried automatically.',
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
        : await confirmContractReadiness(manager, submitted.payload.loanApplicationId, {
          confirmationRequestId: operationId,
          expectedContractVersion: submitted.payload.expectedContractVersion,
        })
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
            ? 'The exact displayed version was rejected as stale. Refreshed evidence must be reviewed before a new logical operation.'
            : 'The backend rejected the command. No unresolved network operation was recorded.',
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
          ? 'The exact retry outcome is also unknown. The same UUID and payload remain retained.'
          : 'The command outcome is unknown. No automatic POST retry will occur; the exact UUID and payload are retained.',
      })
      await reconcileUnknownResult(commandError)
    }
  }

  const submitConfirmed = async () => {
    if (!confirmation) return
    const submitted = confirmation
    setConfirmation(undefined)
    const type = operationType(submitted.kind)
    const payloadDigest = await digestOperationPayload(submitted.payload)
    const identity = decideOperationIdentity(type, resource, payloadDigest)
    if (identity.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setOperation({
        status: 'BLOCKED',
        detail: 'A different semantic payload is still unresolved. Retry that exact operation before creating a new one.',
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
        detail: 'The retained operation payload cannot be verified. Do not create a replacement operation.',
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
        setOperation({ status: 'RESOLVED', detail: 'The confirmed command is now reconciled with authoritative state.' })
      }
    } catch {
      // React Query retains the query error and the last validated data for presentation.
    }
  }

  if (!validId) return <section className="mx-auto max-w-5xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Contract workspace unavailable</h1><Alert variant="warning"><Clock3 /><AlertTitle>Contract workspace unavailable</AlertTitle><AlertDescription>This route does not contain a valid application identifier.</AlertDescription></Alert></section>
  if (query.isPending) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Loading contract workspace</h1><div role="status" className="flex min-h-64 items-center justify-center gap-3 rounded-lg border bg-card"><Spinner /> Loading authoritative contract and readiness evidence…</div></section>
  const accountingRejected = query.error instanceof ApiError && query.error.errorCode === 'ACCOUNTING_ROLE_REQUIRED'
  if (query.isError && !query.data) return <section className="mx-auto max-w-6xl space-y-5"><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Contract and readiness workspace</h1>{accountingRejected ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>This workspace remains visible because your session has contract-read permission, but its operational read additionally requires the ACCOUNTING_OFFICER role.</AlertDescription></Alert> : <QueryErrorPanel error={query.error} resource="case" onRetry={() => void query.refetch()} />}</section>
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

  const closeConfirmation = () => {
    const kind = confirmation?.kind
    setConfirmation(undefined)
    setTimeout(() => document.getElementById(kind === 'PREPARATION' ? 'prepare-contract-trigger' : 'confirm-readiness-trigger')?.focus(), 0)
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
    <header className="rounded-lg border bg-card p-5 shadow-soft sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-sm font-semibold text-muted-foreground">CONTRACT AND READINESS</p><h1 data-route-heading tabIndex={-1} className="mt-1 text-2xl font-semibold sm:text-3xl">{data.applicationNumber}</h1><div className="mt-3 flex flex-wrap items-center gap-3"><StatusBadge status={data.applicationStatus} /><span className="text-sm text-muted-foreground">{productLabel(data.productCode)}</span><span className="text-sm font-semibold">{contractStageLabel(data.workStage)}</span></div></div><Button variant="outline" onClick={() => void refreshOnly()} disabled={query.isFetching}><RefreshCw className={query.isFetching ? 'animate-spin' : undefined} />Refresh</Button></div><dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Application ID"><span className="break-all">{data.loanApplicationId}</span></Fact><Fact label="Requested amount">{formatVnd(data.requestedAmount)}</Fact><Fact label="Requested term">{data.requestedTermMonths} months</Fact><Fact label="Submitted">{formatTimestamp(data.submittedAt)}</Fact></dl></header>
    {!isAccounting ? <Alert variant="warning"><ShieldAlert /><AlertTitle>Accounting authority required</AlertTitle><AlertDescription>Your permissions allow this route to remain visible. Preparation and readiness confirmation additionally require the ACCOUNTING_OFFICER role.</AlertDescription></Alert> : null}
    {query.isError ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Latest refresh unavailable</AlertTitle><AlertDescription>Cached contract evidence remains visible but cannot authorize a command.</AlertDescription></Alert> : null}
    {!safeEvidence ? <Alert variant="destructive"><AlertTriangle /><AlertTitle>Operational evidence unavailable</AlertTitle><AlertDescription>Unknown contract status, work stage, readiness semantics, or blocker values require an authoritative refresh. Consequential actions are disabled.</AlertDescription></Alert> : null}
    {data.workStage === 'READINESS_CONFIRMED' ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Readiness confirmed — not disbursed</AlertTitle><AlertDescription>The backend confirmed the exact contract and moved the application to DISBURSEMENT_PENDING. No bank transfer or LoanAccount activation is recorded here; those are later CP7 operations.</AlertDescription></Alert> : null}
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1.35fr)_minmax(18rem,.65fr)]">
      <Card><CardHeader><CardTitle>Current authoritative contract</CardTitle></CardHeader><CardContent>{contract ? <div className="space-y-6"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Reference">{contract.contractReference}</Fact><Fact label="Contract ID"><span className="break-all">{contract.contractId}</span></Fact><Fact label="Version">{contract.contractVersion}</Fact><Fact label="Status">{contractStatusLabel(contract.status)}</Fact><Fact label="Prepared">{formatTimestamp(contract.preparedAt)}</Fact><Fact label="Acknowledged">{formatTimestamp(contract.acknowledgedAt)}</Fact><Fact label="Readiness confirmed">{formatTimestamp(contract.readinessConfirmedAt)}</Fact></dl><div><h3 className="font-semibold">Immutable accepted terms</h3><dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Approved principal">{formatVnd(contract.approvedPrincipal)}</Fact><Fact label="Approved term">{contract.approvedTermMonths} months</Fact><Fact label="Interest method">{humanizeKnownValue(contract.interestCalculationMethod)}</Fact><Fact label="Flat monthly rate">{contract.flatMonthlyInterestRate}</Fact><Fact label="Total interest">{formatVnd(contract.totalInterest)}</Fact><Fact label="Fee">{formatVnd(contract.feeAmount)}</Fact><Fact label="Total repayment">{formatVnd(contract.totalRepaymentAmount)}</Fact><Fact label="Repayment method">{humanizeKnownValue(contract.repaymentMethod)}</Fact></dl></div><div><h3 className="font-semibold">Contract-bound masked destination</h3><dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><Fact label="Bank">{contract.disbursementBankAccount.bankName} ({contract.disbursementBankAccount.bankCode})</Fact><Fact label="Account holder">{contract.disbursementBankAccount.accountHolderName}</Fact><Fact label="Account number">{contract.disbursementBankAccount.maskedAccountNumber}</Fact><Fact label="Primary at capture">{contract.disbursementBankAccount.primaryAtCapture ? 'Yes' : 'No'}</Fact><Fact label="Active at capture">{contract.disbursementBankAccount.activeAtCapture ? 'Yes' : 'No'}</Fact><Fact label="Captured">{formatTimestamp(contract.disbursementBankAccount.capturedAt)}</Fact></dl></div><div><h3 className="font-semibold">Provisional repayment items</h3><div className="mt-3 overflow-x-auto rounded-md border"><table className="min-w-[42rem] w-full text-left text-sm"><thead className="bg-muted text-xs uppercase text-muted-foreground"><tr><th className="p-3">Item</th><th className="p-3">Principal</th><th className="p-3">Interest</th><th className="p-3">Fee</th><th className="p-3">Total</th></tr></thead><tbody>{contract.repaymentPreview.map((item) => <tr key={item.installmentNumber} className="border-t"><td className="p-3 font-semibold">{item.installmentNumber}</td><td className="p-3">{formatVnd(item.principalDue)}</td><td className="p-3">{formatVnd(item.interestDue)}</td><td className="p-3">{formatVnd(item.feeDue)}</td><td className="p-3 font-semibold">{formatVnd(item.totalDue)}</td></tr>)}</tbody></table></div></div></div> : <p className="text-sm text-muted-foreground">No current contract has been prepared.</p>}</CardContent></Card>
      <Card><CardHeader><CardTitle>Advisory readiness</CardTitle></CardHeader><CardContent className="space-y-5"><Alert variant={data.readiness.ready ? 'success' : 'warning'}>{data.readiness.ready ? <CheckCircle2 /> : <AlertTriangle />}<AlertTitle>{data.readiness.ready ? 'Ready at latest read' : 'Not ready at latest read'}</AlertTitle><AlertDescription>POINT_IN_TIME_ADVISORY evidence can become stale. Confirmation recomputes every readiness rule transactionally and may still conflict.</AlertDescription></Alert>{data.readiness.blockerCodes.length > 0 ? <ul className="space-y-2 text-sm">{data.readiness.blockerCodes.map((code) => <li key={code} className="rounded-md border p-3"><span className="font-semibold">{knownReadinessBlockers.has(code) ? humanizeKnownValue(code) : 'Unknown blocker'}</span><br /><span className="text-muted-foreground">{blockerLabel(code)}</span></li>)}</ul> : <p className="text-sm text-muted-foreground">The backend returned no readiness blockers.</p>}{contract?.availableCustomerAction === 'ACKNOWLEDGE' || data.workStage === 'CUSTOMER_ACKNOWLEDGMENT_REQUIRED' ? <Alert variant="information"><Clock3 /><AlertTitle>Customer acknowledgment required</AlertTitle><AlertDescription>The Customer must acknowledge this exact current version through the Customer-owned flow. Staff cannot perform that action.</AlertDescription></Alert> : null}</CardContent></Card>
    </div>
    <Card><CardHeader><CardTitle>Accounting commands</CardTitle><p className="text-sm text-muted-foreground">Every command binds a durable UUID to the exact displayed version and semantic payload.</p></CardHeader><CardContent className="space-y-5">{preparationAvailable ? <div className="space-y-2"><h3 className="font-semibold">{contract ? 'Regenerate contract destination' : 'Prepare version 1'}</h3><p className="text-sm text-muted-foreground">{contract ? 'The current version will be superseded only for DISBURSEMENT_ACCOUNT_REFRESH. Accepted financial terms and repayment items remain unchanged; the eligible destination is captured again and the Customer must acknowledge the new version.' : 'The backend will prepare version 1 from the accepted offer and current eligible destination using expected current version 0.'}</p><Button id="prepare-contract-trigger" onClick={openPreparation}>{contract ? 'Review regeneration' : 'Review preparation'}</Button></div> : null}{confirmationAvailable ? <div className="space-y-2 border-t pt-5"><h3 className="font-semibold">Confirm exact contract readiness</h3><p className="text-sm text-muted-foreground">The backend will recompute readiness for version {contract?.contractVersion}. Success moves the application to DISBURSEMENT_PENDING. It does not record a transfer or create a LoanAccount.</p><Button id="confirm-readiness-trigger" onClick={openReadinessConfirmation}>Review readiness confirmation</Button></div> : null}{!preparationAvailable && !confirmationAvailable && !hasUnresolved ? <p className="text-sm text-muted-foreground">No Accounting command is available for the authoritative current state.</p> : null}{unresolvedPreparation ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Contract preparation result unknown</AlertTitle><AlertDescription><p>No automatic POST retry occurred. Retry the exact operation with the retained UUID and unchanged version/reason payload.</p><Button className="mt-3" variant="outline" disabled={operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING'} onClick={() => void retryExact(unresolvedPreparation)}>Retry exact operation</Button></AlertDescription></Alert> : null}{unresolvedReadiness ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Readiness confirmation result unknown</AlertTitle><AlertDescription><p>Refreshed state alone cannot prove this request identity. Retry with the same UUID and exact expected version.</p><Button className="mt-3" variant="outline" disabled={operation.status === 'IN_FLIGHT' || operation.status === 'RECONCILING'} onClick={() => void retryExact(unresolvedReadiness)}>Retry exact operation</Button></AlertDescription></Alert> : null}{staleEvidence ? <Alert variant="warning"><AlertTriangle /><AlertTitle>Displayed version changed</AlertTitle><AlertDescription><p>The prior command remains bound to its old expected version. Review the refreshed contract before creating a new logical operation.</p><Button className="mt-3" variant="outline" onClick={() => setStaleEvidence(false)}>I reviewed the current contract</Button></AlertDescription></Alert> : null}{temporaryContract && confirmedRefreshFailed ? <Alert variant="success"><CheckCircle2 /><AlertTitle>Command response confirmed</AlertTitle><AlertDescription>Temporary response: contract v{temporaryContract.contractVersion}, {contractStatusLabel(temporaryContract.status)}. Canonical workspace refresh is still required.</AlertDescription></Alert> : null}{operation.status !== 'DRAFT' ? <OperationStatusPanel status={operation.status} /> : null}{operation.detail ? <p aria-live="polite" className="text-sm font-medium">{operation.detail}</p> : null}{operation.error instanceof ApiError && operation.error.requestId ? <RequestCorrelation requestId={operation.error.requestId} /> : null}</CardContent></Card>
    {confirmation ? <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog" aria-modal="true" aria-labelledby="contract-confirm-title" aria-describedby="contract-confirm-description" onKeyDown={handleConfirmationKeyDown}><div className="w-full max-w-xl space-y-4 rounded-lg bg-card p-6 shadow-xl"><h2 id="contract-confirm-title" className="text-xl font-semibold">{confirmation.kind === 'PREPARATION' ? confirmation.payload.expectedCurrentContractVersion === 0 ? 'Confirm version 1 preparation' : 'Confirm controlled regeneration' : 'Confirm readiness'}</h2>{confirmation.kind === 'PREPARATION' ? confirmation.payload.expectedCurrentContractVersion === 0 ? <p id="contract-confirm-description" className="text-sm text-muted-foreground">Prepare version 1 with expected current version 0 and no supersession reason. The backend validates every offer, document, verification, Customer, destination, and role rule.</p> : <p id="contract-confirm-description" className="text-sm text-muted-foreground">Supersede exact version {confirmation.payload.expectedCurrentContractVersion} for DISBURSEMENT_ACCOUNT_REFRESH. Financial terms and repayment items remain unchanged. A new destination snapshot is captured and new Customer acknowledgment is required.</p> : <p id="contract-confirm-description" className="text-sm text-muted-foreground">The backend will recompute readiness and confirm exact version {confirmation.payload.expectedContractVersion}. Success means DISBURSEMENT_PENDING, not transferred funds or an activated LoanAccount.</p>}<div className="flex justify-end gap-2"><Button variant="outline" onClick={closeConfirmation}>Cancel</Button><Button autoFocus onClick={() => void submitConfirmed()}>Confirm exact operation</Button></div></div></div> : null}
  </section>
}
