import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'

const rawValue = z.string().trim().min(1)
const nullableTimestamp = apiTimestampSchema.nullable()

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
  applicationStatus: rawValue,
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
      staffInstruction: z.string().nullable(),
      createdAt: apiTimestampSchema,
      completedAt: nullableTimestamp,
      proofState: rawValue,
    })),
  }).nullable(),
})

export type StaffCorrectionTask = z.infer<typeof staffCorrectionTaskSchema>
export type StaffCorrectionCase = z.infer<typeof staffCorrectionCaseSchema>
export type StaffCorrectionCaseTask = NonNullable<StaffCorrectionCase['correctionRequest']>['tasks'][number]
