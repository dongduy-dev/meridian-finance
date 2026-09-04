import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { STAFF_HOME_ROUTE, canAccessStaffRoute, type StaffRouteDefinition } from '@/app/router/staff-route-metadata'
import { SessionStatusScreen } from '@/components/layout/SessionStatusScreen'
import { useAuth } from '@/features/auth/model/auth-context'
import { NoOperationalAccessPage } from '@/features/staff/pages/NoOperationalAccessPage'

export function LoginRoute() {
  const { state } = useAuth()
  if (state.status === 'checking') return <SessionStatusScreen />
  if (state.status === 'authenticated') return <Navigate to={STAFF_HOME_ROUTE.path} replace />
  return <Outlet />
}

export function StaffCapabilityRoute({ route }: { route: StaffRouteDefinition }) {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  if (!canAccessStaffRoute(state.actor, route)) return <NoOperationalAccessPage />
  return <Outlet />
}

export function ProtectedStaffRoute() {
  const { state } = useAuth()
  const location = useLocation()
  if (state.status === 'checking') return <SessionStatusScreen />
  if (state.status === 'anonymous') return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  return <Outlet />
}
