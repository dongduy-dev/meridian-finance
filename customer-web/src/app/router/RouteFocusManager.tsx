import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

const routeTitles: Record<string, string> = {
  '/': 'Customer Dashboard',
  '/products': 'Products Foundation',
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
