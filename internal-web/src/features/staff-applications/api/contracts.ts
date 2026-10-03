import { z } from 'zod'
import { meridianUuidSchema } from '@/lib/validation/meridian-uuid'

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
export const staffActorSchema = z.object({
  userId: meridianUuidSchema,
  displayName: z.string().trim().min(1),
  email: z.string().trim().email(),
})

export const staffLoanApplicationItemSchema = z.object({
  loanApplicationId: uuidSchema,
  applicationNumber: z.string().trim().min(1),
  productCode: rawEnumValueSchema,
  productType: rawEnumValueSchema,
  originationChannel: z.enum(['CUSTOMER_DIGITAL', 'STAFF_ASSISTED']).default('CUSTOMER_DIGITAL'),
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
  customerContext: z.object({
    customerNumber: z.string().trim().min(1),
    fullName: z.string().trim().min(1).nullable(),
    phoneNumber: z.string().trim().min(1).nullable(),
    maskedIdentityReference: z.string().regex(/^\*{4}[^\p{Cc}]{1,4}$/u).nullable(),
  }).nullable(),
  collateralContext: z.object({
    collateralType: rawEnumValueSchema,
    description: z.string().trim().min(1),
    estimatedValue: moneySchema,
    ownershipStatus: z.string().trim().min(1),
    conditionNote: z.string().trim().min(1),
  }).nullable(),
  customerReadiness: z.object({
    active: z.boolean(),
    profileComplete: z.boolean(),
    hasPrimaryActiveBankAccount: z.boolean(),
    verificationStatus: rawEnumValueSchema,
  }),
  formalReviewRecorded: z.boolean(),
  assignedLoanOfficer: staffActorSchema.nullable(),
  lifecycleHistory: z.array(z.object({
    fromStatus: rawEnumValueSchema.nullable(),
    toStatus: rawEnumValueSchema,
    action: rawEnumValueSchema,
    actorType: rawEnumValueSchema,
    actor: staffActorSchema.nullable(),
    occurredAt: apiTimestampSchema,
  })),
})

export type StaffLoanApplicationItem = z.infer<typeof staffLoanApplicationItemSchema>
export type StaffLoanApplicationPage = z.infer<typeof staffLoanApplicationPageSchema>
export type StaffLoanApplicationCase = z.infer<typeof staffLoanApplicationCaseSchema>

export const customerIdentityReferenceRevealSchema = z.object({
  loanApplicationId: uuidSchema,
  identityReference: z.string().min(1).max(100).refine(
    (value) => value.trim().length > 0 && !/\p{Cc}/u.test(value),
    'Invalid protected reveal response',
  ),
}).strict()
