import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router-dom'

import { OperationsShell } from '@/components/layout/OperationsShell'
import { LoginRoute, ProtectedStaffRoute } from '@/routes/guards'
import { RouteErrorPage } from '@/routes/RouteErrorPage'
import { RouteFocus } from '@/routes/RouteFocus'

const LoginPage = lazy(() => import('@/features/auth/components/LoginPage').then((module) => ({ default: module.LoginPage })))
const StaffLandingPage = lazy(() => import('@/features/staff/pages/StaffLandingPage').then((module) => ({ default: module.StaffLandingPage })))
const NotFoundPage = lazy(() => import('@/routes/NotFoundPage').then((module) => ({ default: module.NotFoundPage })))

function RouteFrame() { return <><RouteFocus /><Outlet /></> }
function Deferred({ children }: { children: ReactNode }) {
  return <Suspense fallback={<div className="p-6 text-sm text-muted-foreground">Loading workspace…</div>}>{children}</Suspense>
}

const router = createBrowserRouter([{ element: <RouteFrame />, errorElement: <RouteErrorPage />, children: [
  { path: '/', element: <Navigate to="/staff" replace /> },
  { element: <LoginRoute />, children: [{ path: '/login', element: <Deferred><LoginPage /></Deferred> }] },
  { element: <ProtectedStaffRoute />, children: [{ element: <OperationsShell />, children: [{ path: '/staff', element: <Deferred><StaffLandingPage /></Deferred> }] }] },
  { path: '/admin/*', element: <Deferred><NotFoundPage /></Deferred> },
  { path: '*', element: <Deferred><NotFoundPage /></Deferred> },
] }])

export function AppRouter() {
  return <RouterProvider router={router} />
}
