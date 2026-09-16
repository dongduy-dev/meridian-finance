import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { canAccessAdminRoute, type AdminRouteDefinition } from '@/app/router/admin-route-metadata'
import { preferredInternalDestination } from '@/app/router/internal-destination'
import { canAccessStaffRoute, type StaffRouteDefinition } from '@/app/router/staff-route-metadata'
import { SessionStatusScreen } from '@/components/layout/SessionStatusScreen'
import { useAuth } from '@/features/auth/model/auth-context'
import { NoAdministrativeAccessPage } from '@/features/admin/pages/NoAdministrativeAccessPage'
import { NoOperationalAccessPage } from '@/features/staff/pages/NoOperationalAccessPage'

export function LoginRoute() {
  const { state } = useAuth()
  if (state.status === 'checking') return <SessionStatusScreen />
  if (state.status === 'authenticated') return <Navigate to={preferredInternalDestination(state.actor)} replace />
  return <Outlet />
}

export function InternalHomeRoute() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  return <Navigate to={preferredInternalDestination(state.actor)} replace />
}

export function StaffCapabilityRoute({ route }: { route: StaffRouteDefinition }) {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  if (!canAccessStaffRoute(state.actor, route)) return <NoOperationalAccessPage />
  return <Outlet />
}

export function AdminCapabilityRoute({ route }: { route: AdminRouteDefinition }) {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  if (!canAccessAdminRoute(state.actor, route)) return <NoAdministrativeAccessPage />
  return <Outlet />
}

export function ProtectedInternalRoute() {
  const { state } = useAuth()
  const location = useLocation()
  if (state.status === 'checking') return <SessionStatusScreen />
  if (state.status === 'anonymous') return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  return <Outlet />
}
