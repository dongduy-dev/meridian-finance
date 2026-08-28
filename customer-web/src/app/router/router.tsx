import { createBrowserRouter, createMemoryRouter, type RouteObject } from 'react-router-dom'

import { AuthLayout } from '@/components/layout/AuthLayout'
import { CustomerAppLayout } from '@/components/layout/CustomerAppLayout'
import { NotFoundPage } from '@/routes/NotFoundPage'
import { RouteErrorBoundary } from '@/routes/RouteErrorBoundary'
import { AuthFoundationPage } from '@/routes/foundation/AuthFoundationPage'
import { DetailPreviewPage } from '@/routes/foundation/DetailPreviewPage'
import { FocusedFlowPreviewPage } from '@/routes/foundation/FocusedFlowPreviewPage'
import { FoundationOverviewPage } from '@/routes/foundation/FoundationOverviewPage'
import { FoundationPlaceholderPage } from '@/routes/foundation/FoundationPlaceholderPage'

import { RouteFocusManager } from './RouteFocusManager'

export const routes: RouteObject[] = [
  {
    element: <RouteFocusManager />,
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        element: <CustomerAppLayout />,
        children: [
          { index: true, element: <FoundationOverviewPage /> },
          { path: 'products', element: <FoundationPlaceholderPage title="Products" /> },
          {
            path: 'applications',
            element: <FoundationPlaceholderPage title="Applications" />,
          },
          { path: 'loans', element: <FoundationPlaceholderPage title="Loans" /> },
          { path: 'account', element: <FoundationPlaceholderPage title="Account" /> },
        ],
      },
      {
        path: 'foundation/auth',
        element: <AuthLayout />,
        children: [{ index: true, element: <AuthFoundationPage /> }],
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
