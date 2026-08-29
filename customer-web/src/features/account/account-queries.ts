import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useRef } from 'react'

import { useAuth } from '@/features/auth/auth-context'
import { ApiError } from '@/lib/api'

import {
  createAccountApi,
  type AddCustomerBankAccountInput,
  type UpdateCustomerProfileInput,
} from './account-api'

export const accountKeys = {
  all: ['account'] as const,
  customer: () => [...accountKeys.all, 'customer'] as const,
  bankAccounts: () => [...accountKeys.all, 'bank-accounts'] as const,
}

function useAccountApi() {
  const { manager } = useAuth()
  return useMemo(() => createAccountApi(manager), [manager])
}

export function useOwnCustomerQuery() {
  const api = useAccountApi()
  return useQuery({
    queryKey: accountKeys.customer(),
    queryFn: () => api.getOwnCustomer(),
  })
}

export function useOwnBankAccountsQuery() {
  const api = useAccountApi()
  return useQuery({
    queryKey: accountKeys.bankAccounts(),
    queryFn: () => api.getOwnBankAccounts(),
  })
}

export function useUpdateProfileMutation() {
  const api = useAccountApi()
  const queryClient = useQueryClient()
  const pendingInput = useRef<UpdateCustomerProfileInput | undefined>(undefined)
  const mutation = useMutation({
    mutationFn: async () => {
      const input = pendingInput.current
      if (!input) {
        throw new Error('Profile submission was not prepared.')
      }
      try {
        return await api.updateOwnProfile(input)
      } finally {
        pendingInput.current = undefined
      }
    },
    onSuccess: (customer) => {
      queryClient.setQueryData(accountKeys.customer(), customer)
    },
  })

  return {
    ...mutation,
    submit(input: UpdateCustomerProfileInput) {
      pendingInput.current = input
      return mutation.mutateAsync()
    },
  }
}

function useRefreshBankAccountState() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: accountKeys.bankAccounts() }),
      queryClient.invalidateQueries({ queryKey: accountKeys.customer() }),
    ])
}

function isConflict(error: unknown) {
  return error instanceof ApiError && error.status === 409
}

export function useAddBankAccountMutation() {
  const api = useAccountApi()
  const refresh = useRefreshBankAccountState()
  const pendingInput = useRef<AddCustomerBankAccountInput | undefined>(undefined)
  const mutation = useMutation({
    mutationFn: async () => {
      const input = pendingInput.current
      if (!input) {
        throw new Error('Bank-account submission was not prepared.')
      }
      try {
        return await api.addBankAccount(input)
      } finally {
        pendingInput.current = undefined
      }
    },
    onSuccess: refresh,
    onError: (error) => (isConflict(error) ? refresh() : undefined),
  })

  return {
    ...mutation,
    submit(input: AddCustomerBankAccountInput) {
      pendingInput.current = input
      return mutation.mutateAsync()
    },
  }
}

export function useMakePrimaryMutation() {
  const api = useAccountApi()
  const refresh = useRefreshBankAccountState()
  return useMutation({
    mutationFn: (customerBankAccountId: string) => api.makePrimary(customerBankAccountId),
    onSuccess: refresh,
    onError: (error) => (isConflict(error) ? refresh() : undefined),
  })
}

export function useDeactivateBankAccountMutation() {
  const api = useAccountApi()
  const refresh = useRefreshBankAccountState()
  return useMutation({
    mutationFn: (customerBankAccountId: string) => api.deactivate(customerBankAccountId),
    onSuccess: refresh,
    onError: (error) => (isConflict(error) ? refresh() : undefined),
  })
}
