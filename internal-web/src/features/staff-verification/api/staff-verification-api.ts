import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  staffVerificationCaseSchema,
  type CompleteVerificationInput,
  type CorrectionTarget,
  type StaffVerificationCase,
} from './contracts'

export async function getStaffVerificationCase(
  manager: AuthSessionManager,
  loanApplicationId: string,
): Promise<StaffVerificationCase> {
  const payload = await manager.protectedRequest<unknown>(
    `/staff/loan-applications/${loanApplicationId}/verification`,
  )
  return staffVerificationCaseSchema.parse(payload)
}

export async function startVerification(
  manager: AuthSessionManager,
  data: StaffVerificationCase,
): Promise<void> {
  const segment = data.productCode === 'UNSECURED_CONSUMER_LOAN'
    ? 'unsecured-consumer-loan-verification'
    : 'collateral-loan-verification'
  await manager.protectedRequest<unknown>(
    `/loan-applications/${data.loanApplicationId}/${segment}/start`,
    { method: 'POST' },
  )
}

export async function completeVerification(
  manager: AuthSessionManager,
  data: StaffVerificationCase,
  input: CompleteVerificationInput,
): Promise<void> {
  const segment = data.productCode === 'UNSECURED_CONSUMER_LOAN'
    ? 'unsecured-consumer-loan-verification'
    : 'collateral-loan-verification'
  const body: Record<string, unknown> = {
    outcome: input.outcome,
    assessmentNote: input.assessmentNote,
  }
  if (data.productCode === 'COLLATERAL_LOAN') {
    body.expectedVerificationId = input.expectedVerificationId
  }
  if (input.outcome === 'REQUIRES_MORE_INFORMATION') {
    body.reasonCode = input.reasonCode
    body.correctionPlan = {
      tasks: (input.tasks ?? []).map((task) => correctionTask(
        data.correctionTargets,
        task.targetId,
        task.scope,
        task.instruction,
      )),
    }
  }
  await manager.protectedRequest<unknown>(
    `/loan-applications/${data.loanApplicationId}/${segment}/complete`,
    { method: 'POST', body },
  )
}

function correctionTask(
  targets: CorrectionTarget[],
  targetId: string,
  scope: 'DOCUMENT_REPLACEMENT' | 'DOCUMENT_REVIEW',
  instruction: string,
) {
  const target = targets.find((candidate) => candidate.checklistItemId === targetId)
  if (!target) throw new Error('The selected correction evidence is no longer available.')
  const customerOwned = scope === 'DOCUMENT_REPLACEMENT'
  return {
    scope,
    responsibleParty: customerOwned ? 'CUSTOMER' : 'STAFF',
    documentType: target.documentType,
    createChecklistItem: false,
    checklistItemId: target.checklistItemId,
    baselineDocumentVersionId: target.currentDocumentVersionId,
    customerInstruction: customerOwned ? instruction.trim() : null,
    staffInstruction: customerOwned ? null : instruction.trim(),
  }
}
