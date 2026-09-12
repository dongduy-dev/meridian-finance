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
  STAFF_APPROVAL_QUEUE_ROUTE,
  STAFF_DECISION_CASE_ROUTE,
  STAFF_CONTRACT_QUEUE_ROUTE,
  STAFF_CONTRACT_CASE_ROUTE,
  STAFF_DISBURSEMENT_QUEUE_ROUTE,
  STAFF_DISBURSEMENT_CASE_ROUTE,
  STAFF_SERVICING_QUEUE_ROUTE,
  STAFF_LOAN_ACCOUNT_ROUTE,
  STAFF_REPAYMENT_ENTRY_ROUTE,
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
const StaffApprovalQueuePage = lazy(() => import('@/features/staff-approval/pages/StaffApprovalQueuePage').then((module) => ({ default: module.StaffApprovalQueuePage })))
const StaffDecisionWorkspacePage = lazy(() => import('@/features/staff-approval/pages/StaffDecisionWorkspacePage').then((module) => ({ default: module.StaffDecisionWorkspacePage })))
const StaffContractWorkQueuePage = lazy(() => import('@/features/staff-contracts/pages/StaffContractWorkQueuePage').then((module) => ({ default: module.StaffContractWorkQueuePage })))
const StaffContractWorkspacePage = lazy(() => import('@/features/staff-contracts/pages/StaffContractWorkspacePage').then((module) => ({ default: module.StaffContractWorkspacePage })))
const StaffDisbursementWorkQueuePage = lazy(() => import('@/features/staff-disbursements/pages/StaffDisbursementWorkQueuePage').then((module) => ({ default: module.StaffDisbursementWorkQueuePage })))
const StaffDisbursementWorkspacePage = lazy(() => import('@/features/staff-disbursements/pages/StaffDisbursementWorkspacePage').then((module) => ({ default: module.StaffDisbursementWorkspacePage })))
const StaffServicingWorkQueuePage = lazy(() => import('@/features/staff-servicing/pages/StaffServicingWorkQueuePage').then((module) => ({ default: module.StaffServicingWorkQueuePage })))
const StaffLoanAccountWorkspacePage = lazy(() => import('@/features/staff-servicing/pages/StaffLoanAccountWorkspacePage').then((module) => ({ default: module.StaffLoanAccountWorkspacePage })))
const StaffRepaymentEntryPage = lazy(() => import('@/features/staff-servicing/pages/StaffRepaymentEntryPage').then((module) => ({ default: module.StaffRepaymentEntryPage })))
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
    { element: <StaffCapabilityRoute route={STAFF_APPROVAL_QUEUE_ROUTE} />, children: [
      { path: STAFF_APPROVAL_QUEUE_ROUTE.path, element: <Deferred><StaffApprovalQueuePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_DECISION_CASE_ROUTE} />, children: [
      { path: STAFF_DECISION_CASE_ROUTE.path, element: <Deferred><StaffDecisionWorkspacePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_CONTRACT_QUEUE_ROUTE} />, children: [
      { path: STAFF_CONTRACT_QUEUE_ROUTE.path, element: <Deferred><StaffContractWorkQueuePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_CONTRACT_CASE_ROUTE} />, children: [
      { path: STAFF_CONTRACT_CASE_ROUTE.path, element: <Deferred><StaffContractWorkspacePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_DISBURSEMENT_QUEUE_ROUTE} />, children: [
      { path: STAFF_DISBURSEMENT_QUEUE_ROUTE.path, element: <Deferred><StaffDisbursementWorkQueuePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_DISBURSEMENT_CASE_ROUTE} />, children: [
      { path: STAFF_DISBURSEMENT_CASE_ROUTE.path, element: <Deferred><StaffDisbursementWorkspacePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_SERVICING_QUEUE_ROUTE} />, children: [
      { path: STAFF_SERVICING_QUEUE_ROUTE.path, element: <Deferred><StaffServicingWorkQueuePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_LOAN_ACCOUNT_ROUTE} />, children: [
      { path: STAFF_LOAN_ACCOUNT_ROUTE.path, element: <Deferred><StaffLoanAccountWorkspacePage /></Deferred> },
    ] },
    { element: <StaffCapabilityRoute route={STAFF_REPAYMENT_ENTRY_ROUTE} />, children: [
      { path: STAFF_REPAYMENT_ENTRY_ROUTE.path, element: <Deferred><StaffRepaymentEntryPage /></Deferred> },
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
