import { useEffect, useSyncExternalStore, type ReactNode } from 'react'

import { AuthContext } from './auth-context'
import { authSessionManager, type AuthSessionManager } from './auth-session'

export interface AuthProviderProps {
  children: ReactNode
  manager?: AuthSessionManager
}

export function AuthProvider({ children, manager = authSessionManager }: AuthProviderProps) {
  const state = useSyncExternalStore(manager.subscribe, manager.getSnapshot, manager.getSnapshot)

  useEffect(() => {
    void manager.bootstrap()
  }, [manager])

  return <AuthContext.Provider value={{ manager, state }}>{children}</AuthContext.Provider>
}
