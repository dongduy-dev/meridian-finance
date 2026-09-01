import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { SessionStatusScreen } from '@/components/layout/SessionStatusScreen'
import { useAuth } from '@/features/auth/model/auth-context'

export function LoginRoute() {
  const { state } = useAuth()
  if (state.status === 'checking') return <SessionStatusScreen />
  if (state.status === 'authenticated') return <Navigate to="/staff" replace />
  return <Outlet />
}

export function ProtectedStaffRoute() {
  const { state } = useAuth()
  const location = useLocation()
  if (state.status === 'checking') return <SessionStatusScreen />
  if (state.status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}
