import { z } from 'zod'

const apiTimestampPattern = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:?\d{2})?$/i

function isValidApiTimestamp(value: string): boolean {
  const parts = apiTimestampPattern.exec(value)
  if (!parts) return false
  const timestamp = new Date(0)
  timestamp.setUTCFullYear(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]))
  timestamp.setUTCHours(Number(parts[4]), Number(parts[5]), Number(parts[6] ?? 0), 0)
  return timestamp.getUTCFullYear() === Number(parts[1])
    && timestamp.getUTCMonth() === Number(parts[2]) - 1
    && timestamp.getUTCDate() === Number(parts[3])
    && timestamp.getUTCHours() === Number(parts[4])
    && timestamp.getUTCMinutes() === Number(parts[5])
    && timestamp.getUTCSeconds() === Number(parts[6] ?? 0)
}

export const uuidSchema = z.string().uuid()
export const apiTimestampSchema = z.string().refine(isValidApiTimestamp, 'Invalid API timestamp')
const rawEnumValueSchema = z.string().trim().min(1)
const moneySchema = z.number().finite().int().positive()

export const staffLoanApplicationItemSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productCode: rawEnumValueSchema,
  productType: rawEnumValueSchema,
  requestedAmount: moneySchema,
  requestedTermMonths: z.number().int().positive(),
  status: rawEnumValueSchema,
  submittedAt: apiTimestampSchema,
})

export const staffLoanApplicationPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(staffLoanApplicationItemSchema),
})

export const staffLoanApplicationCaseSchema = staffLoanApplicationItemSchema.extend({
  customerReadiness: z.object({
    active: z.boolean(),
    profileComplete: z.boolean(),
    hasPrimaryActiveBankAccount: z.boolean(),
    verificationStatus: rawEnumValueSchema,
  }),
  lifecycleHistory: z.array(z.object({
    fromStatus: rawEnumValueSchema.nullable(),
    toStatus: rawEnumValueSchema,
    action: rawEnumValueSchema,
    actorType: rawEnumValueSchema,
    occurredAt: apiTimestampSchema,
  })),
})

export type StaffLoanApplicationItem = z.infer<typeof staffLoanApplicationItemSchema>
export type StaffLoanApplicationPage = z.infer<typeof staffLoanApplicationPageSchema>
export type StaffLoanApplicationCase = z.infer<typeof staffLoanApplicationCaseSchema>
