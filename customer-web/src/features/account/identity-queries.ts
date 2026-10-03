import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/auth-context'
import { createIdentityApi } from './identity-api'

export function useOwnIdentityHistory() {
  const { manager, state } = useAuth()
  return useQuery({ queryKey: ['account', 'identity-verifications'], queryFn: () => createIdentityApi(manager).history(),
    enabled: state.status === 'authenticated' && state.actor.permissions.includes('customer:identity:read:own') })
}
