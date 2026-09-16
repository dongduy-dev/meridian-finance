import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getAdminLoanProducts } from './admin-products-api'

export const adminProductKeys = {
  all: ['admin-products'] as const,
  list: () => [...adminProductKeys.all, 'list'] as const,
}

export const adminProductsQuery = (manager: AuthSessionManager, enabled: boolean) => queryOptions({
  queryKey: adminProductKeys.list(),
  queryFn: () => getAdminLoanProducts(manager),
  enabled,
})
