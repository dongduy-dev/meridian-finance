import { useQuery } from '@tanstack/react-query'

import { createLoanProductApi } from './loan-product-api'

export const loanProductKeys = {
  all: ['loan-products'] as const,
  list: () => [...loanProductKeys.all, 'list'] as const,
  detail: (productCode: string) => [...loanProductKeys.all, 'detail', productCode] as const,
}

const loanProductApi = createLoanProductApi()

export function useLoanProductsQuery() {
  return useQuery({
    queryKey: loanProductKeys.list(),
    queryFn: () => loanProductApi.getProducts(),
  })
}

export function useLoanProductQuery(productCode: string) {
  return useQuery({
    queryKey: loanProductKeys.detail(productCode),
    queryFn: () => loanProductApi.getProduct(productCode),
  })
}
