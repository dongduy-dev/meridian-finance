import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter, createMemoryRouter, Navigate, Outlet, RouterProvider, type RouteObject } from 'react-router-dom'

import { OperationsShell } from '@/components/layout/OperationsShell'
import { LoginRoute, ProtectedStaffRoute, StaffCapabilityRoute } from '@/routes/guards'
import { RouteErrorPage } from '@/routes/RouteErrorPage'
import { RouteFocus } from '@/routes/RouteFocus'
import { STAFF_HOME_ROUTE } from './staff-route-metadata'

const LoginPage = lazy(() => import('@/features/auth/components/LoginPage').then((module) => ({ default: module.LoginPage })))
const StaffLandingPage = lazy(() => import('@/features/staff/pages/StaffLandingPage').then((module) => ({ default: module.StaffLandingPage })))
const NotFoundPage = lazy(() => import('@/routes/NotFoundPage').then((module) => ({ default: module.NotFoundPage })))

function RouteFrame() { return <><RouteFocus /><Outlet /></> }
function Deferred({ children }: { children: ReactNode }) {
  return <Suspense fallback={<div className="p-6 text-sm text-muted-foreground">Loading workspace…</div>}>{children}</Suspense>
}

export const routes: RouteObject[] = [{ element: <RouteFrame />, errorElement: <RouteErrorPage />, children: [
  { path: '/', element: <Navigate to={STAFF_HOME_ROUTE.path} replace /> },
  { element: <LoginRoute />, children: [{ path: '/login', element: <Deferred><LoginPage /></Deferred> }] },
  { element: <ProtectedStaffRoute />, children: [{ element: <OperationsShell />, children: [
    { element: <StaffCapabilityRoute route={STAFF_HOME_ROUTE} />, children: [
      { path: STAFF_HOME_ROUTE.path, element: <Deferred><StaffLandingPage /></Deferred> },
    ] },
  ] }] },
  { path: '/admin/*', element: <Deferred><NotFoundPage /></Deferred> },
  { path: '*', element: <Deferred><NotFoundPage /></Deferred> },
] }]

const router = createBrowserRouter(routes)

export function createTestRouter(initialEntries: string[]) {
  return createMemoryRouter(routes, { initialEntries })
}

export function AppRouter() {
  return <RouterProvider router={router} />
}
