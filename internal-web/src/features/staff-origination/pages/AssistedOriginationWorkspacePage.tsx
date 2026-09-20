import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { OperationStatusPanel, type OperationStatus } from '@/components/operations/OperationStatusPanel'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/model/auth-context'
import { hasPermission } from '@/features/auth/model/access-control'
import { ApiError, NetworkError } from '@/lib/api'
import {
  decideOperationIdentity,
  digestFile,
  digestOperationPayload,
  findUnresolvedOperation,
  removeUnresolvedOperation,
  saveUnresolvedOperation,
  UnresolvedOperationConflictError,
} from '@/lib/operation/unresolved-operation'
import {
  abandonIntake, addBankAccount, attachCustomer, bankAction, createCustomer, getCustomer,
  getIntake, listBankAccounts, searchCustomer, submitCollateralIntake, submitUclIntake,
  updateCustomer, uploadEvidence,
} from '../api/staff-origination-api'
import { banksQuery, customerQuery, evidenceQuery, intakeCaseQuery, originationKeys } from '../api/queries'
import type { CustomerProfileInput, StaffCustomer } from '../api/contracts'
import {
  bankActionMatches, evidenceBaselineFrom, evidenceRecoveryResource, evidenceSemanticPayload,
  profileMatches,
} from '../model/recovery'
import { IntakeOcrReviewPanel } from '../components/IntakeOcrReviewPanel'

type ActionState = { status: OperationStatus; message?: string; error?: Error }
type IntakeEvidenceType = 'CUSTOMER_IDENTITY' | 'UCL_PAPER_APPLICATION' | 'COLLATERAL_PAPER_APPLICATION'
type OcrFormTarget = 'create-customer' | 'profile' | 'bank' | 'ucl' | 'collateral'
type MutableFormControl = HTMLInputElement | HTMLSelectElement
type AppliedOcrControl = {
  target: OcrFormTarget
  name: string
  evidenceType: IntakeEvidenceType
  versionId: string
  appliedValue: string
  previousValue: string
}

const collateralTypes = new Set(['MOTORBIKE', 'CAR', 'ELECTRONICS', 'PROPERTY_DOCUMENT', 'OTHER'])

function safePositiveInteger(value: string | undefined): string | undefined {
  if (!value || !/^[1-9]\d*$/.test(value.trim())) return undefined
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? value.trim() : undefined
}

function formControl(form: HTMLFormElement | null, name: string): MutableFormControl | undefined {
  const control = form?.elements.namedItem(name)
  return control instanceof HTMLInputElement || control instanceof HTMLSelectElement ? control : undefined
}

const profileFromForm = (form: HTMLFormElement): CustomerProfileInput & { identityReference?: string } => {
  const data = new FormData(form)
  return {
    fullName: String(data.get('fullName') ?? ''), identityReference: String(data.get('identityReference') ?? '') || undefined,
    phoneNumber: String(data.get('phoneNumber') ?? ''), residentialAddress: String(data.get('residentialAddress') ?? ''),
    employmentStatus: String(data.get('employmentStatus') ?? ''), employerName: String(data.get('employerName') ?? '') || undefined,
    termsConsentAccepted: data.get('termsConsentAccepted') === 'on', dataProcessingConsentAccepted: data.get('dataProcessingConsentAccepted') === 'on',
  }
}

function ProfileFields({ customer }: { customer?: StaffCustomer }) {
  const profile = customer?.profile
  return <div className="grid gap-3 sm:grid-cols-2">
    <label className="grid gap-1 text-sm font-medium">Full name<Input name="fullName" required defaultValue={profile?.fullName} /></label>
    <label className="grid gap-1 text-sm font-medium">Identity reference<Input name="identityReference" required={!customer} /></label>
    <label className="grid gap-1 text-sm font-medium">Phone<Input name="phoneNumber" required defaultValue={profile?.phoneNumber} /></label>
    <label className="grid gap-1 text-sm font-medium">Employment status<Input name="employmentStatus" required defaultValue={profile?.employmentStatus} /></label>
    <label className="grid gap-1 text-sm font-medium sm:col-span-2">Residential address<Input name="residentialAddress" required defaultValue={profile?.residentialAddress} /></label>
    <label className="grid gap-1 text-sm font-medium">Employer<Input name="employerName" defaultValue={profile?.employerName ?? ''} /></label>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" name="termsConsentAccepted" defaultChecked={profile?.termsConsentAccepted} /> Signed application consent recorded</label>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" name="dataProcessingConsentAccepted" defaultChecked={profile?.dataProcessingConsentAccepted} /> Data processing consent recorded</label>
  </div>
}

function ActionNotice({ action }: { action?: ActionState }) {
  if (!action || action.status === 'DRAFT') return null
  return <div className="space-y-2">
    <OperationStatusPanel status={action.status} />
    {action.message ? <p role="alert" className="text-sm text-muted-foreground">{action.message}</p> : null}
    {action.error instanceof ApiError ? <p role="alert" className="text-sm text-danger">{action.error.message}</p> : null}
  </div>
}

