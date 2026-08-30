import { createBrowserRouter, createMemoryRouter, Navigate, type RouteObject } from 'react-router-dom'

import { AuthLayout } from '@/components/layout/AuthLayout'
import { CustomerAppLayout } from '@/components/layout/CustomerAppLayout'
import { NotFoundPage } from '@/routes/NotFoundPage'
import { RouteErrorBoundary } from '@/routes/RouteErrorBoundary'
import { DetailPreviewPage } from '@/routes/foundation/DetailPreviewPage'
import { FocusedFlowPreviewPage } from '@/routes/foundation/FocusedFlowPreviewPage'
import { FoundationPlaceholderPage } from '@/routes/foundation/FoundationPlaceholderPage'
import { ForgotPasswordPage } from '@/routes/auth/ForgotPasswordPage'
import { LoginPage } from '@/routes/auth/LoginPage'
import { RegisterPage } from '@/routes/auth/RegisterPage'
import { ResetPasswordPage } from '@/routes/auth/ResetPasswordPage'
import { VerificationPendingPage } from '@/routes/auth/VerificationPendingPage'
import { VerifyEmailPage } from '@/routes/auth/VerifyEmailPage'
import { BankAccountsPage } from '@/routes/account/BankAccountsPage'
import { ProfilePage } from '@/routes/account/ProfilePage'
import { DashboardPage } from '@/routes/dashboard/DashboardPage'
import { ProductCataloguePage } from '@/routes/products/ProductCataloguePage'
import { ProductDetailPage } from '@/routes/products/ProductDetailPage'
import { SalaryAdvanceApplicationPage } from '@/routes/products/SalaryAdvanceApplicationPage'

import { ProtectedCustomerRoute } from './ProtectedCustomerRoute'
import { RouteFocusManager } from './RouteFocusManager'

export const routes: RouteObject[] = [
  {
    element: <RouteFocusManager />,
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
          { path: 'verify-email', element: <VerifyEmailPage /> },
          { path: 'verify-email/pending', element: <VerificationPendingPage /> },
          { path: 'forgot-password', element: <ForgotPasswordPage /> },
          { path: 'reset-password', element: <ResetPasswordPage /> },
        ],
      },
      {
        element: <ProtectedCustomerRoute />,
        children: [
          {
            element: <CustomerAppLayout />,
            children: [
              { index: true, element: <DashboardPage /> },
              {
                path: 'products',
                children: [
                  { index: true, element: <ProductCataloguePage /> },
                  { path: ':productSlug', element: <ProductDetailPage /> },
                ],
              },
              {
                path: 'applications',
                element: <FoundationPlaceholderPage title="Applications" />,
              },
              { path: 'loans', element: <FoundationPlaceholderPage title="Loans" /> },
              {
                path: 'account',
                children: [
                  { index: true, element: <Navigate replace to="profile" /> },
                  { path: 'profile', element: <ProfilePage /> },
                  { path: 'bank-accounts', element: <BankAccountsPage /> },
                ],
              },
            ],
          },
          {
            path: 'products/salary-advance/apply',
            element: <SalaryAdvanceApplicationPage />,
          },
        ],
      },
      { path: 'foundation/flow', element: <FocusedFlowPreviewPage /> },
      { path: 'foundation/detail', element: <DetailPreviewPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const appRouter = createBrowserRouter(routes)

export function createTestRouter(initialEntries: string[]) {
  return createMemoryRouter(routes, { initialEntries })
}
