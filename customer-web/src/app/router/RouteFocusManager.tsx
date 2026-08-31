import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

const routeTitles: Record<string, string> = {
  '/': 'Customer Dashboard',
  '/products': 'Products',
  '/products/salary-advance': 'Salary Advance',
  '/products/salary-advance/apply': 'Apply for Salary Advance',
  '/products/unsecured-consumer-loan': 'Unsecured Consumer Loan',
  '/products/unsecured-consumer-loan/apply': 'Apply for Unsecured Consumer Loan',
  '/products/collateral-loan': 'Collateral Loan',
  '/products/collateral-loan/apply': 'Apply for Collateral Loan',
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
    const isDocumentsRoute = /^\/applications\/[^/]+\/documents$/.test(location.pathname)
    document.title = `${routeTitles[location.pathname] ?? (isDocumentsRoute ? 'Application Documents' : 'Page unavailable')} | Meridian`
    const focusHeading = () => {
      const heading = document.getElementById('page-heading')
      if (!heading) return false
      heading.focus()
      return true
    }
    if (focusHeading()) return undefined
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect()
    })
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [location.pathname])

  return <Outlet />
}
