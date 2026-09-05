import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter, createMemoryRouter, Navigate, Outlet, RouterProvider, type RouteObject } from 'react-router-dom'

import { OperationsShell } from '@/components/layout/OperationsShell'
import { LoginRoute, ProtectedStaffRoute, StaffCapabilityRoute } from '@/routes/guards'
import { RouteErrorPage } from '@/routes/RouteErrorPage'
import { RouteFocus } from '@/routes/RouteFocus'
import {
  STAFF_APPLICATION_CASE_ROUTE,
  STAFF_APPLICATIONS_ROUTE,
  STAFF_HOME_ROUTE,
  STAFF_DOCUMENT_QUEUE_ROUTE,
  STAFF_CORRECTION_QUEUE_ROUTE,
  STAFF_DOCUMENT_CASE_ROUTE,
  STAFF_CORRECTION_CASE_ROUTE,
  STAFF_VERIFICATION_CASE_ROUTE,
  STAFF_REVIEW_CASE_ROUTE,
} from './staff-route-metadata'

const LoginPage = lazy(() => import('@/features/auth/components/LoginPage').then((module) => ({ default: module.LoginPage })))
const StaffLandingPage = lazy(() => import('@/features/staff/pages/StaffLandingPage').then((module) => ({ default: module.StaffLandingPage })))
const ApplicationSearchPage = lazy(() => import('@/features/staff-applications/pages/ApplicationSearchPage').then((module) => ({ default: module.ApplicationSearchPage })))
const ApplicationCasePage = lazy(() => import('@/features/staff-applications/pages/ApplicationCasePage').then((module) => ({ default: module.ApplicationCasePage })))
const DocumentReviewQueuePage = lazy(() => import('@/features/staff-documents/pages/DocumentReviewQueuePage').then((module) => ({ default: module.DocumentReviewQueuePage })))
const StaffDocumentWorkspacePage = lazy(() => import('@/features/staff-documents/pages/StaffDocumentWorkspacePage').then((module) => ({ default: module.StaffDocumentWorkspacePage })))
const StaffCorrectionQueuePage = lazy(() => import('@/features/staff-corrections/pages/StaffCorrectionQueuePage').then((module) => ({ default: module.StaffCorrectionQueuePage })))
const StaffCorrectionWorkspacePage = lazy(() => import('@/features/staff-corrections/pages/StaffCorrectionWorkspacePage').then((module) => ({ default: module.StaffCorrectionWorkspacePage })))
const StaffVerificationWorkspacePage = lazy(() => import('@/features/staff-verification/pages/StaffVerificationWorkspacePage').then((module) => ({ default: module.StaffVerificationWorkspacePage })))
const StaffReviewWorkspacePage = lazy(() => import('@/features/staff-review/pages/StaffReviewWorkspacePage').then((module) => ({ default: module.StaffReviewWorkspacePage })))
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
    { element: <StaffCapabilityRoute route={STAFF_APPLICATIONS_ROUTE} />, children: [
      { path: STAFF_APPLICATIONS_ROUTE.path, element: <Deferred><ApplicationSearchPage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_APPLICATION_CASE_ROUTE} />, children: [
      { path: STAFF_APPLICATION_CASE_ROUTE.path, element: <Deferred><ApplicationCasePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_DOCUMENT_QUEUE_ROUTE} />, children: [
      { path: STAFF_DOCUMENT_QUEUE_ROUTE.path, element: <Deferred><DocumentReviewQueuePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_CORRECTION_QUEUE_ROUTE} />, children: [
      { path: STAFF_CORRECTION_QUEUE_ROUTE.path, element: <Deferred><StaffCorrectionQueuePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_DOCUMENT_CASE_ROUTE} />, children: [
      { path: STAFF_DOCUMENT_CASE_ROUTE.path, element: <Deferred><StaffDocumentWorkspacePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_CORRECTION_CASE_ROUTE} />, children: [
      { path: STAFF_CORRECTION_CASE_ROUTE.path, element: <Deferred><StaffCorrectionWorkspacePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_VERIFICATION_CASE_ROUTE} />, children: [
      { path: STAFF_VERIFICATION_CASE_ROUTE.path, element: <Deferred><StaffVerificationWorkspacePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_REVIEW_CASE_ROUTE} />, children: [
      { path: STAFF_REVIEW_CASE_ROUTE.path, element: <Deferred><StaffReviewWorkspacePage /></Deferred> },
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
