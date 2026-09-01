import { createContext, useContext, useEffect, useMemo, useSyncExternalStore, type PropsWithChildren } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { AuthSessionManager } from './auth-session'

type AuthContextValue = {
  manager: AuthSessionManager
  state: ReturnType<AuthSessionManager['getSnapshot']>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient()
  const manager = useMemo(() => new AuthSessionManager(queryClient), [queryClient])
  const state = useSyncExternalStore(manager.subscribe, manager.getSnapshot, manager.getSnapshot)
  useEffect(() => { void manager.restore() }, [manager])
  return <AuthContext.Provider value={{ manager, state }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
