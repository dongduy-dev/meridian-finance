import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  partnerCompanySchema,
  partnerEmployeeSchema,
  partnerImportBatchSchema,
  partnerImportResultSchema,
  type CreatePartnerCompanyInput,
  type ImportPartnerEmployeesInput,
  type PartnerCompany,
  type PartnerEmployee,
  type PartnerImportBatch,
  type PartnerImportResult,
  type UpdatePartnerCompanyInput,
} from './contracts'

export async function getPartnerCompanies(manager: AuthSessionManager): Promise<PartnerCompany[]> {
  return partnerCompanySchema.array().parse(await manager.protectedRequest<unknown>('/partner-companies'))
}

export async function getPartnerCompany(manager: AuthSessionManager, id: string): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}`))
}

export async function getPartnerEmployees(manager: AuthSessionManager, id: string): Promise<PartnerEmployee[]> {
  return partnerEmployeeSchema.array().parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/employees?activeOnly=false`))
}

export async function getPartnerImportBatches(manager: AuthSessionManager, id: string): Promise<PartnerImportBatch[]> {
  return partnerImportBatchSchema.array().parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/employee-import-batches`))
}

export async function createPartnerCompany(manager: AuthSessionManager, input: CreatePartnerCompanyInput): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>('/partner-companies', { method: 'POST', body: input }))
}

export async function updatePartnerCompany(manager: AuthSessionManager, id: string, input: UpdatePartnerCompanyInput): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}`, { method: 'PUT', body: input }))
}

export async function changePartnerCompanyStatus(manager: AuthSessionManager, id: string, status: string): Promise<PartnerCompany> {
  return partnerCompanySchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/status`, { method: 'POST', body: { status } }))
}

export async function importPartnerEmployees(
  manager: AuthSessionManager,
  id: string,
  requestId: string,
  input: ImportPartnerEmployeesInput,
): Promise<PartnerImportResult> {
  return partnerImportResultSchema.parse(await manager.protectedRequest<unknown>(`/partner-companies/${id}/employee-import-batches`, {
    method: 'POST',
    body: { requestId, ...input },
  }))
}
