import { z } from 'zod'
import { apiTimestampSchema, staffActorSchema } from '@/features/staff-applications/api/contracts'

export function recordedCustomerActionSchema<T extends z.ZodType>(evidence: T) {
  return z.object({
    action: z.string().trim().min(1),
    recordedBy: staffActorSchema,
    recordedAt: apiTimestampSchema,
    evidence,
  })
}
