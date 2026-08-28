import { createContext, useContext } from 'react'

import type { AuthSessionManager } from './auth-session'

export interface AuthContextValue {
  manager: AuthSessionManager
  state: ReturnType<AuthSessionManager['getSnapshot']>
}
export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider.')
  }
  return context
}
