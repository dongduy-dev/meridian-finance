import { z } from 'zod'

const rawValue = z.string().trim().min(1)
const money = z.number().finite().nonnegative()
const uuid = z.string().regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i)

export const partnerCompanySchema = z.object({
  id: uuid,
  companyCode: z.string().trim().min(1),
  name: z.string().trim().min(1),
  status: rawValue,
  salaryAdvancePolicyLimit: money,
})

export const partnerEmployeeSchema = z.object({
  id: uuid,
  partnerCompanyId: uuid,
  importBatchId: uuid,
  employeeCode: z.string(),
  identityReference: z.string(),
  salaryAmount: money,
  salaryAdvanceLimit: money,
  employmentStatus: rawValue,
  active: z.boolean(),
})

export const partnerImportBatchSchema = z.object({
  id: uuid,
  partnerCompanyId: uuid,
  effectiveMonth: z.string(),
  status: rawValue,
  validRowCount: z.number().int().nonnegative(),
  invalidRowCount: z.number().int().nonnegative(),
})

export const partnerImportResultSchema = z.object({
  importBatchId: uuid,
  partnerCompanyId: uuid,
  effectiveMonth: z.string(),
  status: rawValue,
  validRowCount: z.number().int().nonnegative(),
  invalidRowCount: z.number().int().nonnegative(),
  rejections: z.array(z.object({
    rowIndex: z.number().int().positive(),
    errorCode: rawValue,
    reason: z.string().trim().min(1),
  })),
})

export const createPartnerCompanyInputSchema = z.object({
  companyCode: z.string().trim().min(1).max(50),
  name: z.string().trim().min(1).max(200),
  status: z.enum(['ACTIVE', 'INACTIVE', 'SUSPENDED']),
  salaryAdvancePolicyLimit: z.number().finite().nonnegative(),
})

export const updatePartnerCompanyInputSchema = createPartnerCompanyInputSchema.pick({
  name: true,
  salaryAdvancePolicyLimit: true,
})

export const importRowInputSchema = z.object({
  employeeCode: z.string().trim().min(1).max(50),
  identityReference: z.string().trim().min(1).max(100),
  salaryAmount: z.number().finite().nonnegative(),
  salaryAdvanceLimit: z.number().finite().nonnegative(),
  employmentStatus: z.enum(['ACTIVE', 'INACTIVE', 'TERMINATED', 'SUSPENDED']),
  active: z.boolean(),
})

export const importPartnerEmployeesInputSchema = z.object({
  effectiveMonth: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/),
  rows: z.array(importRowInputSchema).min(1),
})

const reviewCandidateSchema = z.object({
  partnerEmployeeId: uuid,
  importBatchId: uuid,
  employeeCode: z.string().trim().min(1),
  employmentStatus: rawValue,
  active: z.boolean(),
})

const reviewCompanySchema = z.object({
  id: uuid,
  companyCode: z.string().trim().min(1),
  name: z.string().trim().min(1),
  status: rawValue,
})

export const partnerEligibilityReviewSchema = z.object({
  reviewId: uuid,
  customerId: uuid,
  partnerCompany: reviewCompanySchema,
  effectiveMonth: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/),
  sourceImportBatchId: uuid.nullable(),
  triggerOutcome: rawValue,
  requestedEmployeeCode: z.string().trim().min(1),
  status: rawValue,
  decisionOutcome: rawValue.nullable(),
  decisionReason: rawValue.nullable(),
  selectedEmployee: reviewCandidateSchema.nullable(),
  reviewerUserId: uuid.nullable(),
  reviewedAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
  candidates: z.array(reviewCandidateSchema),
  approvalAvailable: z.boolean(),
  rejectionAvailable: z.boolean(),
  nonReviewableReason: rawValue.nullable(),
})

export const partnerEligibilityReviewPageSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  items: z.array(z.object({
    reviewId: uuid,
    customerId: uuid,
    partnerCompanyId: uuid,
    partnerCompanyCode: z.string().trim().min(1),
    partnerCompanyName: z.string().trim().min(1),
    effectiveMonth: z.string(),
    triggerOutcome: rawValue,
    requestedEmployeeCode: z.string().trim().min(1),
    status: rawValue,
    createdAt: z.string(),
    reviewable: z.boolean(),
    nonReviewableReason: rawValue.nullable(),
  })),
})

export const partnerEligibilityReviewDecisionSchema = z.discriminatedUnion('outcome', [
  z.object({
    outcome: z.literal('APPROVE'),
    partnerEmployeeId: uuid,
    reasonCode: z.literal('CURRENT_EMPLOYEE_CONFIRMED'),
  }),
  z.object({
    outcome: z.literal('REJECT'),
    partnerEmployeeId: z.null(),
    reasonCode: z.enum([
      'NO_ELIGIBLE_CURRENT_EMPLOYEE',
      'IDENTITY_EVIDENCE_MISMATCH',
      'INSUFFICIENT_SOURCE_EVIDENCE',
    ]),
  }),
])

export type PartnerCompany = z.infer<typeof partnerCompanySchema>
export type PartnerEmployee = z.infer<typeof partnerEmployeeSchema>
export type PartnerImportBatch = z.infer<typeof partnerImportBatchSchema>
export type PartnerImportResult = z.infer<typeof partnerImportResultSchema>
export type CreatePartnerCompanyInput = z.infer<typeof createPartnerCompanyInputSchema>
export type UpdatePartnerCompanyInput = z.infer<typeof updatePartnerCompanyInputSchema>
export type ImportPartnerEmployeesInput = z.infer<typeof importPartnerEmployeesInputSchema>
export type PartnerEligibilityReview = z.infer<typeof partnerEligibilityReviewSchema>
export type PartnerEligibilityReviewPage = z.infer<typeof partnerEligibilityReviewPageSchema>
export type PartnerEligibilityReviewDecision = z.infer<typeof partnerEligibilityReviewDecisionSchema>
