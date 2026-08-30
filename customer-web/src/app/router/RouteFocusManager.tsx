import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

const routeTitles: Record<string, string> = {
  '/': 'Customer Dashboard',
  '/products': 'Products',
  '/products/salary-advance': 'Salary Advance',
  '/products/unsecured-consumer-loan': 'Unsecured Consumer Loan',
  '/products/collateral-loan': 'Collateral Loan',
  '/applications': 'Applications Foundation',
  '/loans': 'Loans Foundation',
  '/account': 'Account Foundation',
  '/foundation/flow': 'Focused Flow Foundation',
  '/foundation/detail': 'Detail Layout Foundation',
  '/login': 'Login',
  '/register': 'Create Account',
  '/verify-email': 'Confirm Email',
  '/verify-email/pending': 'Email Confirmation Required',
  '/forgot-password': 'Forgot Password',
  '/reset-password': 'Reset Password',
}

export function RouteFocusManager() {
  const location = useLocation()

  useEffect(() => {
    document.title = `${routeTitles[location.pathname] ?? 'Page unavailable'} | Meridian`
    document.getElementById('page-heading')?.focus()
  }, [location.pathname])

  return <Outlet />
}