const locked = (action?: ActionState) => action?.status === 'IN_FLIGHT'
  || action?.status === 'RECONCILING'
  || action?.status === 'RESULT_UNKNOWN'
const busy = (action?: ActionState) => action?.status === 'IN_FLIGHT' || action?.status === 'RECONCILING'

export function AssistedOriginationWorkspacePage() {
  const { assistedOriginationCaseId = '' } = useParams()
  const { manager, state } = useAuth()
  const client = useQueryClient()
  const navigate = useNavigate()
  const actor = state.status === 'authenticated' ? state.actor : undefined
  const canOriginate = Boolean(actor && hasPermission(actor, 'loan:originate:staff'))
  const canCustomer = Boolean(actor && hasPermission(actor, 'customer:intake:manage') && hasPermission(actor, 'customer:read'))
  const canEvidence = Boolean(actor && hasPermission(actor, 'document:upload:intake'))
  const intake = useQuery(intakeCaseQuery(manager, assistedOriginationCaseId, canOriginate && Boolean(assistedOriginationCaseId)))
  const customerId = intake.data?.customerId ?? ''
  const customer = useQuery(customerQuery(manager, customerId, canCustomer && Boolean(customerId)))
  const banks = useQuery(banksQuery(manager, customerId, canCustomer && Boolean(customerId)))
  const evidence = useQuery(evidenceQuery(manager, assistedOriginationCaseId, canEvidence && Boolean(assistedOriginationCaseId)))
  const [searchResult, setSearchResult] = useState<StaffCustomer>()
  const [actions, setActions] = useState<Record<string, ActionState>>({})
  const [ocrFreshnessMessage, setOcrFreshnessMessage] = useState<string>()
  const createCustomerForm = useRef<HTMLFormElement>(null)
  const profileForm = useRef<HTMLFormElement>(null)
  const bankForm = useRef<HTMLFormElement>(null)
  const uclForm = useRef<HTMLFormElement>(null)
  const collateralForm = useRef<HTMLFormElement>(null)
  const appliedOcrControls = useRef(new Map<string, AppliedOcrControl>())
  const search = useMutation({ mutationFn: (input: { customerNumber?: string; identityReference?: string }) => searchCustomer(manager, input), onSuccess: setSearchResult })

  const ocrTargetForm = useCallback((target: OcrFormTarget): HTMLFormElement | null => ({
    'create-customer': createCustomerForm.current,
    profile: profileForm.current,
    bank: bankForm.current,
    ucl: uclForm.current,
    collateral: collateralForm.current,
  })[target], [])

  const clearStaleAppliedOcr = useCallback((evidenceType: string, currentVersionId: string | null) => {
    let removed = false
    for (const [key, applied] of appliedOcrControls.current) {
      if (applied.evidenceType === evidenceType && applied.versionId !== currentVersionId) {
        const control = formControl(ocrTargetForm(applied.target), applied.name)
        if (control?.value === applied.appliedValue) control.value = applied.previousValue
        appliedOcrControls.current.delete(key)
        removed = true
      }
    }
    return removed
  }, [ocrTargetForm])

  useEffect(() => {
    if (!evidence.data) return
    for (const item of evidence.data) {
      clearStaleAppliedOcr(item.evidenceType, item.currentVersionId)
    }
  }, [clearStaleAppliedOcr, evidence.data])

  const setAction = (key: string, action: ActionState) => setActions((value) => ({ ...value, [key]: action }))
  const refresh = async (nextCustomerId = customerId) => {
    const work = [
      client.invalidateQueries({ queryKey: originationKeys.case(assistedOriginationCaseId) }),
      client.invalidateQueries({ queryKey: originationKeys.list() }),
      client.invalidateQueries({ queryKey: originationKeys.evidence(assistedOriginationCaseId) }),
    ]
    if (nextCustomerId) work.push(
      client.invalidateQueries({ queryKey: originationKeys.customer(nextCustomerId) }),
      client.invalidateQueries({ queryKey: originationKeys.banks(nextCustomerId) }),
    )
    await Promise.all(work)
  }
  const refreshConfirmed = async (nextCustomerId = customerId) => {
    try {
      await refresh(nextCustomerId)
      return true
    } catch {
      return false
    }
  }
  const readCase = async () => {
    const value = await getIntake(manager, assistedOriginationCaseId)
    client.setQueryData(originationKeys.case(assistedOriginationCaseId), value)
    return value
  }
  const readCustomer = async (id: string) => {
    const value = await getCustomer(manager, id)
    client.setQueryData(originationKeys.customer(id), value)
    return value
  }
  const readBanks = async (id: string) => {
    const value = await listBankAccounts(manager, id)
    client.setQueryData(originationKeys.banks(id), value)
    return value
  }

  const applyReviewedOcrValues = (source: {
    evidenceType: IntakeEvidenceType
    versionId: string
    reviewedFields: Record<string, string>
  }): number => {
    const targetProfile: OcrFormTarget = customer.data ? 'profile' : 'create-customer'
    let appliedCount = 0
    const apply = (target: OcrFormTarget, name: string, value: string | undefined) => {
      if (value === undefined) return
      const control = formControl(ocrTargetForm(target), name)
      if (!control) return
      const key = `${target}:${name}`
      const previous = appliedOcrControls.current.get(key)
      appliedOcrControls.current.set(key, {
        target,
        name,
        evidenceType: source.evidenceType,
        versionId: source.versionId,
        appliedValue: value,
        previousValue: previous?.previousValue ?? control.value,
      })
      control.value = value
      appliedCount += 1
    }

    for (const field of ['fullName', 'phoneNumber', 'residentialAddress', 'employmentStatus', 'employerName'] as const) {
      apply(targetProfile, field, source.reviewedFields[field])
    }
    if (!customer.data || customer.data.profileCompletionStatus !== 'COMPLETE') {
      apply(targetProfile, 'identityReference', source.reviewedFields.identityReference)
    }
    for (const field of ['bankCode', 'bankNameSnapshot', 'accountHolderName', 'accountNumber'] as const) {
      apply('bank', field, source.reviewedFields[field])
    }

    if (source.evidenceType === 'UCL_PAPER_APPLICATION') {
      apply('ucl', 'requestedAmount', safePositiveInteger(source.reviewedFields.requestedAmount))
      apply('ucl', 'requestedTermMonths', safePositiveInteger(source.reviewedFields.requestedTermMonths))
    }
    if (source.evidenceType === 'COLLATERAL_PAPER_APPLICATION') {
      apply('collateral', 'requestedAmount', safePositiveInteger(source.reviewedFields.requestedAmount))
      apply('collateral', 'requestedTermMonths', safePositiveInteger(source.reviewedFields.requestedTermMonths))
      const collateralType = source.reviewedFields['collateral.type']
      apply('collateral', 'collateralType', collateralType && collateralTypes.has(collateralType) ? collateralType : undefined)
      apply('collateral', 'description', source.reviewedFields['collateral.description'])
      apply('collateral', 'estimatedValue', safePositiveInteger(source.reviewedFields['collateral.estimatedValue']))
      apply('collateral', 'ownershipStatus', source.reviewedFields['collateral.ownershipStatus'])
      apply('collateral', 'conditionNote', source.reviewedFields['collateral.conditionNote'])
    }
    setOcrFreshnessMessage(undefined)
    return appliedCount
  }

  if (intake.isPending) return <p className="flex items-center gap-2"><Spinner /> Loading intake workspace…</p>
  if (intake.isError || !intake.data) return <section><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold">Intake unavailable</h1><Button className="mt-4" asChild variant="outline"><Link to="/staff/origination">Back to intake</Link></Button></section>
  const open = intake.data.status === 'OPEN'
  const selectedEvidenceType = intake.data.productCode === 'UNSECURED_CONSUMER_LOAN' ? 'UCL_PAPER_APPLICATION' : 'COLLATERAL_PAPER_APPLICATION'

  const associate = async (targetCustomerId: string) => {
    const key = 'customer-association'
    setAction(key, { status: 'IN_FLIGHT' })
    try {
      const value = await attachCustomer(manager, assistedOriginationCaseId, targetCustomerId)
      client.setQueryData(originationKeys.case(assistedOriginationCaseId), value)
      const refreshed = await refreshConfirmed(targetCustomerId)
      setAction(key, { status: 'RESOLVED', message: refreshed ? undefined : 'The association was confirmed, but refreshed state is temporarily unavailable.' })
      setActions((valueActions) => ({ ...valueActions, 'customer-creation': { status: 'RESOLVED' } }))
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try {
        const authoritative = await readCase()
        if (authoritative.customerId === targetCustomerId) {
          await refreshConfirmed(targetCustomerId)
          setAction(key, { status: 'RESOLVED', message: 'The selected Customer was confirmed from the authoritative intake.' })
        } else {
          setAction(key, { status: 'RESULT_UNKNOWN', message: 'The association result is unresolved. The command was not repeated.' })
        }
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The association result is unresolved. The command was not repeated.' })
      }
    }
  }

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const mode = String(data.get('mode'))
    const value = String(data.get('value'))
    search.mutate(mode === 'number' ? { customerNumber: value } : { identityReference: value })
  }

  const submitCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const input = profileFromForm(event.currentTarget)
    if (!input.identityReference) return
    const key = 'customer-creation'
    setAction(key, { status: 'IN_FLIGHT' })
    let created: StaffCustomer
    try {
      created = await createCustomer(manager, input as CustomerProfileInput & { identityReference: string })
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try {
        const discovered = await searchCustomer(manager, { identityReference: input.identityReference })
        setSearchResult(discovered)
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The create response was lost. A matching Customer was found; review and select that Customer. No create command was repeated.' })
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The Customer creation result is unresolved. No create command was repeated.' })
      }
      return
    }
    setSearchResult(created)
    try {
      const selected = await attachCustomer(manager, assistedOriginationCaseId, created.customerId)
      client.setQueryData(originationKeys.case(assistedOriginationCaseId), selected)
      const refreshed = await refreshConfirmed(created.customerId)
      setAction(key, { status: 'RESOLVED', message: refreshed ? undefined : 'The Customer was created and selected, but refreshed state is temporarily unavailable.' })
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'RESOLVED', message: 'The Customer was created. Review and select that Customer without creating another record.' })
        setAction('customer-association', { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction('customer-association', { status: 'RECONCILING' })
      try {
        const authoritative = await readCase()
        if (authoritative.customerId === created.customerId) {
          await refreshConfirmed(created.customerId)
          setAction(key, { status: 'RESOLVED' })
          setAction('customer-association', { status: 'RESOLVED' })
        } else {
          setAction(key, { status: 'RESULT_UNKNOWN', message: 'The Customer was created, but selection is unresolved. The association command was not repeated.' })
          setAction('customer-association', { status: 'RESULT_UNKNOWN' })
        }
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The Customer was created, but selection is unresolved. The association command was not repeated.' })
        setAction('customer-association', { status: 'RESULT_UNKNOWN' })
      }
    }
  }

  const submitProfile = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const input = profileFromForm(event.currentTarget)
    const key = 'customer-profile'
    setAction(key, { status: 'IN_FLIGHT' })
    try {
      const value = await updateCustomer(manager, customerId, input)
      client.setQueryData(originationKeys.customer(customerId), value)
      const refreshed = await refreshConfirmed()
      setAction(key, { status: 'RESOLVED', message: refreshed ? undefined : 'The profile update was confirmed, but refreshed state is temporarily unavailable.' })
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try {
        const authoritative = await readCustomer(customerId)
        setAction(key, profileMatches(authoritative, input)
          ? { status: 'RESOLVED', message: 'The saved profile was confirmed from authoritative safe fields.' }
          : { status: 'RESULT_UNKNOWN', message: 'The profile result cannot be proven from the safe projection. The update was not repeated.' })
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The profile result is unresolved. The update was not repeated.' })
      }
    }
  }

  const submitBank = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = event.currentTarget
    const data = Object.fromEntries(new FormData(form)) as Record<string, string>
    const key = 'bank-mutation'
    setAction(key, { status: 'IN_FLIGHT' })
    try {
      await addBankAccount(manager, customerId, data)
      form.reset()
      const refreshed = await refreshConfirmed()
      setAction(key, { status: 'RESOLVED', message: refreshed ? undefined : 'The bank account was confirmed, but refreshed state is temporarily unavailable.' })
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try { await readBanks(customerId) } catch { /* The result remains unresolved. */ }
      setAction(key, { status: 'RESULT_UNKNOWN', message: 'The safe bank projection cannot prove this add request. The command was not repeated.' })
    }
  }

  const mutateBank = async (bankId: string, action: 'make-primary' | 'deactivate') => {
    const key = 'bank-mutation'
    setAction(key, { status: 'IN_FLIGHT' })
    try {
      await bankAction(manager, customerId, bankId, action)
      const refreshed = await refreshConfirmed()
      setAction(key, { status: 'RESOLVED', message: refreshed ? undefined : 'The bank-account change was confirmed, but refreshed state is temporarily unavailable.' })
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try {
        const authoritative = await readBanks(customerId)
        setAction(key, bankActionMatches(authoritative, bankId, action)
          ? { status: 'RESOLVED', message: 'The bank-account state was confirmed from the authoritative projection.' }
          : { status: 'RESULT_UNKNOWN', message: 'The bank-account result is unresolved. The command was not repeated.' })
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The bank-account result is unresolved. The command was not repeated.' })
      }
    }
  }

  const submitEvidence = (evidenceType: string) => async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = event.currentTarget
    const fileInput = form.elements.namedItem('file') as HTMLInputElement | null
    const file = fileInput?.files?.[0]
    if (!file || file.size === 0) return
    const resource = evidenceRecoveryResource(assistedOriginationCaseId, evidenceType)
    const unresolved = findUnresolvedOperation('INTAKE_EVIDENCE_UPLOAD', resource)
    const storedBaseline = evidenceBaselineFrom(unresolved)
    if (unresolved && storedBaseline === undefined) {
      setAction(resource, { status: 'RESULT_UNKNOWN', message: 'Recovery metadata is incomplete. Reconcile the prior upload before continuing.' })
      return
    }
    const baseline = unresolved
      ? storedBaseline!
      : evidence.data?.find((item) => item.evidenceType === evidenceType)?.currentVersionId ?? null
    const payloadDigest = await digestOperationPayload(evidenceSemanticPayload(
      assistedOriginationCaseId, evidenceType, baseline, file, await digestFile(file),
    ))
    const decision = decideOperationIdentity('INTAKE_EVIDENCE_UPLOAD', resource, payloadDigest)
    if (decision.kind === 'CONFLICT_WITH_UNRESOLVED') {
      setAction(resource, { status: 'RESULT_UNKNOWN', error: new UnresolvedOperationConflictError(), message: 'The selected file or original baseline does not match the unresolved upload. No upload was sent.' })
      return
    }
    const operationId = decision.operationId
    setAction(resource, { status: 'IN_FLIGHT' })
    try {
      const uploaded = await uploadEvidence(manager, assistedOriginationCaseId, evidenceType, file, operationId, baseline ?? undefined)
      if (clearStaleAppliedOcr(evidenceType, uploaded.intakeDocumentVersionId)) {
        setOcrFreshnessMessage('OCR-applied values from a replaced evidence version were removed. Review the current evidence before applying again.')
      }
    } catch (caught) {
      const error = caught as Error
      if (error instanceof NetworkError || (error instanceof ApiError && error.errorCode === 'IDEMPOTENCY_KEY_REUSED')) {
        saveUnresolvedOperation({
          type: 'INTAKE_EVIDENCE_UPLOAD', resource, operationId, payloadDigest,
          unresolvedAt: new Date().toISOString(), semanticPayload: { baseline },
        })
        setAction(resource, {
          status: 'RESULT_UNKNOWN', error,
          message: error instanceof ApiError
            ? 'The request identity conflicts with server evidence. It was retained for reconciliation and was not replaced.'
            : 'The upload result is unknown. Reselect this exact file to retry with the retained operation identity.',
        })
        return
      }
      removeUnresolvedOperation('INTAKE_EVIDENCE_UPLOAD', resource)
      if (error instanceof ApiError && error.errorCode === 'STALE_DOCUMENT_VERSION') {
        try { await client.invalidateQueries({ queryKey: originationKeys.evidence(assistedOriginationCaseId) }) } catch { /* Operator review remains required. */ }
        setAction(resource, { status: 'BLOCKED', error, message: 'Evidence changed. Review the refreshed version before starting a new upload.' })
        return
      }
      setAction(resource, { status: 'BLOCKED', error })
      return
    }
    removeUnresolvedOperation('INTAKE_EVIDENCE_UPLOAD', resource)
    setAction(resource, { status: 'RECONCILING' })
    let refreshed = true
    try { await client.invalidateQueries({ queryKey: originationKeys.evidence(assistedOriginationCaseId) }) } catch { refreshed = false }
    setAction(resource, { status: 'RESOLVED', message: refreshed ? undefined : 'The upload was confirmed, but refreshed evidence is temporarily unavailable.' })
    form.reset()
  }

  const submitUcl = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const input = {
      requestedAmount: Number(data.get('requestedAmount')),
      requestedTermMonths: Number(data.get('requestedTermMonths')),
    }
    const key = 'ucl-conversion'
    if (!window.confirm('Create the real UCL Loan Application for this Customer?')) return
    setAction(key, { status: 'IN_FLIGHT' })
    try {
      const completed = await submitUclIntake(manager, assistedOriginationCaseId, input)
      client.setQueryData(originationKeys.case(assistedOriginationCaseId), completed)
      await refreshConfirmed()
      setAction(key, { status: 'RESOLVED' })
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try {
        const authoritative = await readCase()
        if (authoritative.status === 'COMPLETED' && authoritative.loanApplicationId) {
          await refreshConfirmed()
          setAction(key, { status: 'RESOLVED', message: 'Application creation was confirmed from the authoritative intake.' })
        } else if (authoritative.status === 'OPEN') {
          setAction(key, { status: 'RESULT_UNKNOWN', message: 'The conversion result is unresolved. No submission was repeated; review the intake and confirm a new attempt explicitly.' })
        } else {
          setAction(key, { status: 'BLOCKED', message: 'The intake became terminal without a resulting Loan Application.' })
        }
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The conversion result is unknown. No submission was repeated.' })
      }
    }
  }

  const submitCollateral = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const input = {
      requestedAmount: Number(data.get('requestedAmount')),
      requestedTermMonths: Number(data.get('requestedTermMonths')),
      collateral: {
        type: String(data.get('collateralType')) as 'MOTORBIKE' | 'CAR' | 'ELECTRONICS' | 'PROPERTY_DOCUMENT' | 'OTHER',
        description: String(data.get('description')),
        estimatedValue: Number(data.get('estimatedValue')),
        ownershipStatus: String(data.get('ownershipStatus')),
        conditionNote: String(data.get('conditionNote')),
      },
    }
    const key = 'collateral-conversion'
    if (!window.confirm('Create the real Collateral Loan Application for this Customer?')) return
    setAction(key, { status: 'IN_FLIGHT' })
    try {
      const completed = await submitCollateralIntake(manager, assistedOriginationCaseId, input)
      client.setQueryData(originationKeys.case(assistedOriginationCaseId), completed)
      await refreshConfirmed()
      setAction(key, { status: 'RESOLVED' })
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try {
        const authoritative = await readCase()
        if (authoritative.status === 'COMPLETED' && authoritative.loanApplicationId) {
          await refreshConfirmed()
          setAction(key, { status: 'RESOLVED', message: 'Application creation was confirmed from the authoritative intake.' })
        } else if (authoritative.status === 'OPEN') {
          setAction(key, { status: 'RESULT_UNKNOWN', message: 'The conversion result is unresolved. No submission was repeated; review the intake and confirm a new attempt explicitly.' })
        } else {
          setAction(key, { status: 'BLOCKED', message: 'The intake became terminal without a resulting Loan Application.' })
        }
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The conversion result is unknown. No submission was repeated.' })
      }
    }
  }

  const abandon = async () => {
    const key = 'abandon-intake'
    setAction(key, { status: 'IN_FLIGHT' })
    try {
      await abandonIntake(manager, assistedOriginationCaseId)
      const refreshed = await refreshConfirmed()
      setAction(key, { status: 'RESOLVED', message: refreshed ? undefined : 'Abandonment was confirmed, but refreshed state is temporarily unavailable.' })
      navigate('/staff/origination')
    } catch (caught) {
      if (!(caught instanceof NetworkError)) {
        setAction(key, { status: 'BLOCKED', error: caught as Error })
        return
      }
      setAction(key, { status: 'RECONCILING' })
      try {
        const authoritative = await readCase()
        if (authoritative.status === 'ABANDONED') {
          setAction(key, { status: 'RESOLVED', message: 'Abandonment was confirmed from the authoritative intake.' })
          navigate('/staff/origination')
        } else {
          setAction(key, { status: 'RESULT_UNKNOWN', message: 'The abandonment result is unresolved. The command was not repeated.' })
        }
      } catch {
        setAction(key, { status: 'RESULT_UNKNOWN', message: 'The abandonment result is unresolved. The command was not repeated.' })
      }
    }
  }

  return <section className="mx-auto max-w-6xl space-y-6">
    <div><Button asChild variant="link"><Link to="/staff/origination">← Paper intake</Link></Button><h1 data-route-heading tabIndex={-1} className="text-2xl font-semibold sm:text-3xl">{intake.data.productCode === 'UNSECURED_CONSUMER_LOAN' ? 'UCL' : 'Collateral Loan'} intake</h1><p className="mt-1 text-muted-foreground">Status: {intake.data.status}{intake.data.loanApplicationId ? ` · Loan Application ${intake.data.loanApplicationId}` : ' · No LoanApplication exists for this intake.'}</p>{intake.data.loanApplicationId ? <Button className="mt-3" asChild><Link to={`/staff/applications/${intake.data.loanApplicationId}/documents`}>Open application documents</Link></Button> : null}</div>
    {!open ? <div className="rounded-lg border border-warning/40 bg-warning/10 p-4">This intake is terminal. Customer association and evidence upload are disabled.</div> : null}

    {canCustomer ? <div className="grid gap-6 lg:grid-cols-2">
      <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Find Customer</h2><form onSubmit={submitSearch} className="space-y-3"><label className="grid gap-1 text-sm font-medium">Exact search<select name="mode" className="h-11 rounded-md border bg-background px-3"><option value="number">Customer number</option><option value="identity">Identity reference</option></select></label><label className="grid gap-1 text-sm font-medium">Exact value<Input name="value" required autoComplete="off" /></label><Button disabled={!open || search.isPending}>Search</Button></form>{searchResult ? <div className="rounded-md border p-3"><p className="font-semibold">{searchResult.customerNumber} · {searchResult.profile?.fullName}</p><Button className="mt-2" disabled={!open || locked(actions['customer-association'])} onClick={() => void associate(searchResult.customerId)}>Select Customer</Button></div> : null}<ActionNotice action={actions['customer-association']} /></article>
      {!customerId ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Create Customer</h2><form ref={createCustomerForm} onSubmit={(event) => void submitCreate(event)} className="space-y-4"><ProfileFields /><Button disabled={!open || locked(actions['customer-creation']) || actions['customer-creation']?.status === 'RESOLVED'}>Create and select Customer</Button></form><ActionNotice action={actions['customer-creation']} /></article> : null}
    </div> : <p className="rounded-lg border p-4 text-sm">Customer intake permissions are required to find or maintain the selected Customer.</p>}

    {customer.isPending && customerId && canCustomer ? <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading selected Customer…</p> : null}
    {customer.isError ? <div className="rounded-lg border border-danger/30 p-4"><p role="alert">The selected Customer could not be loaded.</p><Button className="mt-3" variant="outline" onClick={() => void customer.refetch()}>Retry Customer</Button></div> : null}
    {customer.data ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Selected Customer · {customer.data.customerNumber}</h2><form ref={profileForm} onSubmit={(event) => void submitProfile(event)} className="space-y-4"><ProfileFields customer={customer.data} /><Button disabled={!open || locked(actions['customer-profile'])}>Save profile</Button></form><ActionNotice action={actions['customer-profile']} /></article> : null}

    {customer.data ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Bank accounts</h2>{banks.isPending ? <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading bank accounts…</p> : null}{banks.isError ? <div><p role="alert">Bank accounts could not be loaded.</p><Button className="mt-2" variant="outline" onClick={() => void banks.refetch()}>Retry bank accounts</Button></div> : null}{banks.data?.length === 0 ? <p className="text-sm text-muted-foreground">No bank accounts have been recorded.</p> : null}{banks.data?.map((bank) => <div key={bank.customerBankAccountId} className="flex flex-wrap items-center justify-between gap-3 rounded-md border p-3"><span>{bank.bankNameSnapshot} · {bank.maskedAccountNumber} · {bank.status}{bank.primaryAccount ? ' · Primary' : ''}</span><div className="flex gap-2">{bank.status === 'ACTIVE' && !bank.primaryAccount ? <Button size="sm" variant="outline" disabled={!open || locked(actions['bank-mutation'])} onClick={() => void mutateBank(bank.customerBankAccountId, 'make-primary')}>Make primary</Button> : null}<Button size="sm" variant="outline" disabled={!open || bank.status !== 'ACTIVE' || locked(actions['bank-mutation'])} onClick={() => void mutateBank(bank.customerBankAccountId, 'deactivate')}>Deactivate</Button></div></div>)}<form ref={bankForm} onSubmit={(event) => void submitBank(event)} className="grid gap-3 sm:grid-cols-2"><Input name="bankCode" required aria-label="Bank code" placeholder="Bank code" /><Input name="bankNameSnapshot" required aria-label="Bank name" placeholder="Bank name" /><Input name="accountHolderName" required aria-label="Account holder" placeholder="Account holder" /><Input name="accountNumber" required aria-label="Account number" placeholder="Account number" autoComplete="off" /><Button disabled={!open || locked(actions['bank-mutation'])}>Add bank account</Button></form><ActionNotice action={actions['bank-mutation']} /></article> : null}

    {canEvidence ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Paper intake evidence</h2>{ocrFreshnessMessage ? <p role="alert" className="text-sm text-warning">{ocrFreshnessMessage}</p> : null}{evidence.isPending ? <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Loading evidence metadata…</p> : null}{evidence.isError ? <div><p role="alert">Evidence metadata could not be loaded.</p><Button className="mt-2" variant="outline" onClick={() => void evidence.refetch()}>Retry evidence</Button></div> : null}{evidence.isSuccess ? ['CUSTOMER_IDENTITY', selectedEvidenceType].map((type) => { const item = evidence.data.find((candidate) => candidate.evidenceType === type); const label = type === 'CUSTOMER_IDENTITY' ? 'Customer identity / CCCD' : 'Signed paper application'; const resource = evidenceRecoveryResource(assistedOriginationCaseId, type); const unresolved = findUnresolvedOperation('INTAKE_EVIDENCE_UPLOAD', resource); return <div key={type} className="space-y-3 rounded-md border p-3"><h3 className="font-medium">{label}</h3><p className="text-sm text-muted-foreground">{item ? `${item.versions.length} version(s); current ${item.currentVersionId}` : 'No evidence uploaded'}</p>{unresolved ? <p className="text-sm font-medium text-warning">A prior upload result is unresolved. Reselect the exact file to retry it.</p> : null}<form className="flex flex-col gap-2 sm:flex-row" onSubmit={submitEvidence(type)}><Input aria-label={`${label} file`} type="file" name="file" required accept="application/pdf,image/jpeg,image/png" /><Button type="submit" disabled={!open || busy(actions[resource])}>{item ? 'Replace evidence' : 'Upload evidence'}</Button></form><ActionNotice action={actions[resource]} />{item?.currentVersionId ? <IntakeOcrReviewPanel key={`${type}:${item.currentVersionId}`} manager={manager} caseId={assistedOriginationCaseId} evidenceType={type} versionId={item.currentVersionId} intakeOpen={open} onApplyReviewedValues={applyReviewedOcrValues} /> : null}</div> }) : null}</article> : null}

    {open && intake.data.productCode === 'UNSECURED_CONSUMER_LOAN' ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Create UCL application</h2><p className="text-sm text-muted-foreground">This consequential action creates the real Staff-assisted Loan Application and its required application-document checklist.</p><ul className="text-sm"><li>Selected Customer: {customer.data ? 'ready to evaluate' : 'not available'}</li><li>Profile: {customer.data?.profileCompletionStatus === 'COMPLETE' ? 'complete' : 'incomplete'}</li><li>Primary active bank account: {customer.data?.primaryActiveBankAccountPresent ? 'present' : 'missing'}</li><li>Signed UCL paper application: {evidence.data?.some((item) => item.evidenceType === 'UCL_PAPER_APPLICATION' && item.currentVersionId) ? 'present' : 'missing'}</li></ul><form ref={uclForm} className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submitUcl(event)}><label className="grid gap-1 text-sm font-medium">Requested amount<Input name="requestedAmount" type="number" min="1" step="1" required /></label><label className="grid gap-1 text-sm font-medium">Requested term months<Input name="requestedTermMonths" type="number" min="1" step="1" required /></label><Button className="sm:col-span-2" disabled={locked(actions['ucl-conversion']) || !customer.data || customer.data.status !== 'ACTIVE' || customer.data.profileCompletionStatus !== 'COMPLETE' || !customer.data.primaryActiveBankAccountPresent || !evidence.data?.some((item) => item.evidenceType === 'UCL_PAPER_APPLICATION' && item.currentVersionId)}>Create UCL application</Button></form><ActionNotice action={actions['ucl-conversion']} /></article> : null}
    {open && intake.data.productCode === 'COLLATERAL_LOAN' ? <article className="space-y-4 rounded-lg border bg-card p-5"><h2 className="text-lg font-semibold">Create Collateral Loan application</h2><p className="text-sm text-muted-foreground">This consequential action creates the real Staff-assisted Loan Application, one structured Collateral fact, and its ownership-evidence checklist.</p><ul className="text-sm"><li>Selected Customer: {customer.data ? 'ready to evaluate' : 'not available'}</li><li>Profile: {customer.data?.profileCompletionStatus === 'COMPLETE' ? 'complete' : 'incomplete'}</li><li>Primary active bank account: {customer.data?.primaryActiveBankAccountPresent ? 'present' : 'missing'}</li><li>Signed Collateral paper application: {evidence.data?.some((item) => item.evidenceType === 'COLLATERAL_PAPER_APPLICATION' && item.currentVersionId) ? 'present' : 'missing'}</li></ul><form ref={collateralForm} className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submitCollateral(event)}><label className="grid gap-1 text-sm font-medium">Requested amount<Input name="requestedAmount" type="number" min="1" step="1" required /></label><label className="grid gap-1 text-sm font-medium">Requested term months<Input name="requestedTermMonths" type="number" min="1" step="1" required /></label><label className="grid gap-1 text-sm font-medium">Collateral type<select name="collateralType" required className="h-11 rounded-md border bg-background px-3"><option value="MOTORBIKE">Motorbike</option><option value="CAR">Car</option><option value="ELECTRONICS">Electronics</option><option value="PROPERTY_DOCUMENT">Property document</option><option value="OTHER">Other</option></select></label><label className="grid gap-1 text-sm font-medium">Estimated value<Input name="estimatedValue" type="number" min="1" step="1" required /></label><label className="grid gap-1 text-sm font-medium sm:col-span-2">Description<Input name="description" maxLength={500} required /></label><label className="grid gap-1 text-sm font-medium">Ownership status<Input name="ownershipStatus" maxLength={200} required /></label><label className="grid gap-1 text-sm font-medium">Condition note<Input name="conditionNote" maxLength={500} required /></label><Button className="sm:col-span-2" disabled={locked(actions['collateral-conversion']) || !customer.data || customer.data.status !== 'ACTIVE' || customer.data.profileCompletionStatus !== 'COMPLETE' || !customer.data.primaryActiveBankAccountPresent || !evidence.data?.some((item) => item.evidenceType === 'COLLATERAL_PAPER_APPLICATION' && item.currentVersionId)}>Create Collateral Loan application</Button></form><ActionNotice action={actions['collateral-conversion']} /></article> : null}

    {open ? <div className="space-y-3 rounded-lg border border-danger/30 p-4"><h2 className="font-semibold">Abandon intake</h2><p className="mt-1 text-sm text-muted-foreground">Abandonment is terminal and blocks further Customer association and paper-evidence changes.</p><Button className="mt-3" variant="destructive" disabled={locked(actions['abandon-intake'])} onClick={() => { if (window.confirm('Abandon this intake permanently?')) void abandon() }}>Abandon intake</Button><ActionNotice action={actions['abandon-intake']} /></div> : null}
  </section>
}
