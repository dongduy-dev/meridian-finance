import { z } from 'zod'
import { apiTimestampSchema, uuidSchema } from '@/features/staff-applications/api/contracts'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

const staffActorSchema = z.object({
  userId: meridianUuidSchema,
  displayName: z.string().trim().min(1),
  email: z.string().email(),
})

const actorEventSchema = z.object({
  actor: staffActorSchema,
  at: apiTimestampSchema,
})

export const accountingCaseContextSchema = z.object({
  customer: z.object({
    customerNumber: z.string().trim().min(1),
    fullName: z.string().trim().min(1),
    phoneNumber: z.string().trim().min(1),
  }),
  handoff: z.object({
    approved: actorEventSchema,
    contractPrepared: actorEventSchema.nullable(),
    customerAcknowledgment: z.object({
      mode: z.string().trim().min(1),
      recordedBy: staffActorSchema.nullable(),
      at: apiTimestampSchema,
      evidenceDocumentVersionId: uuidSchema.nullable(),
    }).nullable(),
    readinessConfirmed: actorEventSchema.nullable(),
    disbursementConfirmed: actorEventSchema.nullable(),
  }),
})

export type AccountingCaseContext = z.infer<typeof accountingCaseContextSchema>
