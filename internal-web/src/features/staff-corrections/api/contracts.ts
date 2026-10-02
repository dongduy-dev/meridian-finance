import { z } from 'zod'
import { recordedCustomerActionSchema } from '@/components/operations/recorded-customer-action'
import { apiTimestampSchema, staffActorSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)
const nullableTimestamp = apiTimestampSchema.nullable()

export const correctionActorSchema = z.object({
  actorType: rawValue,
  staffActor: staffActorSchema.nullable(),
}).refine((actor) => actor.actorType === 'STAFF' ? actor.staffActor !== null : actor.staffActor === null)

export const historicalCorrectionSchema = z.object({
  correctionRequestId: uuidSchema,
  status: rawValue,
  reasonCode: rawValue,
  sourceAction: rawValue,
  sourceReviewCycleId: uuidSchema.nullable(),
  createdBy: correctionActorSchema,
  createdAt: apiTimestampSchema,
  readyAt: nullableTimestamp,
  resubmittedAt: nullableTimestamp,
  cancelledAt: nullableTimestamp,
  resubmittedBy: correctionActorSchema.nullable(),
  resultingApplicationStatus: rawValue.nullable(),
  tasks: z.array(z.object({
    taskId: uuidSchema,
    sequence: z.number().int().positive(),
    responsibleParty: rawValue,
    scope: rawValue,
    documentType: rawValue.nullable(),
    checklistItemId: uuidSchema.nullable(),
    baselineDocumentVersionId: uuidSchema.nullable(),
    customerInstruction: z.string().nullable(),
    staffInstruction: z.string().nullable(),
    createdAt: apiTimestampSchema,
    status: rawValue,
    completedBy: correctionActorSchema.nullable(),
    completedAt: nullableTimestamp,
  })),
})

export type HistoricalCorrection = z.infer<typeof historicalCorrectionSchema>
export type CorrectionActor = z.infer<typeof correctionActorSchema>

export const assistedCancellationEvidenceSchema = z.object({
  documentId: uuidSchema,
  documentVersionId: uuidSchema,
  evidenceType: z.literal('CUSTOMER_CANCELLATION_REQUEST'),
  declaredOfferDecision: z.null(),
  targetId: uuidSchema,
  targetVersion: z.null(),
  versionNumber: z.number().int().positive(),
  detectedMimeType: z.enum(['application/pdf', 'image/jpeg', 'image/png']),
  byteSize: z.number().int().positive(),
  uploadedAt: apiTimestampSchema,
})

export const uploadedCancellationEvidenceVersionSchema = z.object({
  documentVersionId: uuidSchema,
  versionNumber: z.number().int().positive(),
  detectedMimeType: z.enum(['application/pdf', 'image/jpeg', 'image/png']),
  byteSize: z.number().int().positive(),
  uploadedAt: apiTimestampSchema,
})

export const cancelledLoanApplicationSchema = z.object({
  loanApplicationId: uuidSchema,
  resultingStatus: z.literal('CANCELLED'),
  cancelledAt: apiTimestampSchema,
  idempotentReplay: z.boolean(),
})

export const staffCorrectionTaskSchema = z.object({
  taskId: uuidSchema,
  correctionRequestId: uuidSchema,
  loanApplicationId: uuidSchema,
  status: rawValue,
  scope: rawValue,
  documentType: rawValue.nullable(),
  checklistItemId: uuidSchema.nullable(),
  baselineDocumentVersionId: uuidSchema.nullable(),
  reasonCode: rawValue,
  staffInstruction: z.string().nullable(),
  createdAt: apiTimestampSchema,
  completedAt: nullableTimestamp,
})

export const staffCorrectionCaseSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productCode: rawValue,
  originationChannel: rawValue,
  applicationStatus: rawValue,
  correctionHistory: z.array(historicalCorrectionSchema),
  correctionRequest: z.object({
    correctionRequestId: uuidSchema,
    status: rawValue,
    reasonCode: rawValue,
    createdAt: apiTimestampSchema,
    makerCheckerBlockedForCurrentActor: z.boolean(),
    allTasksComplete: z.boolean(),
    staffResubmissionReady: z.boolean(),
    tasks: z.array(z.object({
      taskId: uuidSchema,
      responsibleParty: rawValue,
      status: rawValue,
      scope: rawValue,
      documentType: rawValue.nullable(),
      checklistItemId: uuidSchema.nullable(),
      baselineDocumentVersionId: uuidSchema.nullable(),
      reasonCode: rawValue,
      customerInstruction: z.string().nullable(),
      staffInstruction: z.string().nullable(),
      createdAt: apiTimestampSchema,
      completedAt: nullableTimestamp,
      proofState: rawValue,
      customerSourceViaStaff: z.boolean(),
      uploadActionAvailable: z.boolean(),
      completionActionAvailable: z.boolean(),
    })),
  }).nullable(),
  assistedCancellation: z.object({
    available: z.boolean(),
    correctionRequestId: uuidSchema.nullable(),
    evidence: assistedCancellationEvidenceSchema.nullable(),
    evidenceUploadAvailable: z.boolean(),
    cancellationCommandAvailable: z.boolean(),
    completedCancellation: recordedCustomerActionSchema(assistedCancellationEvidenceSchema).nullable(),
  }),
})

export type StaffCorrectionTask = z.infer<typeof staffCorrectionTaskSchema>
export type StaffCorrectionCase = z.infer<typeof staffCorrectionCaseSchema>
export type StaffCorrectionCaseTask = NonNullable<StaffCorrectionCase['correctionRequest']>['tasks'][number]
