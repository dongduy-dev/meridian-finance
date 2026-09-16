import { z } from 'zod'

const rawValue = z.string().trim().min(1)
const money = z.number().finite().nonnegative()
const uuid = z.string().uuid()

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

export type PartnerCompany = z.infer<typeof partnerCompanySchema>
export type PartnerEmployee = z.infer<typeof partnerEmployeeSchema>
export type PartnerImportBatch = z.infer<typeof partnerImportBatchSchema>
export type PartnerImportResult = z.infer<typeof partnerImportResultSchema>
export type CreatePartnerCompanyInput = z.infer<typeof createPartnerCompanyInputSchema>
export type UpdatePartnerCompanyInput = z.infer<typeof updatePartnerCompanyInputSchema>
export type ImportPartnerEmployeesInput = z.infer<typeof importPartnerEmployeesInputSchema>
