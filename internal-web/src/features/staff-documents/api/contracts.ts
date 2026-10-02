import { z } from 'zod'
import { apiTimestampSchema, staffActorSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)

export const documentReviewQueueItemSchema = z.object({
  checklistItemId: uuidSchema,
  loanApplicationId: uuidSchema,
  documentType: rawValue,
  currentVersionId: uuidSchema,
  uploadedAt: apiTimestampSchema,
  uploaderActorType: rawValue,
  reviewStatus: rawValue,
})

const documentVersionSchema = z.object({
  documentVersionId: uuidSchema,
  versionNumber: z.number().int().positive(),
  originalFilename: z.string().trim().min(1),
  detectedMimeType: z.string().trim().min(1),
  byteSize: z.number().int().positive(),
  uploadedAt: apiTimestampSchema,
})

const reviewHistorySchema = z.object({
  reviewDecisionId: uuidSchema,
  documentVersionId: uuidSchema,
  outcome: rawValue,
  waiverReasonCode: rawValue.nullable(),
  correctionReasonCode: rawValue.nullable(),
  customerInstruction: z.string().nullable(),
  reviewer: staffActorSchema.nullable(),
  decidedAt: apiTimestampSchema,
  restrictedStaffNoteReadable: z.boolean(),
  restrictedStaffNotes: z.string().nullable(),
}).refine((review) => review.restrictedStaffNoteReadable || review.restrictedStaffNotes === null, {
  message: 'Restricted document note is not authorized',
})

export const assistedActionEvidenceSchema = z.object({
  documentId: uuidSchema,
  evidenceType: rawValue,
  approvedOfferId: uuidSchema.nullable(),
  declaredOfferDecision: rawValue.nullable(),
  loanContractId: uuidSchema.nullable(),
  contractVersion: z.number().int().positive().nullable(),
  correctionRequestId: uuidSchema.nullable(),
  currentVersion: documentVersionSchema.nullable(),
  versionHistory: z.array(documentVersionSchema),
})

export type AssistedActionEvidence = z.infer<typeof assistedActionEvidenceSchema>
export type DocumentReviewHistory = z.infer<typeof reviewHistorySchema>

export const staffDocumentChecklistSchema = z.object({
  loanApplicationId: uuidSchema,
  originationChannel: z.enum(['CUSTOMER_DIGITAL', 'STAFF_ASSISTED']).default('CUSTOMER_DIGITAL'),
  applicationStatus: rawValue,
  checklistStage: rawValue,
  uploadComplete: z.boolean(),
  processingReady: z.boolean(),
  items: z.array(z.object({
    checklistItemId: uuidSchema,
    documentType: rawValue,
    requirementStatus: rawValue,
    evidenceStatus: rawValue,
    uploadComplete: z.boolean(),
    processingReady: z.boolean(),
    currentVersion: documentVersionSchema.nullable(),
    versionHistory: z.array(documentVersionSchema),
    reviewHistory: z.array(reviewHistorySchema),
  })),
})

export const documentReviewResultSchema = z.object({
  reviewDecisionId: uuidSchema,
  checklistItemId: uuidSchema,
  documentVersionId: uuidSchema,
  outcome: rawValue,
  waiverReasonCode: rawValue.nullable(),
  decidedAt: apiTimestampSchema,
})

export type DocumentReviewQueueItem = z.infer<typeof documentReviewQueueItemSchema>
export type StaffDocumentChecklist = z.infer<typeof staffDocumentChecklistSchema>
export type StaffDocumentItem = StaffDocumentChecklist['items'][number]
