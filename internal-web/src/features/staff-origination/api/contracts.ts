import { z } from 'zod'

const timestampSchema = z.string().min(1)
const uuidSchema = z.string().uuid()

export const assistedOriginationSchema = z.object({
  assistedOriginationCaseId: uuidSchema,
  productCode: z.enum(['UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN']),
  customerId: uuidSchema.nullable(),
  status: z.enum(['OPEN', 'COMPLETED', 'ABANDONED']),
  loanApplicationId: uuidSchema.nullable().default(null),
  createdByStaffUserId: uuidSchema,
  createdAt: timestampSchema,
  updatedAt: timestampSchema,
  terminalAt: timestampSchema.nullable(),
})

export const originationChannelSchema = z.enum(['CUSTOMER_DIGITAL', 'STAFF_ASSISTED'])

const customerProfileSchema = z.object({
  fullName: z.string(), phoneNumber: z.string(), residentialAddress: z.string(),
  employmentStatus: z.string(), employerName: z.string().nullable(),
  termsConsentAccepted: z.boolean(), dataProcessingConsentAccepted: z.boolean(),
}).strict()

export const staffCustomerSchema = z.object({
  customerId: uuidSchema, customerNumber: z.string(), status: z.string(),
  verificationStatus: z.string(), profileCompletionStatus: z.string(),
  primaryActiveBankAccountPresent: z.boolean(), profile: customerProfileSchema.nullable(),
}).strict()

export const bankAccountSchema = z.object({
  customerBankAccountId: uuidSchema, bankCode: z.string(), bankNameSnapshot: z.string(),
  accountHolderName: z.string(), maskedAccountNumber: z.string(), accountNumberLastFour: z.string(),
  status: z.string(), primaryAccount: z.boolean(), createdAt: timestampSchema,
  updatedAt: timestampSchema, deactivatedAt: timestampSchema.nullable(),
})

export const intakeVersionSchema = z.object({
  intakeDocumentVersionId: uuidSchema, versionNumber: z.number().int().positive(),
  originalFilename: z.string(), detectedMimeType: z.string(), byteSize: z.number().positive(),
  uploadedAt: timestampSchema,
})

export const intakeEvidenceSchema = z.object({
  intakeDocumentId: uuidSchema, assistedOriginationCaseId: uuidSchema,
  evidenceType: z.enum(['CUSTOMER_IDENTITY', 'UCL_PAPER_APPLICATION', 'COLLATERAL_PAPER_APPLICATION']),
  currentVersionId: uuidSchema.nullable(), versions: z.array(intakeVersionSchema),
})

export type AssistedOrigination = z.infer<typeof assistedOriginationSchema>
export type StaffCustomer = z.infer<typeof staffCustomerSchema>
export type BankAccount = z.infer<typeof bankAccountSchema>
export type IntakeEvidence = z.infer<typeof intakeEvidenceSchema>
export type IntakeEvidenceVersion = z.infer<typeof intakeVersionSchema>

export type CustomerProfileInput = {
  fullName: string; identityReference?: string; phoneNumber: string; residentialAddress: string;
  employmentStatus: string; employerName?: string; termsConsentAccepted: boolean;
  dataProcessingConsentAccepted: boolean
}

export type CollateralLoanInput = {
  requestedAmount: number
  requestedTermMonths: number
  collateral: {
    type: 'MOTORBIKE' | 'CAR' | 'ELECTRONICS' | 'PROPERTY_DOCUMENT' | 'OTHER'
    description: string
    estimatedValue: number
    ownershipStatus: string
    conditionNote: string
  }
}
