import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import {
  adminLoanProductSchema,
  type AdminLoanProduct,
  type UpdateProductLimitsInput,
} from './contracts'

export async function getAdminLoanProducts(manager: AuthSessionManager): Promise<AdminLoanProduct[]> {
  return adminLoanProductSchema.array().parse(
    await manager.protectedRequest<unknown>('/admin/loan-products'),
  )
}

export async function updateProductLimits(
  manager: AuthSessionManager,
  productCode: string,
  input: UpdateProductLimitsInput,
): Promise<AdminLoanProduct> {
  return adminLoanProductSchema.parse(await manager.protectedRequest<unknown>(
    `/admin/loan-products/${encodeURIComponent(productCode)}/limits`,
    { method: 'PUT', body: input },
  ))
}

export async function changeProductActivation(
  manager: AuthSessionManager,
  productCode: string,
  active: boolean,
): Promise<AdminLoanProduct> {
  return adminLoanProductSchema.parse(await manager.protectedRequest<unknown>(
    `/admin/loan-products/${encodeURIComponent(productCode)}/activation`,
    { method: 'PUT', body: { active } },
  ))
}
