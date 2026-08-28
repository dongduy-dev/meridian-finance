import { z } from 'zod'

export const meridianErrorEnvelopeSchema = z.object({
  timestamp: z.string(),
  status: z.number().int(),
  errorCode: z.string().min(1),
  message: z.string().min(1),
  path: z.string(),
})

export type MeridianErrorEnvelope = z.infer<typeof meridianErrorEnvelopeSchema>

export function parseMeridianErrorEnvelope(value: unknown) {
  const parsed = meridianErrorEnvelopeSchema.safeParse(value)
  return parsed.success ? parsed.data : undefined
}
